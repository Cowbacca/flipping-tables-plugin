package com.dashery.flippingtables;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;

@Singleton
final class OfferResultsRecorder {
    private final FlippingTablesClient api;
    private final ClientThread clientThread;
    private final TradeObservationStore store;
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "flipping-tables-offers");
        thread.setDaemon(true);
        return thread;
    });
    private final java.util.concurrent.ScheduledExecutorService retries = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "flipping-tables-offer-retry");
        thread.setDaemon(true);
        return thread;
    });
    private final Clock clock;
    private final Gson gson = new Gson();
    private TradeObservationStore.AccountLedger ledger;
    private String profileKey;
    private String destination;
    private String token;
    private boolean recording;
    private boolean ready;
    private boolean failed;
    private String loadingKey;
    private boolean initialSnapshotPending;
    private volatile boolean syncQueued;
    private int failures;
    private volatile long nextRetryAt;
    private volatile int queuedCount;
    private final java.util.Set<Integer> emptySlots = new java.util.HashSet<>();
    private Consumer<String> status = ignored -> { };

    @Inject
    OfferResultsRecorder(FlippingTablesClient api, ClientThread clientThread) {
        this(api, clientThread, new TradeObservationStore(Path.of(System.getProperty("user.home"), ".runelite",
                "flipping-tables", "offer-observations.json")), Clock.systemUTC());
    }

    OfferResultsRecorder(FlippingTablesClient api, ClientThread clientThread, TradeObservationStore store, Clock clock) {
        this.api = api;
        this.clientThread = clientThread;
        this.store = store;
        this.clock = clock;
    }

    void setStatusListener(Consumer<String> status) {
        this.status = status == null ? ignored -> { } : status;
    }

    void configure(boolean recording, String destination, String token) {
        if (this.destination != null && destination != null && !this.destination.equals(destination)) {
            ready = false;
            ledger = null;
            profileKey = null;
        }
        this.recording = recording;
        this.destination = destination;
        this.token = token;
        if (!recording) {
            deactivate();
            return;
        }
        publishStatus();
        requestSync();
    }

    void activate(String profileKey, List<OfferSnapshot> offers, int slotCount) {
        if (profileKey == null || profileKey.isEmpty() || !recording) {
            deactivate();
            return;
        }
        String ledgerKey = profileKey + "\n" + destination;
        if (ready && ledgerKey.equals(this.profileKey)) {
            if (initialSnapshotPending) {
                java.util.Set<Integer> observedSlots = new java.util.HashSet<>();
                for (OfferSnapshot offer : offers) {
                    observedSlots.add(offer.slot);
                    observe(offer, null, true);
                }
                markMissingAfterGap(observedSlots);
                initialSnapshotPending = false;
                return;
            }
            for (OfferSnapshot offer : offers) {
                observe(offer, null, false);
            }
            return;
        }
        if (ledgerKey.equals(loadingKey)) {
            return;
        }
        ready = false;
        this.profileKey = ledgerKey;
        loadingKey = ledgerKey;
        worker.execute(() -> {
            try {
                TradeObservationStore.AccountLedger loaded = store.load(ledgerKey);
                clientThread.invokeLater(() -> {
                    if (!ledgerKey.equals(this.profileKey) || !recording) {
                        return;
                    }
                    ledger = loaded;
                    queuedCount = (int) loaded.outbox.stream().filter(item -> destination.equals(item.destination)).count();
                    ready = true;
                    loadingKey = null;
                    emptySlots.clear();
                    for (int slot = 0; slot < slotCount; slot++) {
                        emptySlots.add(slot);
                    }
                    java.util.Set<Integer> observedSlots = new java.util.HashSet<>();
                    for (OfferSnapshot offer : offers) {
                        observedSlots.add(offer.slot);
                        emptySlots.remove(offer.slot);
                        observe(offer, null, true);
                    }
                    markMissingAfterGap(observedSlots);
                    initialSnapshotPending = true;
                    publishStatus();
                    requestSync();
                });
            } catch (IOException error) {
                clientThread.invokeLater(() -> report("Offer recording error: " + error.getMessage()));
            }
        });
    }

    void deactivate() {
        ready = false;
        loadingKey = null;
        initialSnapshotPending = false;
        ledger = null;
        profileKey = null;
        publishStatus();
    }

    String accountId() {
        return ready && ledger != null ? ledger.accountId : null;
    }

    boolean isRecordingEnabled() {
        return recording;
    }

    void observe(OfferSnapshot offer, String recommendationId, boolean gap) {
        if (!ready || failed || ledger == null || offer == null || !recording || destination == null || destination.isEmpty()) {
            return;
        }
        TrackedOffer previous = ledger.offers.get(offer.slot);
        Instant now = clock.instant();
        boolean newOffer = emptySlots.contains(offer.slot) || previous == null || !offer.sameDescriptor(previous)
                || (!previous.active && "OPEN".equals(offer.state));
        boolean baseline = newOffer && !emptySlots.contains(offer.slot);
        emptySlots.remove(offer.slot);
        if (newOffer) {
            if (previous != null && previous.active) {
                Instant earlier = parse(previous.observedAt);
                OfferSnapshot lost = snapshot(previous, offer.slot, "LOST");
                previous.update(lost, now, false);
                append(previous, new TradeObservation(previous.offerId, previous.sequence, lost, "LOST", now,
                        gap ? earlier : null, false, gap, null));
            }
            previous = TrackedOffer.start(offer);
            previous.observedAt = now.toString();
            previous.active = !isTerminal(offer.state);
            ledger.offers.put(offer.slot, previous);
            append(previous, new TradeObservation(previous.offerId, 0, offer, stateFor(offer), now, null, baseline, gap,
                    baseline ? null : recommendationId));
            return;
        }
        if (offer.filledQuantity < previous.filledQuantity || offer.cumulativeGp < previous.cumulativeGp) {
            Instant earlier = parse(previous.observedAt);
            boolean replacementBaseline = !emptySlots.contains(offer.slot);
            previous.active = false;
            OfferSnapshot lost = snapshot(previous, offer.slot, "LOST");
            previous.update(lost, now, false);
            append(previous, new TradeObservation(previous.offerId, previous.sequence, lost, "LOST", now,
                    gap ? earlier : null, false, gap, null));
            TrackedOffer replacement = TrackedOffer.start(offer);
            replacement.observedAt = now.toString();
            ledger.offers.put(offer.slot, replacement);
            append(replacement, new TradeObservation(replacement.offerId, 0, offer, stateFor(offer), now, null,
                    replacementBaseline, gap, replacementBaseline ? null : recommendationId));
            return;
        }
        if (offer.filledQuantity == previous.filledQuantity && offer.cumulativeGp == previous.cumulativeGp
                && stateFor(offer).equals(previous.state)) {
            return;
        }
        Instant earlier = parse(previous.observedAt);
        previous.update(offer, now, !isTerminal(offer.state));
        append(previous, new TradeObservation(previous.offerId, previous.sequence, offer, stateFor(offer), now,
                gap ? earlier : null, false, gap, null));
    }

    void clear(int slot, boolean gap) {
        if (!ready || failed || ledger == null || !recording) {
            return;
        }
        emptySlots.add(slot);
        TrackedOffer previous = ledger.offers.get(slot);
        if (previous == null || !previous.active) {
            return;
        }
        Instant now = clock.instant();
        Instant earlier = parse(previous.observedAt);
        OfferSnapshot cleared = snapshot(previous, slot, "CLEARED");
        previous.update(cleared, now, false);
        append(previous, new TradeObservation(previous.offerId, previous.sequence, cleared, "CLEARED", now,
                gap ? earlier : null, false, gap, null));
    }

    void shutDown() {
        deactivate();
    }

    private void append(TrackedOffer offer, TradeObservation observation) {
        String targetProfile = profileKey;
        String targetDestination = destination;
        TrackedOffer persisted = gson.fromJson(gson.toJson(offer), TrackedOffer.class);
        worker.execute(() -> {
            try {
                store.append(targetProfile, persisted, observation, targetDestination);
                queuedCount++;
                clientThread.invokeLater(this::publishStatus);
                requestSync();
            } catch (IOException error) {
                clientThread.invokeLater(() -> stopForFailure(error));
            }
        });
    }

    private synchronized void requestSync() {
        String targetProfile = profileKey;
        String targetDestination = destination;
        String targetToken = token;
        if (syncQueued || System.currentTimeMillis() < nextRetryAt || failed || !recording || targetProfile == null || targetDestination == null || targetToken == null || targetToken.isEmpty()) {
            return;
        }
        syncQueued = true;
        worker.execute(() -> sync(targetProfile, targetDestination, targetToken));
    }

    private void sync(String targetProfile, String targetDestination, String targetToken) {
        boolean acknowledged = false;
        try {
            List<TradeObservationStore.QueuedObservation> queued = store.batch(targetProfile, targetDestination);
            if (queued.isEmpty()) {
                clientThread.invokeLater(this::publishStatus);
                return;
            }
            List<TradeObservation> observations = new ArrayList<>();
            for (TradeObservationStore.QueuedObservation item : queued) {
                observations.add(item.observation);
            }
            FlippingTablesClient.IngestResult result = api.submitTradeObservations(targetDestination, ledgerAccount(targetProfile), observations, targetToken);
            if (result.accepted + result.duplicates != queued.size()) {
                throw new IOException("Offer results response did not acknowledge the full batch");
            }
            store.acknowledge(targetProfile, queued);
            queuedCount = Math.max(0, queuedCount - queued.size());
            failures = 0;
            nextRetryAt = 0L;
            clientThread.invokeLater(this::publishStatus);
            acknowledged = true;
        } catch (IOException | RuntimeException error) {
            failures = Math.min(failures + 1, 4);
            long delay = Math.min(60L, 5L * (1L << failures));
            nextRetryAt = System.currentTimeMillis() + java.util.concurrent.TimeUnit.SECONDS.toMillis(delay);
            clientThread.invokeLater(() -> report("Offer recording queued: " + error.getMessage()));
            retries.schedule(this::requestSync, delay, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            syncQueued = false;
        }
        if (acknowledged) {
            requestSync();
        }
    }

    private String ledgerAccount(String targetProfile) throws IOException {
        return store.load(targetProfile).accountId;
    }

    private void publishStatus() {
        if (failed) {
            return;
        }
        if (!recording) {
            status.accept("Offer recording is off.");
        } else if (!ready) {
            status.accept("Preparing offer recording for this account.");
        } else if (token == null || token.isEmpty()) {
            status.accept(queuedCount == 0 ? "Recording Grand Exchange offers. Enter an API token to sync."
                    : "Recording Grand Exchange offers. " + queuedCount + " observation" + (queuedCount == 1 ? "" : "s") + " saved locally; enter an API token to sync.");
        } else {
            status.accept(queuedCount == 0 ? "Recording Grand Exchange offers. Synced."
                    : "Recording Grand Exchange offers. " + queuedCount + " observation" + (queuedCount == 1 ? "" : "s") + " queued.");
        }
    }

    private void report(String message) { status.accept(message); }

    private void stopForFailure(IOException error) {
        failed = true;
        report("Offer recording stopped: " + error.getMessage());
    }

    private static Instant parse(String time) { return time == null ? null : Instant.parse(time); }
    private static String stateFor(OfferSnapshot offer) { return offer.state; }
    private static OfferSnapshot snapshot(TrackedOffer offer, int slot, String state) {
        return new OfferSnapshot(slot, offer.itemId, offer.side, state, offer.pricePerItem, offer.totalQuantity,
                offer.filledQuantity, offer.cumulativeGp);
    }

    private static boolean isTerminal(String state) {
        return "FILLED".equals(state) || "CANCELLED".equals(state) || "CLEARED".equals(state) || "LOST".equals(state);
    }

    private void markMissingAfterGap(java.util.Set<Integer> observedSlots) {
        Instant now = clock.instant();
        for (java.util.Map.Entry<Integer, TrackedOffer> entry : ledger.offers.entrySet()) {
            TrackedOffer previous = entry.getValue();
            if (!previous.active || observedSlots.contains(entry.getKey())) {
                continue;
            }
            Instant earlier = parse(previous.observedAt);
            OfferSnapshot lost = snapshot(previous, entry.getKey(), "LOST");
            previous.update(lost, now, false);
            append(previous, new TradeObservation(previous.offerId, previous.sequence, lost, "LOST", now,
                    earlier, false, true, null));
        }
    }
}

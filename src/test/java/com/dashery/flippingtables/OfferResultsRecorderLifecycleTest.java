package com.dashery.flippingtables;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class OfferResultsRecorderLifecycleTest {
    private static final String PROFILE = "account";
    private static final String DESTINATION = "https://example.test/api";
    private static final String LEDGER_KEY = PROFILE + "\n" + DESTINATION;

    @Test
    public void reconnectingTheSameOfferRecordsAGapIncrement() throws Exception {
        Path journal = Files.createTempDirectory("offer-recorder-reconnect").resolve("journal.json");
        MutableClock clock = new MutableClock("2026-09-21T10:00:00Z");
        TradeObservationStore store = new TradeObservationStore(journal);
        OfferResultsRecorder recorder = recorder(store, clock);
        activate(recorder, Collections.emptyList());
        recorder.observe(offer(0, 4151, 0, 0, "OPEN"), null, false);
        await(() -> observations(store).size() == 1);

        clock.advanceSeconds(60);
        recorder.deactivate();
        recorder.activate(PROFILE, Collections.singletonList(offer(0, 4151, 2, 200, "OPEN")), 1);
        await(() -> observations(store).size() == 2);

        List<TradeObservation> observations = observations(store);
        TradeObservation initial = observations.get(0);
        TradeObservation reconnect = observations.get(1);
        assertEquals(initial.offerId, reconnect.offerId);
        assertEquals(1, reconnect.sequence);
        assertTrue(reconnect.gap);
        assertEquals(initial.observedAt, reconnect.previousObservedAt);
        assertFalse(reconnect.baseline);
    }

    @Test
    public void startupReplacementRecordsLostOfferAndBaselinesTheNewOffer() throws Exception {
        Path journal = Files.createTempDirectory("offer-recorder-replacement").resolve("journal.json");
        MutableClock clock = new MutableClock("2026-09-21T10:00:00Z");
        TradeObservationStore firstStore = new TradeObservationStore(journal);
        OfferResultsRecorder firstRecorder = recorder(firstStore, clock);
        activate(firstRecorder, Collections.emptyList());
        firstRecorder.observe(offer(0, 4151, 1, 100, "OPEN"), null, false);
        await(() -> observations(firstStore).size() == 1);
        String originalOfferId = observations(firstStore).get(0).offerId;
        firstRecorder.shutDown();
        firstStore.close();

        clock.advanceSeconds(60);
        TradeObservationStore reloadedStore = new TradeObservationStore(journal);
        OfferResultsRecorder reloadedRecorder = recorder(reloadedStore, clock);
        activate(reloadedRecorder, Collections.singletonList(offer(0, 4152, 0, 0, "OPEN")));
        await(() -> observations(reloadedStore).size() == 3);

        List<TradeObservation> observations = observations(reloadedStore);
        TradeObservation lost = observations.get(1);
        TradeObservation replacement = observations.get(2);
        assertEquals(originalOfferId, lost.offerId);
        assertEquals("LOST", lost.state);
        assertTrue(lost.gap);
        assertTrue(replacement.baseline);
        assertTrue(replacement.gap);
        assertNotEquals(originalOfferId, replacement.offerId);
        assertEquals(4152, replacement.itemId);
        reloadedRecorder.shutDown();
        reloadedStore.close();
    }

    @Test
    public void startupMarksAPreviouslyActiveMissingOfferAsLost() throws Exception {
        Path journal = Files.createTempDirectory("offer-recorder-missing").resolve("journal.json");
        MutableClock clock = new MutableClock("2026-09-21T10:00:00Z");
        TradeObservationStore firstStore = new TradeObservationStore(journal);
        OfferResultsRecorder firstRecorder = recorder(firstStore, clock);
        activate(firstRecorder, Collections.emptyList());
        firstRecorder.observe(offer(0, 4151, 1, 100, "OPEN"), null, false);
        await(() -> observations(firstStore).size() == 1);
        firstRecorder.shutDown();
        firstStore.close();

        clock.advanceSeconds(60);
        TradeObservationStore reloadedStore = new TradeObservationStore(journal);
        OfferResultsRecorder reloadedRecorder = recorder(reloadedStore, clock);
        activate(reloadedRecorder, Collections.emptyList());
        await(() -> observations(reloadedStore).size() == 2);

        TradeObservation lost = observations(reloadedStore).get(1);
        assertEquals("LOST", lost.state);
        assertTrue(lost.gap);
        assertEquals(1, lost.sequence);
        assertTrue(lost.previousObservedAt != null);
        reloadedRecorder.shutDown();
        reloadedStore.close();
    }

    @Test
    public void ignoresDuplicateTerminalSnapshotsButRecordsAnIdenticalInstantFillAfterAnEmptySlot() throws Exception {
        Path journal = Files.createTempDirectory("offer-recorder-terminal").resolve("journal.json");
        MutableClock clock = new MutableClock("2026-09-21T10:00:00Z");
        TradeObservationStore store = new TradeObservationStore(journal);
        OfferResultsRecorder recorder = recorder(store, clock);
        activate(recorder, Collections.emptyList());

        OfferSnapshot terminal = offer(0, 4151, 1, 100, "FILLED");
        recorder.observe(terminal, null, false);
        await(() -> observations(store).size() == 1);
        recorder.observe(terminal, null, false);
        assertEquals(1, observations(store).size());

        recorder.clear(0, false);
        clock.advanceSeconds(60);
        recorder.observe(terminal, null, false);
        await(() -> observations(store).size() == 2);

        List<TradeObservation> observations = observations(store);
        assertNotEquals(observations.get(0).offerId, observations.get(1).offerId);
        assertFalse(observations.get(1).baseline);
    }

    @Test
    public void pauseAndResumeMarksTheFirstReconciledSnapshotAsAGap() throws Exception {
        Path journal = Files.createTempDirectory("offer-recorder-resume").resolve("journal.json");
        MutableClock clock = new MutableClock("2026-09-21T10:00:00Z");
        TradeObservationStore store = new TradeObservationStore(journal);
        OfferResultsRecorder recorder = recorder(store, clock);
        activate(recorder, Collections.emptyList());
        recorder.observe(offer(0, 4151, 0, 0, "OPEN"), null, false);
        await(() -> observations(store).size() == 1);

        recorder.configure(false, DESTINATION, "");
        clock.advanceSeconds(60);
        recorder.configure(true, DESTINATION, "");
        recorder.activate(PROFILE, Collections.singletonList(offer(0, 4151, 2, 200, "OPEN")), 1);
        await(() -> observations(store).size() == 2);

        TradeObservation resumed = observations(store).get(1);
        assertTrue(resumed.gap);
        assertTrue(resumed.previousObservedAt != null);
        assertEquals(2, resumed.filledQuantity);
    }

    private static OfferResultsRecorder recorder(TradeObservationStore store, Clock clock) {
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));
        return new OfferResultsRecorder(mock(FlippingTablesClient.class), clientThread, store, clock);
    }

    private static void activate(OfferResultsRecorder recorder, List<OfferSnapshot> offers) throws Exception {
        recorder.configure(true, DESTINATION, "");
        recorder.activate(PROFILE, offers, 1);
        await(() -> recorder.accountId() != null);
    }

    private static List<TradeObservation> observations(TradeObservationStore store) throws IOException {
        return store.load(LEDGER_KEY).outbox.stream().map(queued -> queued.observation).collect(Collectors.toList());
    }

    private static OfferSnapshot offer(int slot, int itemId, int filledQuantity, long cumulativeGp, String state) {
        return new OfferSnapshot(slot, itemId, "BUY", state, 100, 10, filledQuantity, cumulativeGp);
    }

    private static void await(CheckedCondition condition) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for offer recorder");
    }

    private interface CheckedCondition { boolean matches() throws Exception; }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(String instant) {
            this.instant = Instant.parse(instant);
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

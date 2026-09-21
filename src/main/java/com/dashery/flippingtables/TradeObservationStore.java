package com.dashery.flippingtables;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TradeObservationStore implements AutoCloseable {
    private final Path file;
    private final Gson gson = new Gson();
    private Root root;
    private FileChannel lockChannel;
    private FileLock lock;

    TradeObservationStore(Path file) {
        this.file = file;
    }

    synchronized AccountLedger load(String profileKey) throws IOException {
        ensureLoaded();
        AccountLedger ledger = root.ledgers.get(profileKey);
        if (ledger == null) {
            ledger = new AccountLedger();
            ledger.accountId = java.util.UUID.randomUUID().toString();
            root.ledgers.put(profileKey, ledger);
            save();
        }
        return gson.fromJson(gson.toJson(ledger), AccountLedger.class);
    }

    synchronized void append(String profileKey, TrackedOffer offer, TradeObservation observation, String destination) throws IOException {
        ensureLoaded();
        AccountLedger ledger = root.ledgers.get(profileKey);
        if (ledger == null) {
            throw new IOException("Trade account was not loaded");
        }
        ledger.offers.put(observation.slot, gson.fromJson(gson.toJson(offer), TrackedOffer.class));
        QueuedObservation queued = new QueuedObservation();
        queued.destination = destination;
        queued.observation = observation;
        ledger.outbox.add(queued);
        save();
    }

    synchronized List<QueuedObservation> batch(String profileKey, String destination) throws IOException {
        ensureLoaded();
        AccountLedger ledger = root.ledgers.get(profileKey);
        List<QueuedObservation> result = new ArrayList<>();
        if (ledger != null) {
            for (QueuedObservation observation : ledger.outbox) {
                if (destination.equals(observation.destination)) {
                    result.add(observation);
                    if (result.size() == 100) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    synchronized void acknowledge(String profileKey, List<QueuedObservation> accepted) throws IOException {
        ensureLoaded();
        AccountLedger ledger = root.ledgers.get(profileKey);
        if (ledger == null) {
            return;
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (QueuedObservation queued : accepted) {
            ids.add(queued.observation.eventId);
        }
        ledger.outbox.removeIf(queued -> ids.contains(queued.observation.eventId));
        save();
    }

    private void ensureLoaded() throws IOException {
        if (root != null) {
            return;
        }
        Files.createDirectories(file.getParent());
        lockJournal();
        if (!Files.exists(file)) {
            root = new Root();
            return;
        }
        try {
            root = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Root.class);
            if (root == null || root.ledgers == null) {
                throw new IOException("Recorded offer data is invalid");
            }
        } catch (RuntimeException error) {
            throw new IOException("Recorded offer data is invalid", error);
        }
    }

    private void lockJournal() throws IOException {
        if (lock != null && lock.isValid()) {
            return;
        }
        Path lockFile = file.resolveSibling(file.getFileName() + ".lock");
        lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
        } catch (java.nio.channels.OverlappingFileLockException error) {
            lock = null;
        }
        if (lock == null) {
            lockChannel.close();
            lockChannel = null;
            throw new IOException("Offer recording is already active in another client");
        }
    }

    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, gson.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (lock != null) {
            lock.release();
            lock = null;
        }
        if (lockChannel != null) {
            lockChannel.close();
            lockChannel = null;
        }
    }

    static final class Root { Map<String, AccountLedger> ledgers = new HashMap<>(); }
    static final class AccountLedger {
        String accountId;
        Map<Integer, TrackedOffer> offers = new HashMap<>();
        List<QueuedObservation> outbox = new ArrayList<>();
    }
    static final class QueuedObservation { String destination; TradeObservation observation; }
}

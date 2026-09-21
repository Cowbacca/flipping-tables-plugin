package com.dashery.flippingtables;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TradeObservationStoreTest {
    private static final String PROFILE = "normal-account\nhttps://one.example/api";
    private static final String DESTINATION_ONE = "https://one.example/api";
    private static final String DESTINATION_TWO = "https://two.example/api";

    @Test
    public void persistsAccountAndEventUuidsAcrossFreshStoreInstances() throws Exception {
        Path journal = Files.createTempDirectory("trade-store-round-trip").resolve("journal.json");
        String accountId;
        String eventId;

        try (TradeObservationStore store = new TradeObservationStore(journal)) {
            TradeObservationStore.AccountLedger ledger = store.load(PROFILE);
            accountId = ledger.accountId;
            TradeObservation observation = append(store, 0, DESTINATION_ONE);
            eventId = observation.eventId;
        }

        try (TradeObservationStore reopened = new TradeObservationStore(journal)) {
            TradeObservationStore.AccountLedger ledger = reopened.load(PROFILE);
            assertEquals(UUID.fromString(accountId), UUID.fromString(ledger.accountId));
            List<TradeObservationStore.QueuedObservation> queued = reopened.batch(PROFILE, DESTINATION_ONE);
            assertEquals(1, queued.size());
            assertEquals(UUID.fromString(eventId), UUID.fromString(queued.get(0).observation.eventId));
        }
    }

    @Test
    public void batchesAndAcknowledgesOnlyTheRequestedDestinationAndEvents() throws Exception {
        Path journal = Files.createTempDirectory("trade-store-destination").resolve("journal.json");

        try (TradeObservationStore store = new TradeObservationStore(journal)) {
            store.load(PROFILE);
            TradeObservation first = append(store, 0, DESTINATION_ONE);
            TradeObservation second = append(store, 1, DESTINATION_TWO);
            TradeObservation third = append(store, 2, DESTINATION_ONE);

            List<TradeObservationStore.QueuedObservation> firstDestination = store.batch(PROFILE, DESTINATION_ONE);
            assertEquals(2, firstDestination.size());
            assertEquals(first.eventId, firstDestination.get(0).observation.eventId);
            assertEquals(third.eventId, firstDestination.get(1).observation.eventId);

            store.acknowledge(PROFILE, Collections.singletonList(firstDestination.get(0)));

            List<TradeObservationStore.QueuedObservation> remainingFirstDestination = store.batch(PROFILE, DESTINATION_ONE);
            assertEquals(1, remainingFirstDestination.size());
            assertEquals(third.eventId, remainingFirstDestination.get(0).observation.eventId);
            List<TradeObservationStore.QueuedObservation> secondDestination = store.batch(PROFILE, DESTINATION_TWO);
            assertEquals(1, secondDestination.size());
            assertEquals(second.eventId, secondDestination.get(0).observation.eventId);
        }
    }

    @Test
    public void limitsEachDestinationBatchToOneHundredEvents() throws Exception {
        Path journal = Files.createTempDirectory("trade-store-batch-size").resolve("journal.json");

        try (TradeObservationStore store = new TradeObservationStore(journal)) {
            store.load(PROFILE);
            for (int slot = 0; slot < 101; slot++) {
                append(store, slot, DESTINATION_ONE);
            }

            List<TradeObservationStore.QueuedObservation> firstBatch = store.batch(PROFILE, DESTINATION_ONE);
            assertEquals(100, firstBatch.size());
            store.acknowledge(PROFILE, firstBatch);
            assertEquals(1, store.batch(PROFILE, DESTINATION_ONE).size());
        }
    }

    @Test
    public void rejectsASecondClientAndCorruptJournal() throws Exception {
        Path directory = Files.createTempDirectory("trade-store-lock");
        Path journal = directory.resolve("journal.json");

        try (TradeObservationStore first = new TradeObservationStore(journal)) {
            first.load(PROFILE);
            IOException error = assertThrows(IOException.class, () -> new TradeObservationStore(journal).load(PROFILE));
            assertTrue(error.getMessage().contains("already active"));
        }

        Path corruptJournal = directory.resolve("corrupt.json");
        Files.writeString(corruptJournal, "not JSON");
        try (TradeObservationStore corrupt = new TradeObservationStore(corruptJournal)) {
            IOException error = assertThrows(IOException.class, () -> corrupt.load(PROFILE));
            assertTrue(error.getMessage().contains("invalid"));
        }
    }

    private static TradeObservation append(TradeObservationStore store, int slot, String destination) throws IOException {
        OfferSnapshot snapshot = new OfferSnapshot(slot, 4151, "BUY", "OPEN", 100, 1, 1, 100);
        TrackedOffer offer = TrackedOffer.start(snapshot);
        TradeObservation observation = new TradeObservation(offer.offerId, offer.sequence, snapshot, "OPEN",
                Instant.parse("2026-09-21T12:00:00Z").plusSeconds(slot), null, false, false, null);
        store.append(PROFILE, offer, observation, destination);
        return observation;
    }
}

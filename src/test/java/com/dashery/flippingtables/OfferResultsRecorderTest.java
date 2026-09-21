package com.dashery.flippingtables;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class OfferResultsRecorderTest {
    @Test
    public void baselinesStartupButRecordsAnInstantFillAfterAnObservedEmptySlot() throws Exception {
        Path directory = Files.createTempDirectory("offer-recorder-test");
        TradeObservationStore store = new TradeObservationStore(directory.resolve("journal.json"));
        OfferResultsRecorder recorder = recorder(store);
        recorder.configure(true, "https://example.test/api", "");
        recorder.activate("account", Collections.singletonList(offer(0, 2, 200, "OPEN")), 2);
        await(() -> recorder.accountId() != null && store.load("account\nhttps://example.test/api").outbox.size() == 1);
        TradeObservationStore.AccountLedger ledger = store.load("account\nhttps://example.test/api");
        assertEquals(1, ledger.outbox.size());
        assertEquals(true, ledger.outbox.get(0).observation.baseline);

        recorder.clear(1, false);
        recorder.observe(offer(1, 3, 330, "FILLED"), "recommendation", false);
        await(() -> store.load("account\nhttps://example.test/api").outbox.size() == 2);
        TradeObservation second = store.load("account\nhttps://example.test/api").outbox.get(1).observation;
        assertFalse(second.baseline);
        assertEquals("recommendation", second.recommendationId);
    }

    @Test
    public void terminalSlotReuseGetsANewOfferIdentity() throws Exception {
        Path directory = Files.createTempDirectory("offer-recorder-reuse");
        TradeObservationStore store = new TradeObservationStore(directory.resolve("journal.json"));
        OfferResultsRecorder recorder = recorder(store);
        recorder.configure(true, "https://example.test/api", "");
        recorder.activate("account", Collections.emptyList(), 1);
        await(() -> recorder.accountId() != null);
        recorder.observe(offer(0, 1, 100, "FILLED"), null, false);
        await(() -> store.load("account\nhttps://example.test/api").outbox.size() == 1);
        String first = store.load("account\nhttps://example.test/api").outbox.get(0).observation.offerId;
        recorder.observe(offer(0, 0, 0, "OPEN"), null, false);
        await(() -> store.load("account\nhttps://example.test/api").outbox.size() == 2);
        String second = store.load("account\nhttps://example.test/api").outbox.get(1).observation.offerId;
        assertNotEquals(first, second);
    }

    private static OfferResultsRecorder recorder(TradeObservationStore store) {
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));
        return new OfferResultsRecorder(mock(FlippingTablesClient.class), clientThread, store,
                Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC));
    }

    private static OfferSnapshot offer(int slot, int quantity, long gp, String state) {
        return new OfferSnapshot(slot, 4151, "BUY", state, 100, 10, quantity, gp);
    }

    private static void await(CheckedCondition condition) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                if (condition.matches()) {
                    return;
                }
            } catch (java.io.IOException ignored) {
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for offer recorder");
    }

    private interface CheckedCondition { boolean matches() throws Exception; }
}

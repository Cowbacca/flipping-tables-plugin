package com.dashery.flippingtables;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class OfferResultsRecorderSyncTest {
    @Test
    public void drainsOneHundredAndThenTheRemainingDurableObservation() throws Exception {
        CapturingServer server = new CapturingServer(false);
        Path journal = Files.createTempDirectory("offer-recorder-sync").resolve("journal.json");
        TradeObservationStore store = new TradeObservationStore(journal);
        OfferResultsRecorder recorder = recorder(store);
        String destination = server.destination();
        recorder.configure(true, destination, "");
        recorder.activate("account", Collections.emptyList(), 101);
        await(() -> recorder.accountId() != null, 2_000);
        for (int slot = 0; slot < 101; slot++) {
            recorder.observe(offer(slot), null, false);
        }
        await(() -> store.load("account\n" + destination).outbox.size() == 101, 4_000);

        recorder.configure(true, destination, "token");
        await(() -> store.load("account\n" + destination).outbox.isEmpty(), 4_000);
        assertEquals(java.util.Arrays.asList(100, 1), server.batchSizes());
        assertEquals(101, new java.util.HashSet<>(server.eventIds()).size());
        recorder.shutDown();
        store.close();
        server.close();
    }

    @Test
    public void retainsTheSameEventAfterFailureThenRetriesAndClearsIt() throws Exception {
        CapturingServer server = new CapturingServer(true);
        Path journal = Files.createTempDirectory("offer-recorder-retry").resolve("journal.json");
        TradeObservationStore store = new TradeObservationStore(journal);
        OfferResultsRecorder recorder = recorder(store);
        String destination = server.destination();
        recorder.configure(true, destination, "");
        recorder.activate("account", Collections.emptyList(), 1);
        await(() -> recorder.accountId() != null, 2_000);
        recorder.observe(offer(0), null, false);
        await(() -> store.load("account\n" + destination).outbox.size() == 1, 2_000);
        String eventId = store.load("account\n" + destination).outbox.get(0).observation.eventId;

        recorder.configure(true, destination, "token");
        await(() -> server.batchSizes.size() == 1, 2_000);
        assertEquals(eventId, server.eventIds().get(0));
        assertEquals(1, store.load("account\n" + destination).outbox.size());
        server.fail.set(false);
        await(() -> store.load("account\n" + destination).outbox.isEmpty(), 15_000);
        assertEquals(eventId, server.eventIds().get(1));
        recorder.shutDown();
        store.close();
        server.close();
    }

    @Test
    public void destinationSwitchDoesNotSendOldEventsToTheNewApi() throws Exception {
        CapturingServer first = new CapturingServer(false);
        CapturingServer second = new CapturingServer(false);
        Path journal = Files.createTempDirectory("offer-recorder-destination").resolve("journal.json");
        TradeObservationStore store = new TradeObservationStore(journal);
        OfferResultsRecorder recorder = recorder(store);
        recorder.configure(true, first.destination(), "");
        recorder.activate("account", Collections.emptyList(), 1);
        await(() -> recorder.accountId() != null, 2_000);
        recorder.observe(offer(0), null, false);
        await(() -> store.load("account\n" + first.destination()).outbox.size() == 1, 2_000);
        String oldEventId = store.load("account\n" + first.destination()).outbox.get(0).observation.eventId;

        recorder.configure(true, second.destination(), "token");
        recorder.activate("account", Collections.emptyList(), 1);
        await(() -> recorder.accountId() != null && store.load("account\n" + second.destination()).outbox.isEmpty(), 2_000);
        recorder.observe(offer(0), null, false);
        await(() -> !second.eventIds().isEmpty(), 2_000);
        assertFalse(second.eventIds().contains(oldEventId));
        assertEquals(1, store.load("account\n" + first.destination()).outbox.size());
        recorder.shutDown();
        store.close();
        first.close();
        second.close();
    }

    private static OfferResultsRecorder recorder(TradeObservationStore store) {
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));
        FlippingTablesClient client = new FlippingTablesClient(new OkHttpClient(), new FlippingTablesConfig() {
            @Override public String apiBaseUrl() { return "http://127.0.0.1/api"; }
        });
        return new OfferResultsRecorder(client, clientThread, store,
                Clock.fixed(Instant.parse("2026-09-21T14:00:00Z"), ZoneOffset.UTC));
    }

    private static OfferSnapshot offer(int slot) {
        return new OfferSnapshot(slot, 4151 + slot, "BUY", "OPEN", 100, 10, 1, 100);
    }

    private static void await(CheckedCondition condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for offer recorder sync");
    }

    private interface CheckedCondition { boolean matches() throws Exception; }

    private static final class CapturingServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicBoolean fail;
        private final List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());
        private final List<String> eventIds = Collections.synchronizedList(new ArrayList<>());

        private CapturingServer(boolean fail) throws IOException {
            this.fail = new AtomicBoolean(fail);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/trade-observations", new Handler());
            server.start();
        }

        private String destination() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        }

        private List<Integer> batchSizes() {
            synchronized (batchSizes) {
                return new ArrayList<>(batchSizes);
            }
        }

        private List<String> eventIds() {
            synchronized (eventIds) {
                return new ArrayList<>(eventIds);
            }
        }

        @Override public void close() { server.stop(0); }

        private final class Handler implements HttpHandler {
            @Override public void handle(HttpExchange exchange) throws IOException {
                JsonObject request = new JsonParser().parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray observations = request.getAsJsonArray("observations");
                batchSizes.add(observations.size());
                for (int index = 0; index < observations.size(); index++) {
                    eventIds.add(observations.get(index).getAsJsonObject().get("eventId").getAsString());
                }
                int status = fail.get() ? 500 : 200;
                byte[] response = (fail.get() ? "{}" : "{\"accepted\":" + observations.size() + ",\"duplicates\":0}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            }
        }
    }
}

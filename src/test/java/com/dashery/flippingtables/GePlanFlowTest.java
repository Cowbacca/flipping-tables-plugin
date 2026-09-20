package com.dashery.flippingtables;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;

import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GePlanFlowTest {
    @Test
    public void preservesRemainingBuyAfterWalletEventThenExactOfferEventAndClearsUnexpectedChange() throws Exception {
        AtomicReference<Item[]> inventory = new AtomicReference<>(coins(1_000));
        AtomicReference<GrandExchangeOffer[]> offers = new AtomicReference<>(new GrandExchangeOffer[8]);
        Client client = client(inventory, offers);
        GeLimitsTracker tracker = new GeLimitsTracker(Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC), itemId -> 100);
        PortfolioCaptureService captureService = new PortfolioCaptureService(client, itemId -> itemId,
                itemId -> composition(), tracker, Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC));
        CapturedPortfolio initial = captureService.capture();
        PortfolioModels.Action firstBuy = new PortfolioModels.Action("CREATE_BUY", 4151, 2, 100, null);
        PortfolioModels.Action secondBuy = new PortfolioModels.Action("CREATE_BUY", 1515, 3, 50, null);
        PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
        repository.save(response(firstBuy, secondBuy), snapshot(initial));

        FlippingTablesPlugin plugin = new FlippingTablesPlugin();
        FlippingTablesPanel panel = mock(FlippingTablesPanel.class);
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));
        install(plugin, "client", client);
        install(plugin, "captureService", captureService);
        install(plugin, "repository", repository);
        install(plugin, "lastCapture", initial);
        install(plugin, "planProgress", PortfolioPlanProgress.start(initial, Arrays.asList(firstBuy, secondBuy)));
        install(plugin, "panel", panel);
        install(plugin, "api", mock(FlippingTablesClient.class));
        install(plugin, "clientThread", clientThread);
        install(plugin, "geLimitsTracker", tracker);
        install(plugin, "geOfferWidgetController", mock(GeOfferWidgetController.class));
        install(plugin, "running", true);

        inventory.set(coins(800));
        plugin.onItemContainerChanged(inventoryChanged());
        assertArrayEquals(new short[]{(short) 4151, (short) 1515}, repository.buyItemIds());
        assertTrue(!repository.isCompleted(firstBuy));

        GrandExchangeOffer placed = offer(4151, 2, 0, 100, GrandExchangeOfferState.BUYING);
        offers.set(new GrandExchangeOffer[]{placed, null, null, null, null, null, null, null});
        plugin.onGrandExchangeOfferChanged(offerChanged(0, placed));
        plugin.onGameTick(new GameTick());
        flushEdt();

        assertTrue(repository.isCompleted(firstBuy));
        assertArrayEquals(new short[]{(short) 1515}, repository.buyItemIds());
        verify(panel).showPlanProgress();

        inventory.set(coins(799));
        plugin.onItemContainerChanged(inventoryChanged());
        plugin.onGameTick(new GameTick());
        flushEdt();

        assertArrayEquals(new short[0], repository.buyItemIds());
        verify(panel).invalidateAdvice(any(String.class), org.mockito.ArgumentMatchers.eq(true));
        verify(panel, never()).showError(any(String.class));
    }

    private static PortfolioModels.AdviceResponse response(PortfolioModels.Action... actions) {
        return new PortfolioModels.AdviceResponse(1L,
                new PortfolioModels.Advice(Arrays.asList(actions), 0L, 0L, 0L, 0L, Collections.emptyList(), "EXACT"),
                "2026-09-20T10:00:00Z");
    }

    private static PortfolioModels.Snapshot snapshot(CapturedPortfolio captured) {
        return new PortfolioModels.Snapshot(captured.getWalletCoins(), Collections.emptyList(), captured.getOpenOffers(),
                captured.getTotalSlots(), captured.getLimits(), captured.getCapturedAt());
    }

    private static Item[] coins(long amount) {
        return new Item[]{new Item(995, (int) amount)};
    }

    private static Client client(AtomicReference<Item[]> inventory, AtomicReference<GrandExchangeOffer[]> offers) {
        ItemContainer container = (ItemContainer) java.lang.reflect.Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
                new Class<?>[]{ItemContainer.class}, (proxy, method, arguments) -> "getItems".equals(method.getName())
                        ? inventory.get() : defaultValue(method.getReturnType()));
        Player player = (Player) java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> "getName".equals(method.getName()) ? "Plan Test" : defaultValue(method.getReturnType()));
        return (Client) java.lang.reflect.Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getGameState": return GameState.LOGGED_IN;
                        case "getItemContainer": return arguments[0] == InventoryID.INVENTORY ? container : null;
                        case "getGrandExchangeOffers": return offers.get();
                        case "getWorldType": return EnumSet.of(WorldType.MEMBERS);
                        case "getLocalPlayer": return player;
                        default: return defaultValue(method.getReturnType());
                    }
                });
    }

    private static ItemComposition composition() {
        return (ItemComposition) java.lang.reflect.Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                new Class<?>[]{ItemComposition.class}, (proxy, method, arguments) -> {
                    if ("isTradeable".equals(method.getName())) return true;
                    if ("getName".equals(method.getName())) return "Item";
                    return defaultValue(method.getReturnType());
                });
    }

    private static GrandExchangeOffer offer(int itemId, int quantity, int filled, int price, GrandExchangeOfferState state) {
        return new GrandExchangeOffer() {
            @Override public int getItemId() { return itemId; }
            @Override public int getTotalQuantity() { return quantity; }
            @Override public int getQuantitySold() { return filled; }
            @Override public int getPrice() { return price; }
            @Override public int getSpent() { return filled * price; }
            @Override public GrandExchangeOfferState getState() { return state; }
        };
    }

    private static GrandExchangeOfferChanged offerChanged(int slot, GrandExchangeOffer offer) {
        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setSlot(slot);
        event.setOffer(offer);
        return event;
    }

    private static ItemContainerChanged inventoryChanged() {
        return new ItemContainerChanged(InventoryID.INVENTORY.getId(), null);
    }

    private static void install(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }
}

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

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PortfolioCaptureServiceTest {
    @Test
    public void observesInstantFinishedOffersForPlanProgressButRequiresCollectionForNewAdvice() {
        Client client = client(new Item[]{new Item(995, 800)}, new GrandExchangeOffer[]{offer(4151, 2, 2,
                GrandExchangeOfferState.BOUGHT)});
        PortfolioCaptureService service = new PortfolioCaptureService(client, itemId -> itemId,
                itemId -> composition("Abyssal whip", true), new GeLimitsTracker(Clock.systemUTC(), itemId -> 70),
                Clock.systemUTC());

        assertThrows(IllegalStateException.class, service::capture);
        CapturedPortfolio progress = service.captureForProgress();
        assertEquals(800, progress.getWalletCoins());
        assertEquals("BUY", progress.getOpenOffers().get(0).getSide());
        assertEquals(2, progress.getOpenOffers().get(0).getFilledQuantity());
        assertEquals(0, progress.getStock().size());
    }

    @Test
    public void capturesRemainingPartialSellStockAlongsideInventoryStock() {
        ItemComposition composition = composition("Abyssal whip", true);
        Client client = client(new Item[]{new Item(4151, 2)}, new GrandExchangeOffer[]{offer(4151, 10, 4,
                GrandExchangeOfferState.SELLING)});
        GeLimitsTracker tracker = new GeLimitsTracker(Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC),
                itemId -> 70);
        PortfolioCaptureService service = new PortfolioCaptureService(client, itemId -> itemId,
                itemId -> composition, tracker, Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC));

        CapturedPortfolio captured = service.capture();

        assertEquals(1, captured.getOpenOffers().size());
        assertEquals("SELL", captured.getOpenOffers().get(0).getSide());
        assertEquals(1, captured.getStock().size());
        assertEquals(2, captured.getStock().get(0).getCarriedQuantity());
        assertEquals(6, captured.getStock().get(0).getListedQuantity());
        assertEquals("Abyssal whip", captured.getStock().get(0).getName());
    }

    private static Client client(Item[] items, GrandExchangeOffer[] offers) {
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, arguments) -> "getName".equals(method.getName()) ? "Portfolio Test" : defaultValue(method.getReturnType()));
        ItemContainer inventory = (ItemContainer) Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),
                new Class<?>[]{ItemContainer.class}, (proxy, method, arguments) -> {
                    if ("getItems".equals(method.getName())) {
                        return items;
                    }
                    return defaultValue(method.getReturnType());
                });
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getGameState":
                            return GameState.LOGGED_IN;
                        case "getItemContainer":
                            return arguments.length == 1 && arguments[0] == InventoryID.INVENTORY ? inventory : null;
                        case "getGrandExchangeOffers":
                            return offers;
                        case "getWorldType":
                            return EnumSet.noneOf(net.runelite.api.WorldType.class);
                        case "getLocalPlayer":
                            return player;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static ItemComposition composition(String name, boolean tradeable) {
        return (ItemComposition) Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                new Class<?>[]{ItemComposition.class}, (proxy, method, arguments) -> {
                    if ("getName".equals(method.getName())) {
                        return name;
                    }
                    if ("isTradeable".equals(method.getName())) {
                        return tradeable;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static GrandExchangeOffer offer(int itemId, int totalQuantity, int quantitySold, GrandExchangeOfferState state) {
        return new GrandExchangeOffer() {
            @Override public int getQuantitySold() { return quantitySold; }
            @Override public int getItemId() { return itemId; }
            @Override public int getTotalQuantity() { return totalQuantity; }
            @Override public int getPrice() { return 100; }
            @Override public int getSpent() { return quantitySold * 100; }
            @Override public GrandExchangeOfferState getState() { return state; }
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}

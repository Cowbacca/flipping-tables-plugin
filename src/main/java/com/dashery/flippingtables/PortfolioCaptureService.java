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
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

@Singleton
public final class PortfolioCaptureService {
    private static final int COINS = 995;

    private final Client client;
    private final IntUnaryOperator canonicalize;
    private final IntFunction<ItemComposition> itemComposition;
    private final GeLimitsTracker limitsTracker;
    private final Clock clock;

    @Inject
    public PortfolioCaptureService(Client client, ItemManager itemManager, GeLimitsTracker limitsTracker) {
        this(client, itemManager::canonicalize, itemManager::getItemComposition, limitsTracker, Clock.systemUTC());
    }

    PortfolioCaptureService(Client client, IntUnaryOperator canonicalize, IntFunction<ItemComposition> itemComposition,
            GeLimitsTracker limitsTracker, Clock clock) {
        this.client = client;
        this.canonicalize = canonicalize;
        this.itemComposition = itemComposition;
        this.limitsTracker = limitsTracker;
        this.clock = clock;
    }

    public CapturedPortfolio capture() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            throw new IllegalStateException("Log in before capturing a portfolio.");
        }
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            throw new IllegalStateException("Your inventory is not available yet.");
        }
        GrandExchangeOffer[] geOffers = client.getGrandExchangeOffers();
        if (geOffers == null) {
            throw new IllegalStateException("Your Grand Exchange offers are not available yet.");
        }

        Map<Integer, StockQuantity> stock = new LinkedHashMap<>();
        long walletCoins = captureInventory(inventory, stock);
        List<PortfolioModels.OpenOffer> offers = captureOffers(geOffers, stock);
        boolean members = isMembers();
        List<CapturedPortfolio.Stock> capturedStock = stock.values().stream()
                .filter(StockQuantity::hasQuantity)
                .sorted(Comparator.comparingLong(StockQuantity::getItemId))
                .map(StockQuantity::toStock)
                .collect(java.util.stream.Collectors.toList());
        String capturedAt = clock.instant().toString();
        return new CapturedPortfolio(walletCoins, offers, capturedStock, limitsTracker.snapshotLimits(), members ? 8 : 3,
                members, capturedAt, fingerprint(inventory.getItems(), geOffers, members));
    }

    private long captureInventory(ItemContainer inventory, Map<Integer, StockQuantity> stock) {
        long walletCoins = 0;
        for (Item item : inventory.getItems()) {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
                continue;
            }
            int itemId = canonicalize.applyAsInt(item.getId());
            if (itemId == COINS) {
                walletCoins = Math.addExact(walletCoins, item.getQuantity());
                continue;
            }
            ItemComposition composition = itemComposition.apply(itemId);
            if (composition == null || !composition.isTradeable()) {
                continue;
            }
            stock.computeIfAbsent(itemId, ignored -> new StockQuantity(itemId, composition.getName()))
                    .addCarried(item.getQuantity());
        }
        return walletCoins;
    }

    private List<PortfolioModels.OpenOffer> captureOffers(GrandExchangeOffer[] geOffers, Map<Integer, StockQuantity> stock) {
        List<PortfolioModels.OpenOffer> offers = new ArrayList<>();
        for (int slot = 0; slot < geOffers.length; slot++) {
            GrandExchangeOffer offer = geOffers[slot];
            if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY) {
                continue;
            }
            GrandExchangeOfferState state = offer.getState();
            if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD
                    || state == GrandExchangeOfferState.CANCELLED_BUY || state == GrandExchangeOfferState.CANCELLED_SELL) {
                throw new IllegalStateException("Collect finished Grand Exchange offers before capturing a portfolio.");
            }
            if (state != GrandExchangeOfferState.BUYING && state != GrandExchangeOfferState.SELLING) {
                continue;
            }
            int canonicalItemId = canonicalize.applyAsInt(offer.getItemId());
            long filledQuantity = offer.getQuantitySold();
            long requestedQuantity = offer.getTotalQuantity();
            long price = offer.getPrice();
            if (canonicalItemId <= 0 || requestedQuantity <= 0 || price <= 0 || filledQuantity < 0
                    || filledQuantity > requestedQuantity) {
                throw new IllegalStateException("Your Grand Exchange offer has invalid values. Collect it and create it again.");
            }
            String side = state == GrandExchangeOfferState.BUYING ? "BUY" : "SELL";
            offers.add(new PortfolioModels.OpenOffer("slot-" + slot, canonicalItemId, side, price, requestedQuantity,
                    filledQuantity));
            if (state == GrandExchangeOfferState.SELLING) {
                ItemComposition composition = itemComposition.apply(canonicalItemId);
                String name = composition == null ? "Item " + canonicalItemId : composition.getName();
                stock.computeIfAbsent(canonicalItemId, ignored -> new StockQuantity(canonicalItemId, name))
                        .addListed(Math.max(0, requestedQuantity - filledQuantity));
            }
        }
        return offers;
    }

    private boolean isMembers() {
        EnumSet<WorldType> worldTypes = client.getWorldType();
        return worldTypes != null && worldTypes.contains(WorldType.MEMBERS);
    }

    private String fingerprint(Item[] inventory, GrandExchangeOffer[] offers, boolean members) {
        StringBuilder input = new StringBuilder(accountIdentity()).append('|').append(members);
        for (Item item : inventory) {
            if (item != null) {
                input.append('|').append(item.getId()).append(':').append(item.getQuantity());
            }
        }
        if (offers != null) {
            for (GrandExchangeOffer offer : offers) {
                if (offer != null) {
                    input.append('|').append(offer.getState()).append(':').append(offer.getItemId()).append(':')
                            .append(offer.getPrice()).append(':').append(offer.getTotalQuantity()).append(':')
                            .append(offer.getQuantitySold());
                }
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String accountIdentity() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getName() == null || localPlayer.getName().isEmpty()) {
            throw new IllegalStateException("Your player identity is not available yet.");
        }
        return localPlayer.getName();
    }

    private static final class StockQuantity {
        private final int itemId;
        private final String name;
        private long carriedQuantity;
        private long listedQuantity;

        private StockQuantity(int itemId, String name) {
            this.itemId = itemId;
            this.name = name;
        }

        private void addCarried(long quantity) {
            carriedQuantity = Math.addExact(carriedQuantity, quantity);
        }

        private void addListed(long quantity) {
            listedQuantity = Math.addExact(listedQuantity, quantity);
        }

        private boolean hasQuantity() {
            return carriedQuantity > 0 || listedQuantity > 0;
        }

        private long getItemId() {
            return itemId;
        }

        private CapturedPortfolio.Stock toStock() {
            return new CapturedPortfolio.Stock(itemId, name, carriedQuantity, listedQuantity);
        }
    }
}

package com.dashery.flippingtables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PortfolioPlanProgress {
    private final CapturedPortfolio portfolio;
    private final List<PortfolioModels.Action> pendingActions;
    private final List<PortfolioModels.Action> completedActions;

    private PortfolioPlanProgress(CapturedPortfolio portfolio, List<PortfolioModels.Action> pendingActions,
            List<PortfolioModels.Action> completedActions) {
        this.portfolio = portfolio;
        this.pendingActions = Collections.unmodifiableList(new ArrayList<>(pendingActions));
        this.completedActions = Collections.unmodifiableList(new ArrayList<>(completedActions));
    }

    public static PortfolioPlanProgress start(CapturedPortfolio portfolio, List<PortfolioModels.Action> actions) {
        if (portfolio == null || actions == null) {
            throw new IllegalArgumentException("Portfolio and actions are required.");
        }
        List<PortfolioModels.Action> pending = new ArrayList<>();
        for (PortfolioModels.Action action : actions) {
            if (isCreate(action)) {
                validateAction(action);
                pending.add(action);
            }
        }
        return new PortfolioPlanProgress(portfolio, pending, Collections.emptyList());
    }

    public Optional<PortfolioPlanProgress> advance(CapturedPortfolio current) {
        if (current == null || current.getTotalSlots() != portfolio.getTotalSlots()
                || current.isMembers() != portfolio.isMembers()) {
            return Optional.empty();
        }
        try {
            Map<String, PortfolioModels.OpenOffer> previousOffers = offersById(portfolio.getOpenOffers());
            Map<String, PortfolioModels.OpenOffer> currentOffers = offersById(current.getOpenOffers());
            if (!existingOffersProgressMonotonically(previousOffers, currentOffers)) {
                return Optional.empty();
            }

            List<RecognizedCreate> recognized = new ArrayList<>();
            List<PortfolioModels.Action> remaining = new ArrayList<>(pendingActions);
            for (PortfolioModels.OpenOffer offer : currentOffers.values()) {
                if (previousOffers.containsKey(offer.getId())) {
                    continue;
                }
                PortfolioModels.Action action = takeExactCreate(remaining, offer);
                if (action == null) {
                    return Optional.empty();
                }
                recognized.add(new RecognizedCreate(action, offer));
            }
            if (!walletMatches(current, recognized) || !stockMatches(current, previousOffers, currentOffers, recognized)) {
                return Optional.empty();
            }
            List<PortfolioModels.Action> completed = new ArrayList<>(completedActions);
            for (RecognizedCreate create : recognized) {
                completed.add(create.action);
            }
            return Optional.of(new PortfolioPlanProgress(current, remaining, completed));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public List<PortfolioModels.Action> pendingActions() {
        return pendingActions;
    }

    public List<PortfolioModels.Action> getPendingActions() {
        return pendingActions();
    }

    public List<PortfolioModels.Action> getCompletedActions() {
        return completedActions;
    }

    private boolean walletMatches(CapturedPortfolio current, List<RecognizedCreate> recognized) {
        long reserved = 0L;
        for (RecognizedCreate create : recognized) {
            PortfolioModels.Action action = create.action;
            if ("CREATE_BUY".equals(action.getType())) {
                reserved = Math.addExact(reserved, Math.multiplyExact(action.getQuantity(), action.getPricePerItem()));
            }
        }
        return current.getWalletCoins() == Math.subtractExact(portfolio.getWalletCoins(), reserved);
    }

    private boolean stockMatches(CapturedPortfolio current, Map<String, PortfolioModels.OpenOffer> previousOffers,
            Map<String, PortfolioModels.OpenOffer> currentOffers, List<RecognizedCreate> recognized) {
        Map<Long, StockAmounts> expected = stockAmounts(portfolio.getStock());
        for (PortfolioModels.OpenOffer offer : previousOffers.values()) {
            if ("SELL".equals(offer.getSide())) {
                PortfolioModels.OpenOffer now = currentOffers.get(offer.getId());
                applySellFill(expected, offer.getItemId(), Math.subtractExact(now.getFilledQuantity(), offer.getFilledQuantity()));
            }
        }
        for (RecognizedCreate create : recognized) {
            PortfolioModels.Action action = create.action;
            if ("CREATE_SELL".equals(action.getType())) {
                PortfolioModels.OpenOffer offer = create.offer;
                long filled = offer.getFilledQuantity();
                StockAmounts amounts = expected.computeIfAbsent(action.getItemId(), ignored -> new StockAmounts());
                amounts.listed = Math.addExact(amounts.listed, Math.subtractExact(action.getQuantity(), filled));
                amounts.total = Math.subtractExact(amounts.total, filled);
            }
        }
        normalizeStock(expected);
        Map<Long, StockAmounts> actual = stockAmounts(current.getStock());
        normalizeStock(actual);
        return expected.equals(actual);
    }

    private void applySellFill(Map<Long, StockAmounts> stock, long itemId, long filled) {
        StockAmounts amounts = stock.computeIfAbsent(itemId, ignored -> new StockAmounts());
        amounts.listed = Math.subtractExact(amounts.listed, filled);
        amounts.total = Math.subtractExact(amounts.total, filled);
    }

    private boolean existingOffersProgressMonotonically(Map<String, PortfolioModels.OpenOffer> previous,
            Map<String, PortfolioModels.OpenOffer> current) {
        for (PortfolioModels.OpenOffer offer : previous.values()) {
            PortfolioModels.OpenOffer now = current.get(offer.getId());
            if (now == null || !sameIdentity(offer, now) || now.getFilledQuantity() < offer.getFilledQuantity()
                    || now.getFilledQuantity() > now.getRequestedQuantity()) {
                return false;
            }
        }
        return true;
    }

    private PortfolioModels.Action takeExactCreate(List<PortfolioModels.Action> remaining,
            PortfolioModels.OpenOffer offer) {
        for (int index = 0; index < remaining.size(); index++) {
            PortfolioModels.Action action = remaining.get(index);
            if (matches(action, offer)) {
                remaining.remove(index);
                return action;
            }
        }
        return null;
    }

    private static boolean matches(PortfolioModels.Action action, PortfolioModels.OpenOffer offer) {
        return (("CREATE_BUY".equals(action.getType()) && "BUY".equals(offer.getSide()))
                || ("CREATE_SELL".equals(action.getType()) && "SELL".equals(offer.getSide())))
                && action.getItemId() == offer.getItemId()
                && action.getPricePerItem() == offer.getPricePerItem()
                && action.getQuantity() == offer.getRequestedQuantity()
                && offer.getFilledQuantity() >= 0L
                && offer.getFilledQuantity() <= offer.getRequestedQuantity();
    }

    private static boolean sameIdentity(PortfolioModels.OpenOffer left, PortfolioModels.OpenOffer right) {
        return left.getItemId() == right.getItemId()
                && left.getSide().equals(right.getSide())
                && left.getPricePerItem() == right.getPricePerItem()
                && left.getRequestedQuantity() == right.getRequestedQuantity();
    }

    private static Map<String, PortfolioModels.OpenOffer> offersById(List<PortfolioModels.OpenOffer> offers) {
        Map<String, PortfolioModels.OpenOffer> result = new LinkedHashMap<>();
        for (PortfolioModels.OpenOffer offer : offers) {
            if (offer == null || offer.getId() == null || offer.getId().isEmpty() || offer.getItemId() <= 0L
                    || offer.getRequestedQuantity() <= 0L || offer.getPricePerItem() <= 0L
                    || offer.getFilledQuantity() < 0L || offer.getFilledQuantity() > offer.getRequestedQuantity()
                    || result.put(offer.getId(), offer) != null) {
                throw new IllegalArgumentException("Invalid offer representation.");
            }
        }
        return result;
    }

    private static Map<Long, StockAmounts> stockAmounts(List<CapturedPortfolio.Stock> stock) {
        Map<Long, StockAmounts> result = new HashMap<>();
        for (CapturedPortfolio.Stock item : stock) {
            if (item == null || item.getItemId() <= 0L || item.getCarriedQuantity() < 0L || item.getListedQuantity() < 0L) {
                throw new IllegalArgumentException("Invalid stock representation.");
            }
            StockAmounts amounts = result.computeIfAbsent(item.getItemId(), ignored -> new StockAmounts());
            amounts.total = Math.addExact(amounts.total, Math.addExact(item.getCarriedQuantity(), item.getListedQuantity()));
            amounts.listed = Math.addExact(amounts.listed, item.getListedQuantity());
        }
        return result;
    }

    private static void normalizeStock(Map<Long, StockAmounts> stock) {
        stock.entrySet().removeIf(entry -> entry.getValue().total == 0L && entry.getValue().listed == 0L);
        for (StockAmounts amounts : stock.values()) {
            if (amounts.total < 0L || amounts.listed < 0L || amounts.listed > amounts.total) {
                throw new IllegalArgumentException("Stock quantities do not reconcile.");
            }
        }
    }

    private static boolean isCreate(PortfolioModels.Action action) {
        return action != null && ("CREATE_BUY".equals(action.getType()) || "CREATE_SELL".equals(action.getType()));
    }

    private static void validateAction(PortfolioModels.Action action) {
        if (action.getItemId() <= 0L || action.getQuantity() <= 0L || action.getPricePerItem() <= 0L) {
            throw new IllegalArgumentException("Invalid planned create action.");
        }
    }

    private static final class StockAmounts {
        private long total;
        private long listed;

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof StockAmounts)) {
                return false;
            }
            StockAmounts amounts = (StockAmounts) other;
            return total == amounts.total && listed == amounts.listed;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(total) * 31 + Long.hashCode(listed);
        }
    }

    private static final class RecognizedCreate {
        private final PortfolioModels.Action action;
        private final PortfolioModels.OpenOffer offer;

        private RecognizedCreate(PortfolioModels.Action action, PortfolioModels.OpenOffer offer) {
            this.action = action;
            this.offer = offer;
        }
    }
}

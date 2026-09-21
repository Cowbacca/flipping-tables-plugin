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
    private final Map<String, PortfolioModels.OpenOffer> replacedOffers;

    private PortfolioPlanProgress(CapturedPortfolio portfolio, List<PortfolioModels.Action> pendingActions,
            List<PortfolioModels.Action> completedActions, Map<String, PortfolioModels.OpenOffer> replacedOffers) {
        this.portfolio = portfolio;
        this.pendingActions = Collections.unmodifiableList(new ArrayList<>(pendingActions));
        this.completedActions = Collections.unmodifiableList(new ArrayList<>(completedActions));
        this.replacedOffers = Collections.unmodifiableMap(new LinkedHashMap<>(replacedOffers));
    }

    public static PortfolioPlanProgress start(CapturedPortfolio portfolio, List<PortfolioModels.Action> actions) {
        if (portfolio == null || actions == null) {
            throw new IllegalArgumentException("Portfolio and actions are required.");
        }
        List<PortfolioModels.Action> pending = new ArrayList<>();
        for (PortfolioModels.Action action : actions) {
            if (isCreate(action) || isCancel(action) || isReprice(action)) {
                validateAction(action);
                pending.add(action);
            }
        }
        return new PortfolioPlanProgress(portfolio, pending, Collections.emptyList(), offersById(portfolio.getOpenOffers()));
    }

    public Optional<PortfolioPlanProgress> advance(CapturedPortfolio current) {
        if (current == null || current.getTotalSlots() != portfolio.getTotalSlots()
                || current.isMembers() != portfolio.isMembers()) {
            return Optional.empty();
        }
        try {
            Map<String, PortfolioModels.OpenOffer> previousOffers = offersById(portfolio.getOpenOffers());
            Map<String, PortfolioModels.OpenOffer> currentOffers = offersById(current.getOpenOffers());
            if (!existingOffersProgressMonotonically(previousOffers, currentOffers, pendingActions)) {
                return Optional.empty();
            }

            List<RecognizedCreate> recognized = new ArrayList<>();
            List<PortfolioModels.Action> remaining = new ArrayList<>(pendingActions);
            List<PortfolioModels.Action> completed = new ArrayList<>(completedActions);
            boolean cancellationObserved = false;
            boolean repriceObserved = false;
            boolean repriceCancellationObserved = false;
            java.util.Set<String> repricedOfferIds = new java.util.HashSet<>();
            for (PortfolioModels.OpenOffer offer : previousOffers.values()) {
                PortfolioModels.OpenOffer now = currentOffers.get(offer.getId());
                if (now != null && sameIdentity(offer, now)) {
                    continue;
                }
                if (now == null && hasReprice(remaining, offer, null)) {
                    repriceCancellationObserved = true;
                    continue;
                }
                PortfolioModels.Action reprice = now == null ? null : takeExactReprice(remaining, offer, now);
                if (reprice != null) {
                    repriceObserved = true;
                    repricedOfferIds.add(offer.getId());
                    completed.add(reprice);
                    continue;
                }
                PortfolioModels.Action action = takeCancel(remaining, offer);
                if (action == null) {
                    return Optional.empty();
                }
                cancellationObserved = true;
                completed.add(action);
            }
            for (PortfolioModels.OpenOffer offer : currentOffers.values()) {
                PortfolioModels.OpenOffer previous = previousOffers.get(offer.getId());
                if (previous != null && (sameIdentity(previous, offer) || repricedOfferIds.contains(offer.getId()))) {
                    continue;
                }
                PortfolioModels.Action action = takeExactReprice(remaining, replacedOffers, currentOffers, offer);
                if (action != null) {
                    repriceObserved = true;
                    completed.add(action);
                    continue;
                }
                action = takeExactCreate(remaining, offer);
                if (action == null) {
                    return Optional.empty();
                }
                recognized.add(new RecognizedCreate(action, offer));
            }
            if (cancellationObserved || repriceObserved || repriceCancellationObserved) {
                for (RecognizedCreate create : recognized) {
                    completed.add(create.action);
                }
                return Optional.of(new PortfolioPlanProgress(current, remaining, completed, replacedOffers));
            }
            if (!walletMatches(current, recognized) || !stockMatches(current, previousOffers, currentOffers, recognized)) {
                return Optional.empty();
            }
            for (RecognizedCreate create : recognized) {
                completed.add(create.action);
            }
            return Optional.of(new PortfolioPlanProgress(current, remaining, completed, replacedOffers));
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

    public PortfolioPlanProgress rebase(CapturedPortfolio current) {
        if (current == null || current.getTotalSlots() != portfolio.getTotalSlots()
                || current.isMembers() != portfolio.isMembers()) {
            throw new IllegalArgumentException("Portfolio cannot be rebased across worlds.");
        }
        return new PortfolioPlanProgress(current, pendingActions, completedActions, replacedOffers);
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
            Map<String, PortfolioModels.OpenOffer> current, List<PortfolioModels.Action> actions) {
        for (PortfolioModels.OpenOffer offer : previous.values()) {
            PortfolioModels.OpenOffer now = current.get(offer.getId());
            if ((now == null || !sameIdentity(offer, now)) && (hasCancel(actions, offer) || hasReprice(actions, offer, now))) {
                continue;
            }
            if (now == null || !sameIdentity(offer, now) || now.getFilledQuantity() < offer.getFilledQuantity()
                    || now.getFilledQuantity() > now.getRequestedQuantity()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasCancel(List<PortfolioModels.Action> actions, PortfolioModels.OpenOffer offer) {
        return actions.stream().anyMatch(action -> isCancel(action) && offer.getId().equals(action.getReplacesOfferId()));
    }

    private static boolean hasReprice(List<PortfolioModels.Action> actions, PortfolioModels.OpenOffer offer,
            PortfolioModels.OpenOffer current) {
        return actions.stream().anyMatch(action -> isReprice(action) && offer.getId().equals(action.getReplacesOfferId())
                && (current == null || matchesReprice(action, offer, current)));
    }

    private static PortfolioModels.Action takeCancel(List<PortfolioModels.Action> actions, PortfolioModels.OpenOffer offer) {
        for (int index = 0; index < actions.size(); index++) {
            PortfolioModels.Action action = actions.get(index);
            if (isCancel(action) && offer.getId().equals(action.getReplacesOfferId())) {
                actions.remove(index);
                return action;
            }
        }
        return null;
    }

    private static PortfolioModels.Action takeExactReprice(List<PortfolioModels.Action> actions,
            PortfolioModels.OpenOffer previous, PortfolioModels.OpenOffer current) {
        for (int index = 0; index < actions.size(); index++) {
            PortfolioModels.Action action = actions.get(index);
            if (matchesReprice(action, previous, current)) {
                actions.remove(index);
                return action;
            }
        }
        return null;
    }

    private static PortfolioModels.Action takeExactReprice(List<PortfolioModels.Action> actions,
            Map<String, PortfolioModels.OpenOffer> replacedOffers, Map<String, PortfolioModels.OpenOffer> currentOffers,
            PortfolioModels.OpenOffer current) {
        for (int index = 0; index < actions.size(); index++) {
            PortfolioModels.Action action = actions.get(index);
            PortfolioModels.OpenOffer previous = replacedOffers.get(action.getReplacesOfferId());
            PortfolioModels.OpenOffer original = previous == null ? null : currentOffers.get(previous.getId());
            if (previous != null && (original == null || !sameIdentity(previous, original))
                    && matchesReprice(action, previous, current)) {
                actions.remove(index);
                return action;
            }
        }
        return null;
    }

    private static boolean matchesReprice(PortfolioModels.Action action, PortfolioModels.OpenOffer previous,
            PortfolioModels.OpenOffer current) {
        return isReprice(action) && previous.getId().equals(action.getReplacesOfferId())
                && current.getItemId() == action.getItemId() && current.getSide().equals(previous.getSide())
                && current.getPricePerItem() == action.getPricePerItem()
                && current.getRequestedQuantity() == action.getQuantity()
                && current.getFilledQuantity() >= 0L && current.getFilledQuantity() <= current.getRequestedQuantity();
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

    private static boolean isCancel(PortfolioModels.Action action) {
        return action != null && "CANCEL".equals(action.getType());
    }

    private static boolean isReprice(PortfolioModels.Action action) {
        return action != null && "REPRICE".equals(action.getType());
    }

    private static void validateAction(PortfolioModels.Action action) {
        if (action.getItemId() <= 0L || action.getQuantity() <= 0L || action.getPricePerItem() < 0L
                || ((isCancel(action) || isReprice(action))
                && (action.getReplacesOfferId() == null || action.getReplacesOfferId().isEmpty()))) {
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

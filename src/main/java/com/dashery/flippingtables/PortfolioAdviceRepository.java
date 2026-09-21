package com.dashery.flippingtables;

import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class PortfolioAdviceRepository {
    private PortfolioModels.AdviceResponse response;
    private PortfolioModels.Snapshot snapshot;
    private PortfolioModels.Action selected;
    private Instant savedAt;
    private boolean stale;
    private final Set<PortfolioModels.Action> completed = new HashSet<>();

    public synchronized void save(PortfolioModels.AdviceResponse response, PortfolioModels.Snapshot snapshot) {
        this.response = response;
        this.snapshot = snapshot;
        this.savedAt = Instant.now();
        this.selected = null;
        this.stale = false;
        completed.clear();
    }

    public synchronized void clear() {
        response = null;
        snapshot = null;
        selected = null;
        savedAt = null;
        stale = false;
        completed.clear();
    }

    public synchronized boolean isExpired() {
        return savedAt != null && !Instant.now().isBefore(savedAt.plus(Duration.ofMinutes(5)));
    }

    public synchronized void markStale() {
        if (response != null) {
            stale = true;
        }
    }

    public synchronized boolean isActionable() {
        return response != null && !isExpired();
    }

    public synchronized boolean hasAdvice() {
        return response != null;
    }

    public synchronized boolean isStale() {
        return stale;
    }

    public synchronized void select(PortfolioModels.Action action) {
        if (!isActionable() || !isOfferSuggestion(action) || completed.contains(action)
                || !response.getAdvice().getActions().contains(action)) {
            throw new IllegalStateException("Request fresh advice before selecting a suggestion.");
        }
        selected = action;
    }

    public synchronized Optional<PortfolioModels.Action> selectedFor(int itemId, String side) {
        if (!isActionable()) {
            return Optional.empty();
        }
        if (selected != null && !completed.contains(selected) && selected.getItemId() == itemId
                && matchesSide(selected, side)) {
            return Optional.of(selected);
        }
        List<PortfolioModels.Action> matching = availableActions().stream()
                .filter(action -> action.getItemId() == itemId && matchesSide(action, side))
                .collect(Collectors.toList());
        return matching.size() == 1 ? Optional.of(matching.get(0)) : Optional.empty();
    }

    public synchronized void markCompleted(List<PortfolioModels.Action> actions) {
        if (response != null) {
            actions.stream().filter(response.getAdvice().getActions()::contains).forEach(completed::add);
            if (completed.contains(selected)) {
                selected = null;
            }
        }
    }

    public synchronized boolean isCompleted(PortfolioModels.Action action) {
        return completed.contains(action);
    }

    public synchronized List<PortfolioModels.Action> availableActions() {
        if (!isActionable()) {
            return Collections.emptyList();
        }
        return response.getAdvice().getActions().stream().filter(action -> !completed.contains(action))
                .collect(Collectors.toList());
    }

    public synchronized short[] buyItemIds() {
        if (!isActionable()) {
            return new short[0];
        }
        int[] ids = availableActions().stream()
                .filter(action -> "CREATE_BUY".equals(action.getType()))
                .mapToInt(action -> Math.toIntExact(action.getItemId()))
                .filter(id -> id > 0 && id <= 65535)
                .distinct().toArray();
        short[] result = new short[ids.length];
        for (int index = 0; index < ids.length; index++) {
            result[index] = (short) ids[index];
        }
        return result;
    }

    public synchronized String sideOf(PortfolioModels.Action action) {
        if ("CREATE_BUY".equals(action.getType())) {
            return "BUY";
        }
        if ("CREATE_SELL".equals(action.getType())) {
            return "SELL";
        }
        if (snapshot == null) {
            return "";
        }
        return snapshot.getOpenOffers().stream()
                .filter(offer -> offer.getId().equals(action.getReplacesOfferId()))
                .map(PortfolioModels.OpenOffer::getSide)
                .findFirst().orElse("");
    }

    public synchronized String exactRecommendationIdFor(int itemId, String side, int quantity, int price) {
        if (response == null || isExpired()) {
            return null;
        }
        List<PortfolioModels.Action> matches = availableActions().stream()
                .filter(action -> action.getRecommendationId() != null && action.getItemId() == itemId
                        && (action.getType().equals("CREATE_" + side)
                                || ("REPRICE".equals(action.getType()) && side.equals(sideOf(action))))
                        && action.getQuantity() == quantity
                        && action.getPricePerItem() == price)
                .collect(Collectors.toList());
        return matches.size() == 1 ? matches.get(0).getRecommendationId() : null;
    }

    private boolean matchesSide(PortfolioModels.Action action, String side) {
        return action.getType().equals("CREATE_" + side)
                || ("REPRICE".equals(action.getType()) && side.equals(sideOf(action)));
    }

    private static boolean isOfferSuggestion(PortfolioModels.Action action) {
        return action != null && ("CREATE_BUY".equals(action.getType()) || "CREATE_SELL".equals(action.getType())
                || "REPRICE".equals(action.getType()));
    }
}

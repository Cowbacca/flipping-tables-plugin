package com.dashery.flippingtables;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PortfolioRequestBuilder {
    private PortfolioRequestBuilder() {
    }

    public static PortfolioModels.AdviceRequest create(CapturedPortfolio captured, Set<Long> selectedStock,
            Map<Long, Long> costs, long cashBudget, Duration nextReturnInterval,
            int participation, String accountId) {
        if (cashBudget < 0 || cashBudget > captured.getWalletCoins()) {
            throw new IllegalArgumentException("Cash budget must be between zero and the coins in your inventory. Collect or withdraw coins, then read the portfolio again.");
        }
        if (nextReturnInterval.compareTo(Duration.ofMinutes(5)) < 0 || nextReturnInterval.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Your next return must be between five minutes and seven days away.");
        }
        if (participation < 1 || participation > 100) {
            throw new IllegalArgumentException("Volume participation must be between 1 and 100 percent.");
        }
        List<PortfolioModels.InventoryLot> inventory = new ArrayList<>();
        long totalCost = 0;
        for (CapturedPortfolio.Stock stock : captured.getStock()) {
            long quantity = Math.addExact(stock.getListedQuantity(), selectedStock.contains(stock.getItemId()) ? stock.getCarriedQuantity() : 0);
            long cost = costs.getOrDefault(stock.getItemId(), 0L);
            if (cost < 0 || cost > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Item cost must be between zero and 2,147,483,647 GP.");
            }
            if (quantity > 0) {
                totalCost = Math.addExact(totalCost, Math.multiplyExact(quantity, cost));
                inventory.add(new PortfolioModels.InventoryLot(stock.getItemId(), quantity, cost));
            }
        }
        PortfolioModels.Snapshot snapshot = new PortfolioModels.Snapshot(
                cashBudget, inventory, captured.getOpenOffers(), captured.getTotalSlots(),
                captured.getLimits(), captured.getCapturedAt());
        return new PortfolioModels.AdviceRequest(snapshot, nextReturnInterval.toString(),
                participation, captured.isMembers(), accountId);
    }

    public static PortfolioModels.AdviceRequest create(CapturedPortfolio captured, Set<Long> selectedStock,
            Map<Long, Long> costs, long cashBudget, Duration nextReturnInterval, int participation) {
        return create(captured, selectedStock, costs, cashBudget, nextReturnInterval, participation, null);
    }
}

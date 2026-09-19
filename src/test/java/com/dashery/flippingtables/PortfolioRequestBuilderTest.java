package com.dashery.flippingtables;

import org.junit.Test;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class PortfolioRequestBuilderTest {
    @Test
    public void includesRemainingListedStockAndOnlySelectedCarriedItems() {
        PortfolioModels.AdviceRequest request = PortfolioRequestBuilder.create(portfolio(), Collections.singleton(4151L),
                Collections.singletonMap(4151L, 100L), 500, Duration.ofMinutes(150), 10);
        assertEquals("PT2H30M", request.getNextVisitInterval());
        assertEquals(8, request.getSnapshot().getSlots());
        assertEquals(500, request.getSnapshot().getCashAvailable());
        assertEquals(1, request.getSnapshot().getInventory().size());
        assertEquals(5, request.getSnapshot().getInventory().get(0).getQuantity());
        assertEquals(100, request.getSnapshot().getInventory().get(0).getCostPerItem());
        assertEquals(2, request.getSnapshot().getOpenOffers().get(0).getFilledQuantity());
    }

    @Test
    public void includesExistingSellStockWhenCarriedStockIsNotSelected() {
        PortfolioModels.AdviceRequest request = PortfolioRequestBuilder.create(portfolio(), Collections.emptySet(),
                Collections.emptyMap(), 0, Duration.ofHours(4), 10);
        assertEquals(1, request.getSnapshot().getInventory().size());
        assertEquals(2, request.getSnapshot().getInventory().get(0).getQuantity());
    }

    @Test
    public void cannotSpendBankedOrUncollectedCoins() {
        assertThrows(IllegalArgumentException.class, () -> PortfolioRequestBuilder.create(portfolio(), Collections.emptySet(),
                Collections.emptyMap(), 1001, Duration.ofHours(4), 10));
        assertThrows(IllegalArgumentException.class, () -> PortfolioRequestBuilder.create(portfolio(), Collections.emptySet(),
                Collections.emptyMap(), -1, Duration.ofHours(4), 10));
    }

    @Test
    public void validatesForecastBoundsAndCost() {
        assertThrows(IllegalArgumentException.class, () -> PortfolioRequestBuilder.create(portfolio(), Collections.emptySet(),
                Collections.emptyMap(), 100, Duration.ofMinutes(4), 10));
        assertThrows(IllegalArgumentException.class, () -> PortfolioRequestBuilder.create(portfolio(), Collections.emptySet(),
                Collections.emptyMap(), 100, Duration.ofHours(169), 10));
        assertThrows(IllegalArgumentException.class, () -> PortfolioRequestBuilder.create(portfolio(), Collections.emptySet(),
                Collections.emptyMap(), 100, Duration.ofHours(4), 101));
        assertThrows(IllegalArgumentException.class, () -> PortfolioRequestBuilder.create(portfolio(), Collections.emptySet(),
                Collections.singletonMap(4151L, -1L), 100, Duration.ofHours(4), 10));
    }

    static CapturedPortfolio portfolio() {
        return new CapturedPortfolio(1000,
                Arrays.asList(new PortfolioModels.OpenOffer("slot-0", 4151, "SELL", 120, 4, 2),
                        new PortfolioModels.OpenOffer("slot-1", 1515, "BUY", 80, 100, 10)),
                Arrays.asList(new CapturedPortfolio.Stock(4151, "Abyssal whip", 3, 2),
                        new CapturedPortfolio.Stock(1515, "Yew logs", 10, 0)),
                Collections.emptyMap(), 8, true, "2026-09-19T20:00:00Z", "fingerprint");
    }
}

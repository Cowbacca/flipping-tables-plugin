package com.dashery.flippingtables;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class PortfolioAdviceRepositoryTest {
    @Test
    public void matchesUniqueGeItemWithoutRequiringAnotherSidebarSelection() {
        PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
        PortfolioModels.Action buy = new PortfolioModels.Action("CREATE_BUY", 4151, 2, 100, null);
        repository.save(response(buy), null);
        assertSame(buy, repository.selectedFor(4151, "BUY").get());
        repository.select(buy);
        assertTrue(repository.selectedFor(4151, "BUY").isPresent());
        assertFalse(repository.selectedFor(4151, "SELL").isPresent());
        assertFalse(repository.selectedFor(1515, "BUY").isPresent());
        repository.clear();
        assertFalse(repository.selectedFor(4151, "BUY").isPresent());
    }

    @Test
    public void ambiguousItemsRequireExplicitSelectionAndPlacedActionsAreRemovedFromSearch() {
        PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
        PortfolioModels.Action first = new PortfolioModels.Action("CREATE_BUY", 4151, 2, 100, null);
        PortfolioModels.Action second = new PortfolioModels.Action("CREATE_BUY", 4151, 3, 99, null);
        PortfolioModels.Action next = new PortfolioModels.Action("CREATE_BUY", 1515, 10, 50, null);
        repository.save(response(first, second, next), null);
        assertFalse(repository.selectedFor(4151, "BUY").isPresent());
        repository.select(first);
        assertSame(first, repository.selectedFor(4151, "BUY").get());
        repository.markCompleted(Collections.singletonList(first));
        assertSame(second, repository.selectedFor(4151, "BUY").get());
        repository.markCompleted(Collections.singletonList(second));
        assertFalse(repository.selectedFor(4151, "BUY").isPresent());
        assertArrayEquals(new short[]{1515}, repository.buyItemIds());
        assertThrows(IllegalStateException.class, () -> repository.select(first));
    }

    @Test
    public void searchIdsAreUniqueAndDoNotIncludeKeepsCancellationsOrSales() {
        PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
        repository.save(response(new PortfolioModels.Action("CREATE_BUY", 4151, 1, 100, null),
                new PortfolioModels.Action("CREATE_BUY", 4151, 1, 100, null),
                new PortfolioModels.Action("CREATE_SELL", 1515, 10, 120, null),
                new PortfolioModels.Action("KEEP", 1515, 10, 120, "slot-1")), null);
        assertArrayEquals(new short[]{4151}, repository.buyItemIds());
    }

    private static PortfolioModels.AdviceResponse response(PortfolioModels.Action... actions) {
        return new PortfolioModels.AdviceResponse(1, new PortfolioModels.Advice(Arrays.asList(actions), 0, 0, 0, 0,
                Collections.emptyList(), "EXACT"), "2026-09-19T20:00:00Z");
    }
}

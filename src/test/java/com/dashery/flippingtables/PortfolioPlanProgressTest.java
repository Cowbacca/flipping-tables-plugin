package com.dashery.flippingtables;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PortfolioPlanProgressTest {
    @Test
    public void keepsRemainingActionsAfterExactBuyOfferIsPlaced() {
        PortfolioModels.Action buy = create("CREATE_BUY", 4151, 2, 100);
        PortfolioModels.Action sell = create("CREATE_SELL", 1515, 5, 200);
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(1_000, Collections.emptyList(), stock(1515, 5, 0)),
                Arrays.asList(buy, sell));

        Optional<PortfolioPlanProgress> advanced = progress.advance(portfolio(800,
                Collections.singletonList(offer("slot-0", 4151, "BUY", 100, 2, 0)), stock(1515, 5, 0)));

        assertTrue(advanced.isPresent());
        assertEquals(Collections.singletonList(sell), advanced.get().getPendingActions());
        assertEquals(Collections.singletonList(buy), advanced.get().getCompletedActions());
    }

    @Test
    public void acceptsInstantCompletedSellAndKeepsBuyPending() {
        PortfolioModels.Action buy = create("CREATE_BUY", 4151, 2, 100);
        PortfolioModels.Action sell = create("CREATE_SELL", 1515, 5, 200);
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(1_000, Collections.emptyList(), stock(1515, 5, 0)),
                Arrays.asList(buy, sell));

        Optional<PortfolioPlanProgress> advanced = progress.advance(portfolio(1_000,
                Collections.singletonList(offer("slot-1", 1515, "SELL", 200, 5, 5))));

        assertTrue(advanced.isPresent());
        assertEquals(Collections.singletonList(buy), advanced.get().pendingActions());
        assertEquals(Collections.singletonList(sell), advanced.get().getCompletedActions());
    }

    @Test
    public void acceptsMonotonicFillOfExistingSellOfferWithoutNewPlanAction() {
        PortfolioModels.OpenOffer original = offer("slot-2", 1515, "SELL", 200, 5, 1);
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(1_000, Collections.singletonList(original), stock(1515, 0, 4)),
                Collections.emptyList());

        Optional<PortfolioPlanProgress> advanced = progress.advance(portfolio(1_000,
                Collections.singletonList(offer("slot-2", 1515, "SELL", 200, 5, 5))));

        assertTrue(advanced.isPresent());
        assertTrue(advanced.get().getPendingActions().isEmpty());
    }

    @Test
    public void rejectsUnknownOfferAndUnexplainedWalletOrStockChanges() {
        PortfolioModels.Action buy = create("CREATE_BUY", 4151, 2, 100);
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(1_000, Collections.emptyList(), stock(1515, 5, 0)),
                Collections.singletonList(buy));

        assertFalse(progress.advance(portfolio(1_000,
                Collections.singletonList(offer("slot-0", 4151, "BUY", 101, 2, 0)), stock(1515, 5, 0))).isPresent());
        assertFalse(progress.advance(portfolio(801,
                Collections.singletonList(offer("slot-0", 4151, "BUY", 100, 2, 0)), stock(1515, 5, 0))).isPresent());
        assertFalse(progress.advance(portfolio(800,
                Collections.singletonList(offer("slot-0", 4151, "BUY", 100, 2, 0)), stock(1515, 4, 0))).isPresent());
    }

    @Test
    public void rejectsCancellationRepriceAndNonMonotonicExistingOffer() {
        PortfolioModels.OpenOffer original = offer("slot-3", 4151, "BUY", 100, 2, 1);
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(800, Collections.singletonList(original)),
                Collections.emptyList());

        assertFalse(progress.advance(portfolio(800, Collections.emptyList())).isPresent());
        assertFalse(progress.advance(portfolio(800,
                Collections.singletonList(offer("slot-3", 4151, "BUY", 101, 2, 1)))).isPresent());
        assertFalse(progress.advance(portfolio(800,
                Collections.singletonList(offer("slot-3", 4151, "BUY", 100, 2, 0)))).isPresent());
    }

    @Test
    public void recordsPlannedCancellationThenKeepsTheNextBuyUsableAfterCollection() {
        PortfolioModels.OpenOffer existingBuy = offer("slot-3", 4151, "BUY", 100, 2, 0);
        PortfolioModels.Action cancel = new PortfolioModels.Action("CANCEL", 4151, 2, 100, "slot-3");
        PortfolioModels.Action nextBuy = create("CREATE_BUY", 1515, 1, 100);
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(800, Collections.singletonList(existingBuy)),
                Arrays.asList(cancel, nextBuy));

        Optional<PortfolioPlanProgress> cancelled = progress.advance(portfolio(800, Collections.emptyList()));
        assertTrue(cancelled.isPresent());
        assertEquals(Collections.singletonList(cancel), cancelled.get().getCompletedActions());
        assertEquals(Collections.singletonList(nextBuy), cancelled.get().getPendingActions());

        PortfolioPlanProgress collected = cancelled.get().rebase(portfolio(1_000, Collections.emptyList()));
        Optional<PortfolioPlanProgress> placed = collected.advance(portfolio(900,
                Collections.singletonList(offer("slot-1", 1515, "BUY", 100, 1, 0))));
        assertTrue(placed.isPresent());
        assertEquals(Arrays.asList(cancel, nextBuy), placed.get().getCompletedActions());
    }

    @Test
    public void recordsCancellationAndExactReplacementWhenTheSlotIsReused() {
        PortfolioModels.OpenOffer existingBuy = offer("slot-3", 4151, "BUY", 100, 2, 0);
        PortfolioModels.Action cancel = new PortfolioModels.Action("CANCEL", 4151, 2, 100, "slot-3");
        PortfolioModels.Action nextBuy = create("CREATE_BUY", 1515, 1, 100);
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(800, Collections.singletonList(existingBuy)),
                Arrays.asList(cancel, nextBuy));

        Optional<PortfolioPlanProgress> advanced = progress.advance(portfolio(700,
                Collections.singletonList(offer("slot-3", 1515, "BUY", 100, 1, 0))));

        assertTrue(advanced.isPresent());
        assertEquals(Arrays.asList(cancel, nextBuy), advanced.get().getCompletedActions());
        assertTrue(advanced.get().getPendingActions().isEmpty());
    }

    @Test
    public void recordsAnExactRepriceWhenTheSellSlotIsReused() {
        PortfolioModels.OpenOffer existingSell = offer("slot-3", 4151, "SELL", 100, 2, 0);
        PortfolioModels.Action reprice = new PortfolioModels.Action("REPRICE", 4151, 2, 120, "slot-3");
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(1_000, Collections.singletonList(existingSell),
                stock(4151, 0, 2)), Collections.singletonList(reprice));

        Optional<PortfolioPlanProgress> advanced = progress.advance(portfolio(1_000,
                Collections.singletonList(offer("slot-3", 4151, "SELL", 120, 2, 0)), stock(4151, 0, 2)));

        assertTrue(advanced.isPresent());
        assertEquals(Collections.singletonList(reprice), advanced.get().getCompletedActions());
    }

    @Test
    public void recordsARepriceAfterItsSellOfferIsCancelledCollectedAndRecreated() {
        PortfolioModels.OpenOffer existingSell = offer("slot-3", 4151, "SELL", 100, 2, 0);
        PortfolioModels.Action reprice = new PortfolioModels.Action("REPRICE", 4151, 2, 120, "slot-3");
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(1_000, Collections.singletonList(existingSell),
                stock(4151, 0, 2)), Collections.singletonList(reprice));

        Optional<PortfolioPlanProgress> cancelled = progress.advance(portfolio(1_000, Collections.emptyList()));
        assertTrue(cancelled.isPresent());
        PortfolioPlanProgress collected = cancelled.get().rebase(portfolio(1_000, Collections.emptyList(), stock(4151, 2, 0)));
        Optional<PortfolioPlanProgress> recreated = collected.advance(portfolio(1_000,
                Collections.singletonList(offer("slot-1", 4151, "SELL", 120, 2, 0)), stock(4151, 0, 2)));

        assertTrue(recreated.isPresent());
        assertEquals(Collections.singletonList(reprice), recreated.get().getCompletedActions());
    }

    @Test
    public void doesNotTreatAnExtraOfferAsARepriceWhileTheOriginalIsStillOpen() {
        PortfolioModels.OpenOffer existingSell = offer("slot-3", 4151, "SELL", 100, 2, 0);
        PortfolioModels.Action reprice = new PortfolioModels.Action("REPRICE", 4151, 2, 120, "slot-3");
        PortfolioPlanProgress progress = PortfolioPlanProgress.start(portfolio(1_000, Collections.singletonList(existingSell),
                stock(4151, 0, 2)), Collections.singletonList(reprice));

        Optional<PortfolioPlanProgress> advanced = progress.advance(portfolio(1_000, Arrays.asList(existingSell,
                offer("slot-1", 4151, "SELL", 120, 2, 0)), stock(4151, 0, 4)));

        assertFalse(advanced.isPresent());
    }

    private static PortfolioModels.Action create(String type, long itemId, long quantity, long price) {
        return new PortfolioModels.Action(type, itemId, quantity, price, null);
    }

    private static PortfolioModels.OpenOffer offer(String id, long itemId, String side, long price, long quantity, long filled) {
        return new PortfolioModels.OpenOffer(id, itemId, side, price, quantity, filled);
    }

    private static CapturedPortfolio.Stock stock(long itemId, long carried, long listed) {
        return new CapturedPortfolio.Stock(itemId, "Item " + itemId, carried, listed);
    }

    private static CapturedPortfolio portfolio(long wallet, List<PortfolioModels.OpenOffer> offers, CapturedPortfolio.Stock... stock) {
        return new CapturedPortfolio(wallet, offers, Arrays.asList(stock), Collections.emptyMap(), 8, true,
                "2026-09-20T10:00:00Z", "fingerprint");
    }
}

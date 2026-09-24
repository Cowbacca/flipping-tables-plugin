package com.dashery.flippingtables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PortfolioModels
{
	private PortfolioModels()
	{
	}

	private static <T> List<T> immutableList(List<T> values)
	{
		List<T> safeValues = values == null ? Collections.<T>emptyList() : values;
		return Collections.unmodifiableList(new ArrayList<>(safeValues));
	}

	private static <K, V> Map<K, V> immutableMap(Map<K, V> values)
	{
		Map<K, V> safeValues = values == null ? Collections.<K, V>emptyMap() : values;
		return Collections.unmodifiableMap(new LinkedHashMap<>(safeValues));
	}

	public static final class Snapshot
	{
		private final long cashAvailable;
		private final List<InventoryLot> inventory;
		private final List<OpenOffer> openOffers;
		private final int slots;
		private final Map<Long, ItemLimit> limits;
		private final String capturedAt;

		public Snapshot(long cashAvailable, List<InventoryLot> inventory, List<OpenOffer> openOffers, int slots, Map<Long, ItemLimit> limits, String capturedAt)
		{
			this.cashAvailable = cashAvailable;
			this.inventory = immutableList(inventory);
			this.openOffers = immutableList(openOffers);
			this.slots = slots;
			this.limits = immutableMap(limits);
			this.capturedAt = capturedAt;
		}

		public long getCashAvailable() { return cashAvailable; }
		public List<InventoryLot> getInventory() { return inventory; }
		public List<OpenOffer> getOpenOffers() { return openOffers; }
		public int getSlots() { return slots; }
		public Map<Long, ItemLimit> getLimits() { return limits; }
		public String getCapturedAt() { return capturedAt; }
	}

	public static final class InventoryLot
	{
		private final long itemId;
		private final long quantity;
		private final long costPerItem;

		public InventoryLot(long itemId, long quantity, long costPerItem)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.costPerItem = costPerItem;
		}

		public long getItemId() { return itemId; }
		public long getQuantity() { return quantity; }
		public long getCostPerItem() { return costPerItem; }
	}

	public static final class OpenOffer
	{
		private final String id;
		private final long itemId;
		private final String side;
		private final long pricePerItem;
		private final long requestedQuantity;
		private final long filledQuantity;

		public OpenOffer(String id, long itemId, String side, long pricePerItem, long requestedQuantity, long filledQuantity)
		{
			this.id = id;
			this.itemId = itemId;
			this.side = side;
			this.pricePerItem = pricePerItem;
			this.requestedQuantity = requestedQuantity;
			this.filledQuantity = filledQuantity;
		}

		public String getId() { return id; }
		public long getItemId() { return itemId; }
		public String getSide() { return side; }
		public long getPricePerItem() { return pricePerItem; }
		public long getRequestedQuantity() { return requestedQuantity; }
		public long getFilledQuantity() { return filledQuantity; }
	}

	public static final class ItemLimit
	{
		private final long limit;
		private final long used;
		private final String refreshAt;

		public ItemLimit(long limit, long used, String refreshAt)
		{
			this.limit = limit;
			this.used = used;
			this.refreshAt = refreshAt;
		}

		public long getLimit() { return limit; }
		public long getUsed() { return used; }
		public String getRefreshAt() { return refreshAt; }
	}

	public static final class AdviceRequest
	{
		private final Snapshot snapshot;
		private final String nextReturnInterval;
		private final long volumeParticipationPercent;
		private final boolean members;
		private final String accountId;

		public AdviceRequest(Snapshot snapshot, String nextReturnInterval, long volumeParticipationPercent, boolean members)
		{
			this(snapshot, nextReturnInterval, volumeParticipationPercent, members, null);
		}

		public AdviceRequest(Snapshot snapshot, String nextReturnInterval,
				long volumeParticipationPercent, boolean members, String accountId)
		{
			this.snapshot = snapshot;
			this.nextReturnInterval = nextReturnInterval;
			this.volumeParticipationPercent = volumeParticipationPercent;
			this.members = members;
			this.accountId = accountId;
		}

		public Snapshot getSnapshot() { return snapshot; }
		public String getNextReturnInterval() { return nextReturnInterval; }
		public long getVolumeParticipationPercent() { return volumeParticipationPercent; }
		public boolean isMembers() { return members; }
		public String getAccountId() { return accountId; }
	}

	public static final class AdviceResponse
	{
		private final long snapshotId;
		private final Advice advice;
		private final String marketDataThrough;

		public AdviceResponse(long snapshotId, Advice advice, String marketDataThrough)
		{
			this.snapshotId = snapshotId;
			this.advice = advice;
			this.marketDataThrough = marketDataThrough;
		}

		public long getSnapshotId() { return snapshotId; }
		public Advice getAdvice() { return advice; }
		public String getMarketDataThrough() { return marketDataThrough; }
	}

	public static final class Advice
	{
		private final List<Action> actions;
		private final List<InventoryGuidance> inventoryGuidance;
		private final long projectedCashCommitted;
		private final long realisedProfit;
		private final long inventoryCost;
		private final long conservativeInventoryValue;
		private final Long expectedProfit;
		private final List<String> limitations;
		private final String searchStatus;

		public Advice(List<Action> actions, long projectedCashCommitted, long realisedProfit, long inventoryCost, long conservativeInventoryValue, List<String> limitations, String searchStatus)
		{
			this(actions, Collections.<InventoryGuidance>emptyList(), projectedCashCommitted, realisedProfit, inventoryCost,
				conservativeInventoryValue, limitations, searchStatus);
		}

		public Advice(List<Action> actions, List<InventoryGuidance> inventoryGuidance, long projectedCashCommitted,
				long realisedProfit, long inventoryCost, long conservativeInventoryValue, List<String> limitations, String searchStatus)
		{
			this(actions, inventoryGuidance, projectedCashCommitted, realisedProfit, inventoryCost,
				conservativeInventoryValue, null, limitations, searchStatus);
		}

		public Advice(List<Action> actions, List<InventoryGuidance> inventoryGuidance, long projectedCashCommitted,
				long realisedProfit, long inventoryCost, long conservativeInventoryValue, Long expectedProfit,
				List<String> limitations, String searchStatus)
		{
			this.actions = immutableList(actions);
			this.inventoryGuidance = immutableList(inventoryGuidance);
			this.projectedCashCommitted = projectedCashCommitted;
			this.realisedProfit = realisedProfit;
			this.inventoryCost = inventoryCost;
			this.conservativeInventoryValue = conservativeInventoryValue;
			this.expectedProfit = expectedProfit;
			this.limitations = immutableList(limitations);
			this.searchStatus = searchStatus;
		}

		public List<Action> getActions() { return actions; }
		public List<InventoryGuidance> getInventoryGuidance() { return inventoryGuidance == null ? Collections.<InventoryGuidance>emptyList() : inventoryGuidance; }
		public long getProjectedCashCommitted() { return projectedCashCommitted; }
		public long getRealisedProfit() { return realisedProfit; }
		public long getInventoryCost() { return inventoryCost; }
		public long getConservativeInventoryValue() { return conservativeInventoryValue; }
		public Long getExpectedProfit() { return expectedProfit; }
		public List<String> getLimitations() { return limitations; }
		public String getSearchStatus() { return searchStatus; }
	}

	public static final class Action
	{
		private final String type;
		private final long itemId;
		private final long quantity;
		private final long pricePerItem;
		private final String replacesOfferId;
		private final String recommendationId;

		public Action(String type, long itemId, long quantity, long pricePerItem, String replacesOfferId)
		{
			this(type, itemId, quantity, pricePerItem, replacesOfferId, null);
		}

		public Action(String type, long itemId, long quantity, long pricePerItem, String replacesOfferId, String recommendationId)
		{
			this.type = type;
			this.itemId = itemId;
			this.quantity = quantity;
			this.pricePerItem = pricePerItem;
			this.replacesOfferId = replacesOfferId;
			this.recommendationId = recommendationId;
		}

		public String getType() { return type; }
		public long getItemId() { return itemId; }
		public long getQuantity() { return quantity; }
		public long getPricePerItem() { return pricePerItem; }
		public String getReplacesOfferId() { return replacesOfferId; }
		public String getRecommendationId() { return recommendationId; }
	}

	public static final class InventoryGuidance
	{
		private final long itemId;
		private final long quantity;
		private final long listedQuantity;
		private final String status;
		private final String message;
		private final SaleQuote quote;

		public InventoryGuidance(long itemId, long quantity, long listedQuantity, String status, String message, SaleQuote quote)
		{
			this.itemId = itemId;
			this.quantity = quantity;
			this.listedQuantity = listedQuantity;
			this.status = status;
			this.message = message;
			this.quote = quote;
		}

		public long getItemId() { return itemId; }
		public long getQuantity() { return quantity; }
		public long getListedQuantity() { return listedQuantity; }
		public String getStatus() { return status; }
		public String getMessage() { return message; }
		public SaleQuote getQuote() { return quote; }
	}

	public static final class SaleQuote
	{
		private final long pricePerItem;
		private final String evidenceWindow;
		private final String latestObservationAt;
		private final boolean usedFallback;
		private final long observedVolume;
		private final long projectedVolume;

		public SaleQuote(long pricePerItem, String evidenceWindow, String latestObservationAt, boolean usedFallback,
				long observedVolume, long projectedVolume)
		{
			this.pricePerItem = pricePerItem;
			this.evidenceWindow = evidenceWindow;
			this.latestObservationAt = latestObservationAt;
			this.usedFallback = usedFallback;
			this.observedVolume = observedVolume;
			this.projectedVolume = projectedVolume;
		}

		public long getPricePerItem() { return pricePerItem; }
		public String getEvidenceWindow() { return evidenceWindow; }
		public String getLatestObservationAt() { return latestObservationAt; }
		public boolean isUsedFallback() { return usedFallback; }
		public long getObservedVolume() { return observedVolume; }
		public long getProjectedVolume() { return projectedVolume; }
	}
}

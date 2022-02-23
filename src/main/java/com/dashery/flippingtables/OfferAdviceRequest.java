package com.dashery.flippingtables;

import lombok.Data;

import java.util.Map;

@Data
public class OfferAdviceRequest {
    private final int slotsAvailable;
    private final int moneyAvailable;
    private final BuySellWindows buySellWindows;
    private final Map<Long, GeLimitUsage> geLimitsAlreadyUsed;
    private final boolean members;
    private final double timeLimitSeconds;
    private final boolean useMipStrategy;
}

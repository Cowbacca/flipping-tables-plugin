package com.dashery.flippingtables;

import lombok.Data;

@Data
public class ItemSellAdvice {
    private final long itemId;
    private final long safePrice;
    private final long riskierPrice;
}

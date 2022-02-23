package com.dashery.flippingtables;

import lombok.Data;

import java.util.Set;

@Data
public class SellAdvice {
    private final Set<ItemSellAdvice> itemSellAdvices;
}

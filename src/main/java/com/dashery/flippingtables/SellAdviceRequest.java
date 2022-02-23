package com.dashery.flippingtables;

import lombok.Data;

import java.util.Set;

@Data
public class SellAdviceRequest {
    private final Set<ItemToSell> itemsToSell;
    private final SerializableDuration sellWindow;
}
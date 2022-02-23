package com.dashery.flippingtables;

import lombok.Data;

@Data
public class BuySellWindows {
    private final SerializableDuration buyWindow;
    private final SerializableDuration sellWindow;
}

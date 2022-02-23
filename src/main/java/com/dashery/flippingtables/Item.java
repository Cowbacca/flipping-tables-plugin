package com.dashery.flippingtables;

import lombok.Data;

@Data
public class Item {
    private final String name;
    private final String itemId;

    public long getItemIdAsLong() {
        return Long.parseLong(itemId);
    }

    public boolean hasId(int itemId) {
        return Integer.parseInt(this.itemId) == itemId;
    }
}

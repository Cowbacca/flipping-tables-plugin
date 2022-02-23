package com.dashery.flippingtables;

import lombok.Data;

@Data
public class Offers {
    private final Item item;
    private final Offer buyOffer;
    private final Offer sellOffer;

    public long getItemIdAsLong() {
        return item.getItemIdAsLong();
    }

    public boolean isForItem(int itemId) {
        return item.hasId(itemId);
    }

    public long getBuyPrice() {
        return buyOffer.getPricePerItem();
    }

    public long getBuyQuantity() {
        return buyOffer.getNumberOfItems();
    }
}

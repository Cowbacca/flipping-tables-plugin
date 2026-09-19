package com.dashery.flippingtables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapturedPortfolio {
    private final long walletCoins;
    private final List<PortfolioModels.OpenOffer> openOffers;
    private final List<Stock> stock;
    private final Map<Long, PortfolioModels.ItemLimit> limits;
    private final int totalSlots;
    private final boolean members;
    private final String capturedAt;
    private final String fingerprint;

    public CapturedPortfolio(long walletCoins, List<PortfolioModels.OpenOffer> openOffers, List<Stock> stock,
            Map<Long, PortfolioModels.ItemLimit> limits, int totalSlots, boolean members, String capturedAt,
            String fingerprint) {
        this.walletCoins = walletCoins;
        this.openOffers = Collections.unmodifiableList(new ArrayList<>(openOffers));
        this.stock = Collections.unmodifiableList(new ArrayList<>(stock));
        this.limits = Collections.unmodifiableMap(new LinkedHashMap<>(limits));
        this.totalSlots = totalSlots;
        this.members = members;
        this.capturedAt = capturedAt;
        this.fingerprint = fingerprint;
    }

    public long getWalletCoins() {
        return walletCoins;
    }

    public List<PortfolioModels.OpenOffer> getOpenOffers() {
        return openOffers;
    }

    public List<Stock> getStock() {
        return stock;
    }

    public Map<Long, PortfolioModels.ItemLimit> getLimits() {
        return limits;
    }

    public int getTotalSlots() {
        return totalSlots;
    }

    public boolean isMembers() {
        return members;
    }

    public String getCapturedAt() {
        return capturedAt;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public static final class Stock {
        private final long itemId;
        private final String name;
        private final long carriedQuantity;
        private final long listedQuantity;

        public Stock(long itemId, String name, long carriedQuantity, long listedQuantity) {
            this.itemId = itemId;
            this.name = name;
            this.carriedQuantity = carriedQuantity;
            this.listedQuantity = listedQuantity;
        }

        public long getItemId() {
            return itemId;
        }

        public String getName() {
            return name;
        }

        public long getCarriedQuantity() {
            return carriedQuantity;
        }

        public long getListedQuantity() {
            return listedQuantity;
        }
    }
}

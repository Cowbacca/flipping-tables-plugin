package com.dashery.flippingtables;

import com.google.inject.Singleton;

import java.util.Optional;

@Singleton
public class OfferAdviceRepository {
    private OfferAdvice offerAdvice;

    public OfferAdvice save(OfferAdvice offerAdvice) {
        this.offerAdvice = offerAdvice;
        return this.offerAdvice;
    }

    public Optional<OfferAdvice> find() {
        return Optional.ofNullable(this.offerAdvice);
    }
}

package com.dashery.flippingtables;

import lombok.Data;

import javax.annotation.Nullable;

@Data
public class OfferAdviceJob {
    private final String id;
    @Nullable
    private final OfferAdvice offerAdvice;
}

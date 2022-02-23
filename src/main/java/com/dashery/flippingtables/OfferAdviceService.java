package com.dashery.flippingtables;

import lombok.AllArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject}))
public class OfferAdviceService {
    private final FlippingTablesClient flippingTablesClient;
    private final OfferAdviceRepository offerAdviceRepository;


    public Optional<Long> findPriceForItem(int itemId) {
        return offerAdviceRepository.find()
                .flatMap(offerAdvice -> offerAdvice.priceForItem(itemId));
    }

    public Optional<Long> findQuantityForItem(int itemId) {
        return offerAdviceRepository.find()
                .flatMap(offerAdvice -> offerAdvice.quantityForItem(itemId));
    }
}

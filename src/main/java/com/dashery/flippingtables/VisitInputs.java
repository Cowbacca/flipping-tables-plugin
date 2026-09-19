package com.dashery.flippingtables;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

public final class VisitInputs {
    private VisitInputs() {
    }

    public static long wholeNumber(String value, String label) {
        String normalized = value.trim().replace(",", "");
        if (!normalized.matches("[0-9]+")) {
            throw new IllegalArgumentException(label + " must be a whole, non-negative number.");
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " is too large.");
        }
    }

    public static Duration visitInterval(String value) {
        try {
            long seconds = new BigDecimal(value.trim()).multiply(BigDecimal.valueOf(3600))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            if (seconds < 300 || seconds > 604800) {
                throw new IllegalArgumentException("Next visit must be between five minutes and seven days away.");
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException("Enter the hours until your next visit, for example 2.5.");
        }
    }
}

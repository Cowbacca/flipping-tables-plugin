package com.dashery.flippingtables;

import org.junit.Test;
import java.time.Duration;
import static org.junit.Assert.*;

public class VisitInputsTest {
    @Test
    public void preservesFractionalVisitHours() {
        assertEquals(Duration.ofMinutes(150), VisitInputs.visitInterval("2.5"));
        assertEquals(Duration.ofDays(7), VisitInputs.visitInterval("168"));
    }

    @Test
    public void rejectsInvalidTimesRatherThanSilentlyChangingThem() {
        for (String input : new String[]{"-1", "0", "0.01", "169", "NaN", "1.2.3", "99999999999999999999999"}) {
            assertThrows(input, IllegalArgumentException.class, () -> VisitInputs.visitInterval(input));
        }
    }

    @Test
    public void acceptsFormattedWholeNumbers() {
        assertEquals(1234567, VisitInputs.wholeNumber("1,234,567", "GP"));
        assertEquals(0, VisitInputs.wholeNumber("0", "GP"));
    }

    @Test
    public void rejectsNegativesDecimalsAndOverflow() {
        for (String input : new String[]{"-100", "1.5", "1 million", "", "99999999999999999999999999"}) {
            assertThrows(input, IllegalArgumentException.class, () -> VisitInputs.wholeNumber(input, "GP"));
        }
    }
}

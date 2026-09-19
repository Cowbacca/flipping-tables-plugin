package com.dashery.flippingtables;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class FlippingTablesPanelTest {
    @Test
    public void retainsSelectionsAndShowsErrorsBesidePlanAcrossRepeatedRequests() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                FlippingTablesPlugin plugin = mock(FlippingTablesPlugin.class);
                FlippingTablesPanel panel = new FlippingTablesPanel(plugin, new FlippingTablesConfig() {},
                        new PortfolioAdviceRepository());
                CapturedPortfolio captured = PortfolioRequestBuilderTest.portfolio();
                panel.displayPortfolio(captured);

                JTextField hours = field(panel, "hours", JTextField.class);
                JTextField cash = field(panel, "cash", JTextField.class);
                JPasswordField token = field(panel, "token", JPasswordField.class);
                JCheckBox consent = field(panel, "consent", JCheckBox.class);
                JButton calculate = field(panel, "calculate", JButton.class);
                JTextArea status = field(panel, "status", JTextArea.class);
                @SuppressWarnings("unchecked")
                java.util.List<Object> stockInputs = field(panel, "stockInputs", java.util.List.class);
                Object firstStock = stockInputs.get(0);
                JCheckBox include = field(firstStock, "include", JCheckBox.class);
                JTextField cost = field(firstStock, "cost", JTextField.class);

                token.setText("test-token");
                consent.setSelected(true);
                cash.setText("500");
                include.setSelected(true);
                cost.setText("100");
                calculate.doClick();

                hours.setText("8");
                calculate.doClick();

                ArgumentCaptor<Set<Long>> selected = ArgumentCaptor.forClass(Set.class);
                ArgumentCaptor<Map<Long, Long>> costs = ArgumentCaptor.forClass(Map.class);
                ArgumentCaptor<Duration> intervals = ArgumentCaptor.forClass(Duration.class);
                verify(plugin, times(2)).requestAdvice(org.mockito.ArgumentMatchers.eq(captured), selected.capture(),
                        costs.capture(), anyLong(), intervals.capture(), anyString());
                assertTrue(selected.getAllValues().get(1).contains(4151L));
                assertEquals(Long.valueOf(100L), costs.getAllValues().get(1).get(4151L));
                assertEquals(Duration.ofHours(8), intervals.getAllValues().get(1));

                panel.showError("Portfolio advice request failed (HTTP 400).");
                assertEquals("Portfolio advice request failed (HTTP 400).", status.getText());
                assertTrue(calculate.isEnabled());
                assertEquals(panel.getComponentZOrder(calculate) + 1, panel.getComponentZOrder(status));
                panel.shutdown();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static <T> T field(Object target, String name, Class<T> type) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}

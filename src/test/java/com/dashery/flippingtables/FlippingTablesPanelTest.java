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
import static org.junit.Assert.assertFalse;
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
    public void allowsAnActionableSellRepriceSuggestionToBeUsed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CapturedPortfolio captured = PortfolioRequestBuilderTest.portfolio();
            PortfolioModels.Action reprice = new PortfolioModels.Action("REPRICE", 4151, 2, 130, "slot-0");
            PortfolioModels.AdviceResponse response = new PortfolioModels.AdviceResponse(1,
                    new PortfolioModels.Advice(java.util.Collections.singletonList(reprice), 0, 0, 0, 0,
                            java.util.Collections.emptyList(), "EXACT"), "2026-09-20T07:00:00Z");
            PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
            repository.save(response, PortfolioRequestBuilder.create(captured, java.util.Collections.emptySet(),
                    java.util.Collections.emptyMap(), 1000, Duration.ofHours(4), 10).getSnapshot());
            FlippingTablesPanel panel = new FlippingTablesPanel(mock(FlippingTablesPlugin.class), new FlippingTablesConfig() {}, repository);
            panel.displayPortfolio(captured);
            panel.showAdvice(response, java.util.Collections.singletonMap(4151L, "Abyssal whip"));

            button(panel, "Use this suggestion").doClick();

            assertEquals("Selected suggestion. Open the matching GE offer, click its quantity or price entry, then Use suggested. Press Enter to accept the number.",
                    fieldUnchecked(panel, "status", JTextArea.class).getText());
            panel.shutdown();
        });
    }

    @Test
    public void keepsNeedsReviewNoticeVisibleThroughPortfolioRefreshProgressAndErrors() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CapturedPortfolio captured = PortfolioRequestBuilderTest.portfolio();
            PortfolioModels.Action action = new PortfolioModels.Action("CREATE_BUY", 1515, 10, 80, null);
            PortfolioModels.AdviceResponse response = new PortfolioModels.AdviceResponse(1,
                    new PortfolioModels.Advice(java.util.Collections.singletonList(action), 0, 0, 0, 0,
                            java.util.Collections.emptyList(), "EXACT"), "2026-09-20T07:00:00Z");
            PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
            repository.save(response, PortfolioRequestBuilder.create(captured, java.util.Collections.emptySet(),
                    java.util.Collections.emptyMap(), 1000, Duration.ofHours(4), 10).getSnapshot());
            FlippingTablesPanel panel = new FlippingTablesPanel(mock(FlippingTablesPlugin.class), new FlippingTablesConfig() {}, repository);
            panel.displayPortfolio(captured);
            panel.showAdvice(response, java.util.Collections.emptyMap());

            panel.displayPortfolio(captured);
            panel.setBusy(true);
            panel.showAdviceStatus("Portfolio updated. Review the retained advice and request a fresh plan when ready.");
            assertTrue(fieldUnchecked(panel, "calculate", JButton.class).isEnabled());
            assertTrue(textValues(fieldUnchecked(panel, "results", javax.swing.JPanel.class)).contains("Portfolio updated. Review the retained advice and request a fresh plan when ready."));
            panel.showPlanProgress();
            panel.showError("Portfolio advice request failed (HTTP 400).");
            assertTrue(textValues(fieldUnchecked(panel, "results", javax.swing.JPanel.class)).contains("Portfolio updated. Review the retained advice and request a fresh plan when ready."));
            panel.setBusy(true);
            panel.showPlanStale("The previous plan expired while a new plan is loading.");
            assertFalse(fieldUnchecked(panel, "calculate", JButton.class).isEnabled());
            assertEquals("Working...", fieldUnchecked(panel, "calculate", JButton.class).getText());
            panel.shutdown();
        });
    }

    @Test
    public void selectAllSendsEveryTradeableInventoryItem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FlippingTablesPlugin plugin = mock(FlippingTablesPlugin.class);
            FlippingTablesPanel panel = new FlippingTablesPanel(plugin, new FlippingTablesConfig() {},
                    new PortfolioAdviceRepository());
            CapturedPortfolio captured = PortfolioRequestBuilderTest.portfolio();
            panel.displayPortfolio(captured);
            fieldUnchecked(panel, "token", JPasswordField.class).setText("test-token");
            fieldUnchecked(panel, "consent", JCheckBox.class).setSelected(true);

            button(panel, "Select all").doClick();
            fieldUnchecked(panel, "calculate", JButton.class).doClick();
            button(panel, "Clear selection").doClick();
            fieldUnchecked(panel, "calculate", JButton.class).doClick();

            ArgumentCaptor<Set<Long>> selected = ArgumentCaptor.forClass(Set.class);
            verify(plugin, times(2)).requestAdvice(org.mockito.ArgumentMatchers.eq(captured), selected.capture(), anyMap(), anyLong(),
                    any(), any(), anyString());
            assertEquals(Set.of(4151L, 1515L), selected.getAllValues().get(0));
            assertTrue(selected.getAllValues().get(1).isEmpty());
            PortfolioModels.AdviceRequest request = PortfolioRequestBuilder.create(captured, selected.getAllValues().get(1),
                    java.util.Collections.emptyMap(), 1000, Duration.ofHours(4), 10);
            assertEquals(2, request.getSnapshot().getInventory().stream()
                    .filter(item -> item.getItemId() == 4151).findFirst().orElseThrow().getQuantity());
            panel.shutdown();
        });
    }

    @Test
    public void listedOnlyStockIsIncludedWithoutAnInventoryChoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FlippingTablesPlugin plugin = mock(FlippingTablesPlugin.class);
            FlippingTablesPanel panel = new FlippingTablesPanel(plugin, new FlippingTablesConfig() {},
                    new PortfolioAdviceRepository());
            CapturedPortfolio captured = new CapturedPortfolio(1000, java.util.Collections.emptyList(),
                    java.util.Collections.singletonList(new CapturedPortfolio.Stock(1337, "Listed item", 0, 5)),
                    java.util.Collections.emptyMap(), 8, true, "now", "fingerprint");
            panel.displayPortfolio(captured);
            assertFalse(buttonLabels(panel).contains("Select all"));
            assertFalse(checkBoxLabels(panel).contains("Sell inventory"));
            fieldUnchecked(panel, "token", JPasswordField.class).setText("test-token");
            fieldUnchecked(panel, "consent", JCheckBox.class).setSelected(true);
            fieldUnchecked(panel, "calculate", JButton.class).doClick();

            ArgumentCaptor<Set<Long>> selected = ArgumentCaptor.forClass(Set.class);
            verify(plugin).requestAdvice(org.mockito.ArgumentMatchers.eq(captured), selected.capture(), anyMap(), anyLong(),
                    any(), any(), anyString());
            assertTrue(selected.getValue().isEmpty());
            panel.shutdown();
        });
    }

    @Test
    public void preservesInventoryChoicesAndCostsWhenPortfolioIsReadAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FlippingTablesPanel panel = new FlippingTablesPanel(mock(FlippingTablesPlugin.class), new FlippingTablesConfig() {},
                    new PortfolioAdviceRepository());
            CapturedPortfolio captured = PortfolioRequestBuilderTest.portfolio();
            panel.displayPortfolio(captured);
            java.util.List<Object> inputs = fieldUnchecked(panel, "stockInputs", java.util.List.class);
            JCheckBox include = fieldUnchecked(inputs.get(0), "include", JCheckBox.class);
            JTextField cost = fieldUnchecked(inputs.get(0), "cost", JTextField.class);
            include.setSelected(true);
            cost.setText("100");

            panel.displayPortfolio(captured);
            inputs = fieldUnchecked(panel, "stockInputs", java.util.List.class);
            include = fieldUnchecked(inputs.get(0), "include", JCheckBox.class);
            cost = fieldUnchecked(inputs.get(0), "cost", JTextField.class);
            assertTrue(include.isSelected());
            assertEquals("100", cost.getText());
            panel.shutdown();
        });
    }

    @Test
    public void keepsRemainingSuggestionControlsVisibleAfterAnOfferIsPlaced() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
            PortfolioModels.Action first = new PortfolioModels.Action("CREATE_BUY", 4151, 2, 100, null);
            PortfolioModels.Action next = new PortfolioModels.Action("CREATE_BUY", 1515, 10, 50, null);
            PortfolioModels.AdviceResponse response = new PortfolioModels.AdviceResponse(1,
                    new PortfolioModels.Advice(java.util.Arrays.asList(first, next), 700, 0, 0, 0,
                            java.util.Collections.emptyList(), "EXACT"), "2026-09-20T07:00:00Z");
            repository.save(response, null);
            FlippingTablesPanel panel = new FlippingTablesPanel(mock(FlippingTablesPlugin.class),
                    new FlippingTablesConfig() {}, repository);
            panel.displayPortfolio(PortfolioRequestBuilderTest.portfolio());
            panel.showAdvice(response, java.util.Collections.emptyMap());
            repository.markCompleted(java.util.Collections.singletonList(first));
            panel.showPlanProgress();
            java.util.List<String> buttons = buttonLabels(panel);
            assertEquals(1, java.util.Collections.frequency(buttons, "Use this suggestion"));
            assertEquals(1, java.util.Collections.frequency(buttons, "Copy quantity"));
            assertEquals(1, java.util.Collections.frequency(buttons, "Copy price"));
            panel.shutdown();
        });
    }

    private static java.util.List<String> buttonLabels(java.awt.Container container) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JButton) {
                labels.add(((JButton) component).getText());
            }
            if (component instanceof java.awt.Container) {
                labels.addAll(buttonLabels((java.awt.Container) component));
            }
        }
        return labels;
    }

    private static java.util.List<String> checkBoxLabels(java.awt.Container container) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JCheckBox) {
                labels.add(((JCheckBox) component).getText());
            }
            if (component instanceof java.awt.Container) {
                labels.addAll(checkBoxLabels((java.awt.Container) component));
            }
        }
        return labels;
    }

    private static java.util.List<String> textValues(java.awt.Container container) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JTextArea) {
                values.add(((JTextArea) component).getText());
            }
            if (component instanceof java.awt.Container) {
                values.addAll(textValues((java.awt.Container) component));
            }
        }
        return values;
    }

    private static JButton button(java.awt.Container container, String label) {
        JButton button = findButton(container, label);
        if (button == null) {
            throw new AssertionError("Missing button: " + label);
        }
        return button;
    }

    private static JButton findButton(java.awt.Container container, String label) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JButton && label.equals(((JButton) component).getText())) {
                return (JButton) component;
            }
            if (component instanceof java.awt.Container) {
                JButton found = findButton((java.awt.Container) component, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T> T fieldUnchecked(Object target, String name, Class<T> type) {
        try {
            return field(target, name, type);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

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
                ArgumentCaptor<Duration> followingIntervals = ArgumentCaptor.forClass(Duration.class);
                verify(plugin, times(2)).requestAdvice(org.mockito.ArgumentMatchers.eq(captured), selected.capture(),
                        costs.capture(), anyLong(), intervals.capture(), followingIntervals.capture(), anyString());
                assertTrue(selected.getAllValues().get(1).contains(4151L));
                assertEquals(Long.valueOf(100L), costs.getAllValues().get(1).get(4151L));
                assertEquals(Duration.ofHours(8), intervals.getAllValues().get(1));
                assertEquals(Duration.ofHours(4), followingIntervals.getAllValues().get(1));

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

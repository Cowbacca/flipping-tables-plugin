/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2019, Bram91 <https://github.com/bram91>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.dashery.flippingtables;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlippingTablesPanel extends PluginPanel {
    private final FlippingTablesPlugin plugin;
    private final FlippingTablesConfig config;
    private final PortfolioAdviceRepository repository;
    private final JPasswordField token = new JPasswordField();
    private final JTextField hours = new JTextField();
    private final JTextField cash = new JTextField("0");
    private final JCheckBox consent = new JCheckBox("Send portfolio for advice");
    private final JButton read = new JButton("Read current portfolio");
    private final JButton calculate = new JButton("Plan next visit");
    private final JTextArea disclosure = text("");
    private final JTextArea status = text("Log in, collect finished offers, then read your portfolio.");
    private final JPanel stocks = column();
    private final JPanel results = column();
    private final List<StockInput> stockInputs = new ArrayList<>();
    private CapturedPortfolio captured;
    private boolean updating;

    @Inject
    public FlippingTablesPanel(FlippingTablesPlugin plugin, FlippingTablesConfig config, PortfolioAdviceRepository repository) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 8, 10, 8));
        add(text("Flipping Tables"));
        add(disclosure);
        add(field("API token (kept in memory)", token));
        hours.setText(Integer.toString(config.nextVisitHours()));
        add(field("Hours until next visit", hours));
        add(field("Cash budget (carried GP)", cash));
        add(consent);
        add(Box.createVerticalStrut(8));
        add(read);
        add(stocks);
        add(Box.createVerticalStrut(8));
        add(calculate);
        add(status);
        add(results);
        calculate.setEnabled(false);
        consent.setAlignmentX(Component.LEFT_ALIGNMENT);
        consent.setOpaque(false);
        consent.setForeground(Color.WHITE);
        read.setAlignmentX(Component.LEFT_ALIGNMENT);
        calculate.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateDisclosure();
        String environmentToken = System.getenv("FLIPPING_TABLES_API_TOKEN");
        if (environmentToken != null) {
            token.setText(environmentToken);
        }
        read.addActionListener(event -> plugin.readPortfolio());
        calculate.addActionListener(event -> submit());
        consent.addActionListener(event -> inputChanged());
        watch(hours);
        watch(cash);
        watch(token);
    }

    public void displayPortfolio(CapturedPortfolio value) {
        updating = true;
        captured = value;
        cash.setText(Long.toString(value.getWalletCoins()));
        stocks.removeAll();
        stockInputs.clear();
        stocks.add(text(value.getOpenOffers().size() + " open offers across " + value.getTotalSlots() + " slots. Listed stock is included automatically."));
        stocks.add(text("Select carried items you want to sell. Uncollected items and bank stock are excluded. Cost is optional; 0 means unknown."));
        for (CapturedPortfolio.Stock stock : value.getStock()) {
            JPanel card = column();
            card.setBorder(new EmptyBorder(8, 0, 8, 0));
            card.add(text(stock.getName() + " (" + stock.getItemId() + ")"));
            card.add(text("Carried: " + stock.getCarriedQuantity() + " | Listed: " + stock.getListedQuantity()));
            JCheckBox include = new JCheckBox("Use carried stock");
            include.setEnabled(stock.getCarriedQuantity() > 0);
            include.setAlignmentX(Component.LEFT_ALIGNMENT);
            include.setOpaque(false);
            include.setForeground(Color.WHITE);
            JTextField cost = new JTextField("0");
            card.add(include);
            card.add(field("Cost per item (GP)", cost));
            stocks.add(card);
            stockInputs.add(new StockInput(stock.getItemId(), include, cost));
            include.addActionListener(event -> inputChanged());
            watch(cost);
        }
        stocks.add(text("Buy limits include fills observed this session. Earlier or offline purchases may leave less allowance. Check limits in game."));
        status.setText("Portfolio ready. Review stock, budget and next visit before requesting advice.");
        setBusy(false);
        updating = false;
        refresh();
    }

    public void showAdvice(PortfolioModels.AdviceResponse response, Map<Long, String> names) {
        results.removeAll();
        PortfolioModels.Advice advice = response.getAdvice();
        status.setText("Advice ready. Market data through " + response.getMarketDataThrough() + ".");
        results.add(text("Estimated cash committed: " + advice.getProjectedCashCommitted() + " GP. Inventory value estimate: " + advice.getConservativeInventoryValue() + " GP. These are estimates, not realised profit."));
        results.add(text("Review cancellations first. After changing an offer, read your portfolio and plan again."));
        if ("SEARCH_LIMIT_REACHED".equals(advice.getSearchStatus())) {
            results.add(text("The planner reached its search limit; this is its best result so far."));
        }
        if (advice.getActions().isEmpty()) {
            results.add(text("No suitable actions found for this portfolio and current market data."));
        }
        for (PortfolioModels.Action action : advice.getActions()) {
            JPanel card = column();
            card.setBorder(new EmptyBorder(10, 0, 10, 0));
            String side = repository.sideOf(action);
            String actionLabel = action.getType().startsWith("CREATE_") ? "New " + side.toLowerCase(java.util.Locale.ROOT)
                    : action.getType().toLowerCase(java.util.Locale.ROOT) + " " + side.toLowerCase(java.util.Locale.ROOT);
            card.add(text(actionLabel + " - " + names.getOrDefault(action.getItemId(), "Item " + action.getItemId())));
            card.add(text(action.getQuantity() + " at " + action.getPricePerItem() + " GP each" + slotLabel(action.getReplacesOfferId())));
            if (action.getType().startsWith("CREATE_")) {
                JButton choose = new JButton("Use this suggestion");
                choose.addActionListener(event -> {
                    try {
                        repository.select(action);
                        status.setText("Selected suggestion. Open the matching GE offer, then click its price or quantity helper. Search ft for suggested buys.");
                    } catch (RuntimeException error) {
                        showError(error.getMessage());
                    }
                });
                choose.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(choose);
            }
            if (!"KEEP".equals(action.getType()) && !"CANCEL".equals(action.getType())) {
                JButton copyPrice = new JButton("Copy price");
                copyPrice.addActionListener(event -> copy(Long.toString(action.getPricePerItem())));
                copyPrice.setAlignmentX(Component.LEFT_ALIGNMENT);
                card.add(copyPrice);
            }
            results.add(card);
        }
        for (String limitation : advice.getLimitations()) {
            results.add(text(limitation));
        }
        setBusy(false);
        refresh();
    }

    public void invalidateAdvice(String message, boolean clearPortfolio) {
        results.removeAll();
        if (clearPortfolio) {
            captured = null;
            stocks.removeAll();
            stockInputs.clear();
        }
        if (message != null) {
            status.setText(message);
        }
        setBusy(false);
        refresh();
    }

    public void showError(String message) {
        status.setText(message == null ? "Unable to get advice. Try reading your portfolio again." : message);
        setBusy(false);
        refresh();
    }

    public void setBusy(boolean busy) {
        read.setEnabled(!busy);
        calculate.setEnabled(!busy && captured != null);
        calculate.setText(busy ? "Working..." : "Plan next visit");
    }

    public void configurationChanged(boolean endpointChanged) {
        if (endpointChanged) {
            token.setText("");
            consent.setSelected(false);
        }
        updateDisclosure();
    }

    public void shutdown() {
        token.setText("");
        consent.setSelected(false);
        captured = null;
        stockInputs.clear();
        results.removeAll();
        stocks.removeAll();
    }

    private void submit() {
        try {
            if (captured == null) {
                throw new IllegalArgumentException("Read your current portfolio first.");
            }
            if (!consent.isSelected()) {
                throw new IllegalArgumentException("Confirm that this portfolio may be sent to the API.");
            }
            String apiToken = new String(token.getPassword()).trim();
            if (apiToken.isEmpty()) {
                throw new IllegalArgumentException("Enter your API token.");
            }
            Set<Long> selected = new HashSet<>();
            Map<Long, Long> costs = new HashMap<>();
            for (StockInput input : stockInputs) {
                if (input.include.isSelected()) {
                    selected.add(input.itemId);
                }
                costs.put(input.itemId, VisitInputs.wholeNumber(input.cost.getText(), "Item cost"));
            }
            plugin.requestAdvice(captured, selected, costs, VisitInputs.wholeNumber(cash.getText(), "Cash budget"),
                    VisitInputs.visitInterval(hours.getText()), apiToken);
        } catch (RuntimeException error) {
            showError(error.getMessage());
        }
    }

    private void inputChanged() {
        if (!updating) {
            plugin.inputsChanged();
        }
    }

    private void watch(JTextField input) {
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { inputChanged(); }
            public void removeUpdate(DocumentEvent event) { inputChanged(); }
            public void changedUpdate(DocumentEvent event) { inputChanged(); }
        });
    }

    private void updateDisclosure() {
        disclosure.setText("On request, send your cash budget, selected stock, open offers and observed limits to:\n" + config.apiBaseUrl() + "\nRuneScape login details and account names are not sent. The API token authenticates the request.");
    }

    private static JPanel column() {
        JPanel panel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return panel;
    }

    private static JTextArea text(String value) {
        JTextArea area = new JTextArea(value) {
            @Override
            public Dimension getPreferredSize() {
                int availableWidth = getParent() == null || getParent().getWidth() <= 0 ? 209
                        : getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right;
                setSize(Math.max(100, availableWidth), Short.MAX_VALUE);
                Dimension preferred = super.getPreferredSize();
                return new Dimension(Math.max(100, availableWidth), preferred.height);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setBackground(ColorScheme.DARK_GRAY_COLOR);
        area.setForeground(Color.WHITE);
        area.setBorder(new EmptyBorder(6, 0, 6, 0));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private static JPanel field(String label, JTextField input) {
        JPanel panel = column();
        JLabel title = new JLabel(label);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setForeground(Color.WHITE);
        panel.add(title);
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        input.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        input.setForeground(Color.WHITE);
        input.setCaretColor(Color.WHITE);
        panel.add(input);
        panel.setBorder(new EmptyBorder(5, 0, 5, 0));
        return panel;
    }

    private static void copy(String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
    }

    private void refresh() {
        revalidate();
        repaint();
    }

    private static String slotLabel(String offerId) {
        if (offerId == null) {
            return "";
        }
        if (offerId.matches("slot-[0-7]")) {
            return " (GE slot " + (Integer.parseInt(offerId.substring(5)) + 1) + ")";
        }
        return " (existing offer)";
    }

    private static class StockInput {
        private final long itemId;
        private final JCheckBox include;
        private final JTextField cost;

        private StockInput(long itemId, JCheckBox include, JTextField cost) {
            this.itemId = itemId;
            this.include = include;
            this.cost = cost;
        }
    }
}

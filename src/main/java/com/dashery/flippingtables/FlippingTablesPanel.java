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

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class FlippingTablesPanel extends PluginPanel {

    private static final String CALCULATE_ADVICE_BUTTON_TEXT = "Calculate advice";
    private static final String LOADING_BUTTON_TEXT = "Loading...";

    private final FlippingTablesPlugin plugin;
    private final FlippingTablesConfig config;
    private final ClientThread clientThread;


    private final JButton calculateAdviceButton;

    private final JTextField buyWindowInput;
    private final JTextField sellWindowInput;
    private final JTextField moneyAvailableInput;
    private final JTextField slotsAvaialbleInput;


    @Inject
    public FlippingTablesPanel(FlippingTablesPlugin plugin, FlippingTablesConfig config, ClientThread clientThread) {
        this.plugin = plugin;
        this.config = config;
        this.clientThread = clientThread;

        setLayout(new GridLayout(2, 2, 7, 7));

        buyWindowInput = addComponent("Buy window", 4);
        sellWindowInput = addComponent("Sell window", 4);
        moneyAvailableInput = addComponent("Money available", 1_000_000);
        slotsAvaialbleInput = addComponent("Slots available", 8);

        calculateAdviceButton = new JButton();
        calculateAdviceButton.setText(CALCULATE_ADVICE_BUTTON_TEXT);
        calculateAdviceButton.addActionListener(e -> new Thread(this::setupOffers).start());
        add(calculateAdviceButton);
    }

    @Override
    public void onActivate() {
        super.onActivate();
    }

    public void shutdown() {

    }

    private JTextField addComponent(String label, int defaultValue) {
        final JPanel container = new JPanel();
        container.setLayout(new BorderLayout());

        final JLabel uiLabel = new JLabel(label);
        final FlatTextField uiInput = new FlatTextField();

        uiInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        uiInput.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        uiInput.setBorder(new EmptyBorder(5, 7, 5, 7));
        uiInput.setText(String.valueOf(defaultValue));

        uiLabel.setFont(FontManager.getRunescapeSmallFont());
        uiLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
        uiLabel.setForeground(Color.WHITE);

        container.add(uiLabel, BorderLayout.NORTH);
        container.add(uiInput, BorderLayout.CENTER);

        add(container);

        return uiInput.getTextField();
    }

    private void setupOffers() {
        showLoading();
        plugin.setupOffers(
                getInput(slotsAvaialbleInput),
                getInput(moneyAvailableInput),
                getInput(buyWindowInput),
                getInput(sellWindowInput)
        );
        hideLoading();
    }

    private void showLoading() {
        calculateAdviceButton.setText(LOADING_BUTTON_TEXT);
    }

    private void hideLoading() {
        calculateAdviceButton.setText(CALCULATE_ADVICE_BUTTON_TEXT);
    }

    private int getInput(JTextField field) {
        try {
            return Integer.parseInt(field.getText().replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

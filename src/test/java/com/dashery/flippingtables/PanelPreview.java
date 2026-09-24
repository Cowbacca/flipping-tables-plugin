package com.dashery.flippingtables;

import net.runelite.client.ui.ColorScheme;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import static org.mockito.Mockito.mock;

public final class PanelPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            try {
                PortfolioAdviceRepository repository = new PortfolioAdviceRepository();
                FlippingTablesPanel panel = new FlippingTablesPanel(mock(FlippingTablesPlugin.class),
                        new FlippingTablesConfig() {}, repository);
                render(panel, output.resolve("connection.png"));
                CapturedPortfolio captured = new CapturedPortfolio(1000,
                        Collections.singletonList(new PortfolioModels.OpenOffer("slot-0", 4151, "SELL", 120, 4, 2)),
                        Arrays.asList(
                                new CapturedPortfolio.Stock(4151, "Abyssal whip", 3, 2),
                                new CapturedPortfolio.Stock(1515, "Yew logs", 10, 0),
                                new CapturedPortfolio.Stock(1127, "Rune platebody", 0, 1)),
                        Collections.emptyMap(), 8, true, "2026-09-19T20:00:00Z", "preview");
                panel.displayPortfolio(captured);
                render(panel, output.resolve("portfolio.png"));
                for (Component component : panel.getComponents()) {
                    if (component instanceof JCheckBox && "Sell all carried items".equals(((JCheckBox) component).getText())) {
                        ((JCheckBox) component).doClick();
                    }
                }
                render(panel, output.resolve("selective-portfolio.png"));
                PortfolioModels.AdviceResponse response = new PortfolioModels.AdviceResponse(1,
                        new PortfolioModels.Advice(Arrays.asList(
                                new PortfolioModels.Action("KEEP", 4151, 2, 100, "slot-0"),
                                new PortfolioModels.Action("CREATE_BUY", 1515, 10, 80, null)),
                                800, 0, 0, 200, Collections.singletonList("Fill estimates do not include your queue position."), "EXACT"),
                        "2026-09-19T20:00:00Z");
                repository.save(response, PortfolioRequestBuilder.create(captured, Collections.emptySet(),
                        Collections.emptyMap(), 1000, java.time.Duration.ofHours(4), 10).getSnapshot());
                panel.showAdvice(response, java.util.Map.of(1515L, "Yew logs", 4151L, "Abyssal whip"));
                render(panel, output.resolve("advice.png"));
                PortfolioModels.AdviceResponse guidanceResponse = new PortfolioModels.AdviceResponse(2,
                        new PortfolioModels.Advice(Collections.singletonList(
                                new PortfolioModels.Action("CREATE_SELL", 12881, 4, 3556029, null)), Arrays.asList(
                                new PortfolioModels.InventoryGuidance(12881, 4, 0, "SELL",
                                        "Sell 4 using the requested selling-window evidence.",
                                        new PortfolioModels.SaleQuote(3556029, "PT1H", "2026-09-21T15:00:00Z", false, 1, 1)),
                                new PortfolioModels.InventoryGuidance(4151, 5, 2, "NO_QUOTE",
                                        "No usable high-side price evidence is available; the existing offer stays listed.", null),
                                new PortfolioModels.InventoryGuidance(31581, 3, 0, "NO_SLOT",
                                        "A quote is available, but no Grand Exchange slot is free.",
                                        new PortfolioModels.SaleQuote(1120254, "PT4H", "2026-09-21T15:00:00Z", true, 3, 0))),
                                0, 0, 0, 200, Collections.emptyList(), "EXACT"), "2026-09-19T20:00:00Z");
                panel.showAdvice(guidanceResponse, java.util.Map.of(31581L, "Toxic blowpipe", 12881L, "Ahrim's robetop", 4151L, "Abyssal whip"));
                render(panel, output.resolve("sale-guidance.png"));
                panel.displayPortfolio(captured);
                panel.showAdviceStatus("Portfolio updated. Review the retained advice and request a fresh plan when ready.");
                render(panel, output.resolve("needs-review.png"));
                java.lang.reflect.Field savedAt = PortfolioAdviceRepository.class.getDeclaredField("savedAt");
                savedAt.setAccessible(true);
                savedAt.set(repository, java.time.Instant.now().minus(java.time.Duration.ofMinutes(6)));
                panel.showPlanStale("Advice expired. It remains visible for reference; request a fresh plan before using suggestions.");
                render(panel, output.resolve("expired-reference.png"));
                panel.showError("Portfolio advice request failed (HTTP 400). Review your inputs and try again.");
                render(panel, output.resolve("error.png"));
                panel.shutdown();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }

    private static void render(FlippingTablesPanel panel, Path output) throws Exception {
        panel.setSize(new Dimension(225, 1600));
        layout(panel);
        int height = Math.max(700, panel.getPreferredSize().height);
        panel.setSize(225, height);
        layout(panel);
        BufferedImage image = new BufferedImage(225, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.DARK_GRAY_COLOR);
        graphics.fillRect(0, 0, 225, height);
        panel.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layout((Container) child);
            }
        }
    }
}

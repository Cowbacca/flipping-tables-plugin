package com.dashery.flippingtables;

import net.runelite.client.ui.ColorScheme;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
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
                CapturedPortfolio captured = PortfolioRequestBuilderTest.portfolio();
                panel.displayPortfolio(captured);
                render(panel, output.resolve("portfolio.png"));
                PortfolioModels.AdviceResponse response = new PortfolioModels.AdviceResponse(1,
                        new PortfolioModels.Advice(Arrays.asList(
                                new PortfolioModels.Action("KEEP", 4151, 2, 100, "slot-0"),
                                new PortfolioModels.Action("CREATE_BUY", 1515, 10, 80, null)),
                                800, 0, 0, 200, Collections.singletonList("Fill estimates do not include your queue position."), "EXACT"),
                        "2026-09-19T20:00:00Z");
                repository.save(response, PortfolioRequestBuilder.create(captured, Collections.emptySet(),
                        Collections.emptyMap(), 1000, java.time.Duration.ofHours(4), 10).getSnapshot());
                panel.showAdvice(response, Collections.singletonMap(1515L, "Yew logs"));
                render(panel, output.resolve("advice.png"));
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

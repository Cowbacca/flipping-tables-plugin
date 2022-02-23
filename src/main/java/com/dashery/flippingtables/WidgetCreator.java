package com.dashery.flippingtables;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.VarClientStr;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject}))
@Slf4j
public class WidgetCreator {
    private final Client client;
    private final ClientThread clientThread;

    public void createChildWidget(WidgetInfo parent, String text, JavaScriptCallback onClick) {
        log.info("Creating child widget with text {}", text);
        clientThread.invokeLater(() -> {
            Widget widget = client.getWidget(parent);
            Widget childWidget = widget.createChild(-1, WidgetType.TEXT);
            childWidget.setText(text);
            childWidget.setTextColor(0x800000);
            childWidget.setFontId(FontID.QUILL_8);
            childWidget.setAction(0, "Set price");
            childWidget.setHasListener(true);
            childWidget.setOnOpListener(onClick);
            childWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
            childWidget.setOriginalX(0);
            childWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            childWidget.setOriginalY(8);
            childWidget.setOriginalHeight(24);
            childWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
            childWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
            childWidget.setWidthMode(WidgetSizeMode.MINUS);
            childWidget.revalidate();
        });
    }
}

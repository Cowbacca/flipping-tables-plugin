package com.dashery.flippingtables;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class PluginInjectionTest {
    @Test
    public void createsPluginWithRuneLiteProvidedServices() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(mock(Client.class));
                bind(ClientThread.class).toInstance(mock(ClientThread.class));
                bind(ItemManager.class).toInstance(mock(ItemManager.class));
                bind(ClientToolbar.class).toInstance(mock(ClientToolbar.class));
                bind(OkHttpClient.class).toInstance(new OkHttpClient());
                bind(FlippingTablesConfig.class).toInstance(new FlippingTablesConfig() {});
            }
        });
        assertNotNull(injector.getInstance(FlippingTablesPlugin.class));
        assertNotNull(injector.getInstance(GeSearchButton.class));
        assertNotNull(injector.getInstance(WidgetCreator.class));
    }
}

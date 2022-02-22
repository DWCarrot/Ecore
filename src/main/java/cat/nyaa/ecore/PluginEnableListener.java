package cat.nyaa.ecore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

public class PluginEnableListener implements Listener {
    private final SpigotLoader pluginInstance;
    private boolean economyRegistered = false;

    public PluginEnableListener(SpigotLoader pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (!economyRegistered) {
            if (pluginInstance.setupEconomy()) {
                economyRegistered = true;
                pluginInstance.getLogger().info("Economy provider found.");
                if (!pluginInstance.setupEcoreProvider()) {
                    pluginInstance.getLogger().severe("Failed to setup ecore provider.");
                    pluginInstance.getPluginLoader().disablePlugin(pluginInstance);
                }
            }
        }
    }
}

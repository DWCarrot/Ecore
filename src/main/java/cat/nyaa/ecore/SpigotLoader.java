package cat.nyaa.ecore;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class SpigotLoader extends JavaPlugin {
    private final Logger logger = getLogger();
    private Economy economyProvided = null;
    @Override
    public void onEnable(){
        //initialize configuration
        var configFile = new File(getDataFolder(),"config.toml");
        if(getDataFolder().mkdir()){
            logger.info("Created data folder.");
        }

        var tomlWriter = new TomlWriter();
        try {
            if(configFile.createNewFile() || configFile.length() == 0){
                var defaultConfig = new Config();
                tomlWriter.write(defaultConfig,configFile);
                logger.info("Created config file.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            this.getServer().getPluginManager().disablePlugin(this);
        }

        var toml = new Toml();
        var config = toml.read(configFile).to(Config.class);
        logger.info("Config loaded.");

        if(!setupEconomy()){
            this.getServer().getPluginManager().disablePlugin(this);
            logger.severe("Vault or economy provider(implementation of vault api) not found, plugin disabled.");
        }

    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        // ';[cvfp[000000000000000000000000000000000000000'
        // By Companion Object -- The cat
        RegisteredServiceProvider<Economy> economyRegisteredServiceProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyRegisteredServiceProvider == null) {
            return false;
        }else{
            economyProvided = economyRegisteredServiceProvider.getProvider();
            return true;
        }
    }

}

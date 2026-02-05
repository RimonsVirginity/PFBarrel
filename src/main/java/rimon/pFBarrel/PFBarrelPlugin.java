package rimon.pFBarrel;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class PFBarrelPlugin extends JavaPlugin {

    private static PFBarrelPlugin instance;
    private static Economy econ = null;

    private ConfigManager configManager;
    private BarrelManager barrelManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.configManager = new ConfigManager(this);
        this.barrelManager = new BarrelManager(this);
        this.guiManager = new GUIManager(this);

        getCommand("pfbarrel").setExecutor(new PFBarrelCommand(this));
        getServer().getPluginManager().registerEvents(new BarrelListener(this), this);

        getLogger().info("PF Barrel Enabled (Config Driven)!");
    }

    @Override
    public void onDisable() {
        if (barrelManager != null) {
            barrelManager.saveAll();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public static PFBarrelPlugin getInstance() { return instance; }
    public static Economy getEconomy() { return econ; }
    public ConfigManager getConfigManager() { return configManager; }
    public BarrelManager getBarrelManager() { return barrelManager; }
    public GUIManager getGuiManager() { return guiManager; }
}

package rimon.pFBarrel;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final PFBarrelPlugin plugin;
    private File itemsFile;
    private FileConfiguration itemsConfig;

    public ConfigManager(PFBarrelPlugin plugin) {
        this.plugin = plugin;
        createItemsConfig();
    }

    public List<Material> getAllowedCrops() {
        List<Material> materials = new ArrayList<>();
        if (itemsConfig.contains("crops")) {
            for (String key : itemsConfig.getConfigurationSection("crops").getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    materials.add(mat);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid material in items.yml: " + key);
                }
            }
        }
        return materials;
    }

    public double getPrice(Material mat) {
        return itemsConfig.getDouble("crops." + mat.name(), 0.0);
    }

    public boolean isAllowedCrop(Material mat) {
        return itemsConfig.contains("crops." + mat.name());
    }

    private void createItemsConfig() {
        itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            itemsFile.getParentFile().mkdirs();
            plugin.saveResource("items.yml", false);
        }

        itemsConfig = new YamlConfiguration();
        try {
            itemsConfig.load(itemsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not load items.yml!");
            e.printStackTrace();
        }
    }

    public void reloadItemsConfig() {
        itemsFile = new File(plugin.getDataFolder(), "items.yml");
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }
}

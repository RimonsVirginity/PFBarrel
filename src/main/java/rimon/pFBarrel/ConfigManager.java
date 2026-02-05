package rimon.pFBarrel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final PFBarrelPlugin plugin;
    private File itemsFile;
    private FileConfiguration itemsConfig;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public ConfigManager(PFBarrelPlugin plugin) {
        this.plugin = plugin;
        createItemsConfig();
        plugin.saveDefaultConfig();
    }

    public Component getMessage(String path) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages." + path, "");
        return serializer.deserialize(prefix + msg);
    }

    public Component getMessageRaw(String msgWithPlaceholders) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return serializer.deserialize(prefix + msgWithPlaceholders);
    }

    public String getString(String path) { return plugin.getConfig().getString(path, ""); }
    public int getInt(String path) { return plugin.getConfig().getInt(path, 0); }
    public double getDouble(String path) { return plugin.getConfig().getDouble(path, 0.0); }
    public boolean getBoolean(String path) { return plugin.getConfig().getBoolean(path, false); }

    public List<Component> getLore(String path) {
        List<String> raw = plugin.getConfig().getStringList(path);
        List<Component> colored = new ArrayList<>();
        for (String s : raw) {
            colored.add(serializer.deserialize(s));
        }
        return colored;
    }

    public List<Material> getAllowedCrops() {
        List<Material> materials = new ArrayList<>();
        if (itemsConfig.contains("crops")) {
            for (String key : itemsConfig.getConfigurationSection("crops").getKeys(false)) {
                try {
                    materials.add(Material.valueOf(key.toUpperCase()));
                } catch (IllegalArgumentException e) {
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
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    public void reload() {
        plugin.reloadConfig();
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }
}

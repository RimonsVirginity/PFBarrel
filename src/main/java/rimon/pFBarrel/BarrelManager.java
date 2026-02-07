package rimon.pFBarrel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BarrelManager {

    private final PFBarrelPlugin plugin;
    private final Map<String, BarrelData> barrels = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public BarrelManager(PFBarrelPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private String locToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location stringToLoc(String str) {
        String[] parts = str.split(",");
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return null;
        return new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    public BarrelData getBarrel(Location loc) {
        return barrels.get(locToString(loc));
    }

    public void createBarrel(Location loc) {
        String key = locToString(loc);
        if (!barrels.containsKey(key)) {
            barrels.put(key, new BarrelData(loc));
        }
    }

    public void removeBarrel(Location loc) {
        barrels.remove(locToString(loc));
    }

    public void saveAll() {
        dataFile = new File(plugin.getDataFolder(), "barreldata.yml");
        dataConfig = new YamlConfiguration();

        for (Map.Entry<String, BarrelData> entry : barrels.entrySet()) {
            String locKey = entry.getKey();
            BarrelData data = entry.getValue();

            ConfigurationSection section = dataConfig.createSection("barrels." + locKey);

            section.set("booster_level", data.getSellBoosterLevel());
            section.set("amount_level", data.getSellAmountLevel());

            for (String cropKey : plugin.getConfigManager().getAllowedCropKeys()) {
                long amount = data.getAmount(cropKey);
                if (amount > 0) {
                    section.set("items." + cropKey, amount);
                }
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save barreldata.yml!");
            e.printStackTrace();
        }
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "barreldata.yml");
        if (!dataFile.exists()) return;

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection barrelsSection = dataConfig.getConfigurationSection("barrels");
        if (barrelsSection == null) return;

        for (String key : barrelsSection.getKeys(false)) {
            Location loc = stringToLoc(key);
            if (loc == null) continue;

            BarrelData data = new BarrelData(loc);
            ConfigurationSection section = barrelsSection.getConfigurationSection(key);

            int booster = section.getInt("booster_level", 0);
            int amount = section.getInt("amount_level", 0);
            data.setLevels(booster, amount);

            if (section.contains("items")) {
                ConfigurationSection itemsSec = section.getConfigurationSection("items");
                for (String cropKey : itemsSec.getKeys(false)) {
                    long count = itemsSec.getLong(cropKey);
                    data.addItem(cropKey, count);
                }
            }
            barrels.put(key, data);
        }
    }
}
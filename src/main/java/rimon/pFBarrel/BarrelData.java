package rimon.pFBarrel;

import org.bukkit.Location;
import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class BarrelData {

    private final Location location;
    private final Map<Material, Long> storedItems;
    private int sellBoosterLevel;
    private int sellAmountLevel;

    public BarrelData(Location loc) {
        this.location = loc;
        this.storedItems = new HashMap<>();
        this.sellBoosterLevel = 0;
        this.sellAmountLevel = 0;
    }

    public Location getLocation() { return location; }

    public void addItem(Material mat, long amount) {
        storedItems.put(mat, storedItems.getOrDefault(mat, 0L) + amount);
    }

    public void removeItem(Material mat, long amount) {
        long current = storedItems.getOrDefault(mat, 0L);
        if (current <= amount) {
            storedItems.remove(mat);
        } else {
            storedItems.put(mat, current - amount);
        }
    }

    public long getAmount(Material mat) {
        return storedItems.getOrDefault(mat, 0L);
    }

    public Map<Material, Long> getStoredItemsMap() {
        return storedItems;
    }

    public int getSellBoosterLevel() { return sellBoosterLevel; }
    public void upgradeSellBooster() { this.sellBoosterLevel++; }

    public int getSellAmountLevel() { return sellAmountLevel; }
    public void upgradeSellAmount() { this.sellAmountLevel++; }

    public void setLevels(int booster, int amount) {
        this.sellBoosterLevel = booster;
        this.sellAmountLevel = amount;
    }


    public double getSellMultiplier() {
        double percentPerLevel = PFBarrelPlugin.getInstance().getConfigManager().getDouble("upgrades.sell-booster.percent-per-level");
        return 1.0 + ((sellBoosterLevel * percentPerLevel) / 100.0);
    }

    public long getItemsSoldPerShard() {
        ConfigManager cfg = PFBarrelPlugin.getInstance().getConfigManager();
        long base = cfg.getInt("upgrades.sell-amount.base-amount");
        long inc = cfg.getInt("upgrades.sell-amount.amount-per-level");
        return base + (sellAmountLevel * inc);
    }
}
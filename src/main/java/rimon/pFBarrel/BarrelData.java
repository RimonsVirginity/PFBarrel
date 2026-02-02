package rimon.pFBarrel;

import org.bukkit.Location;
import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class BarrelData {
    private final Location location;
    private Map<Material, Long> storedItems;
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
    public void setLevels(int sellBoosterLevel, int sellAmountLevel) {
        this.sellBoosterLevel = sellBoosterLevel;
        this.sellAmountLevel = sellAmountLevel;
    }
    public Map<Material, Long> getStoredItemsMap() { return storedItems; }

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

    public int getSellBoosterLevel() { return sellBoosterLevel; }
    public void upgradeSellBooster() { this.sellBoosterLevel++; }
    public int getSellAmountLevel() { return sellAmountLevel; }
    public void upgradeSellAmount() { this.sellAmountLevel++; }

    public double getSellMultiplier() {
        return 1.0 + (sellBoosterLevel * 0.02);
    }

    public long getItemsSoldPerShard() {
        return 3456L * (sellAmountLevel + 1);
    }
}
package rimon.pFBarrel;

import org.bukkit.Location;
import org.bukkit.Material;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

public class BarrelData {

    private final Location location;
    private final Map<String, Long> storedItems;
    private int sellBoosterLevel;
    private int sellAmountLevel;

    public BarrelData(Location loc) {
        this.location = loc;
        this.storedItems = new HashMap<>();
        this.sellBoosterLevel = 0;
        this.sellAmountLevel = 0;
    }

    public Location getLocation() { return location; }

    public void addItem(String key, long amount) {
        storedItems.put(key, storedItems.getOrDefault(key, 0L) + amount);
    }

    public void removeItem(String key, long amount) {
        long current = storedItems.getOrDefault(key, 0L);
        if (current <= amount) {
            storedItems.remove(key);
        } else {
            storedItems.put(key, current - amount);
        }
    }

    public long getAmount(String key) {
        return storedItems.getOrDefault(key, 0L);
    }

    public Map<String, Long> getStoredItemsMap() {
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

    public String serializeItems() {
        if (storedItems.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : storedItems.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(";");
        }
        return sb.toString();
    }

    public void loadFromSerialization(String data) {
        if (data == null || data.isEmpty()) return;
        try {
            String[] parts = data.split(";");
            for (String part : parts) {
                if (part.contains(":")) {
                    String[] split = part.split(":");

                    storedItems.put(split[0], Long.parseLong(split[1]));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<ItemStack> collectSellableItems(long maxUses) {
        List<ItemStack> soldBatch = new ArrayList<>();
        long remainingCap = getItemsSoldPerShard();

        for (String key : PFBarrelPlugin.getInstance().getConfigManager().getAllowedCropKeys()) {
            if (remainingCap <= 0) break;

            long stored = getAmount(key);
            if (stored > 0) {
                long take = Math.min(stored, remainingCap);
                take = Math.min(take, maxUses);

                if (take > 0) {
                    soldBatch.add(createItemFromKey(key, (int) take));
                    removeItem(key, take);
                    remainingCap -= take;
                }
            }
        }
        return soldBatch;
    }

    public ItemStack createItemFromKey(String key, int amount) {
        boolean isOrganic = key.endsWith("_ORGANIC");
        String matName = isOrganic ? key.replace("_ORGANIC", "") : key;
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.BARRIER;

        ItemStack item = new ItemStack(mat, amount);
        if (isOrganic) {
            ItemMeta meta = item.getItemMeta();
            String loreLine = PFBarrelPlugin.getInstance().getConfigManager().getString("settings.organic-lore");
            List<Component> lore = new ArrayList<>();
            lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(loreLine));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
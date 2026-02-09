package rimon.pFBarrel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GUIManager {

    private final PFBarrelPlugin plugin;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public GUIManager(PFBarrelPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player, BarrelData barrel) {
        ConfigManager cfg = plugin.getConfigManager();
        int size = cfg.getInt("menus.main.size");
        String title = cfg.getString("menus.main.title");

        Inventory inv = Bukkit.createInventory(null, size, serializer.deserialize(title));

        if (cfg.getBoolean("menus.main.fill-empty")) {
            fillInventory(inv, Material.valueOf(cfg.getString("menus.main.fill-material")));
        }

        refreshMainMenu(inv, barrel);
        player.openInventory(inv);
    }

    public void refreshMainMenu(Inventory inv, BarrelData barrel) {
        ConfigManager cfg = plugin.getConfigManager();
        int size = cfg.getInt("menus.main.size");
        int startSlot = cfg.getInt("menus.main.crop-slots-start");

        if (cfg.getBoolean("menus.main.fill-empty")) {
            ItemStack filler = new ItemStack(Material.valueOf(cfg.getString("menus.main.fill-material")));
            ItemMeta meta = filler.getItemMeta();
            meta.displayName(Component.empty());
            filler.setItemMeta(meta);

            for (int i = startSlot; i < size; i++) {
                if (i == cfg.getInt("menus.main.upgrade-booster.slot") ||
                        i == cfg.getInt("menus.main.upgrade-amount.slot")) continue;
                inv.setItem(i, filler);
            }
        } else {
            for (int i = startSlot; i < size; i++) {
                if (i == cfg.getInt("menus.main.upgrade-booster.slot") ||
                        i == cfg.getInt("menus.main.upgrade-amount.slot")) continue;
                inv.setItem(i, null);
            }
        }

        int slot = startSlot;
        for (String key : cfg.getAllowedCropKeys()) {
            if (slot >= size) break;

            if (barrel.getAmount(key) <= 0) continue;

            if (slot == cfg.getInt("menus.main.upgrade-booster.slot") ||
                    slot == cfg.getInt("menus.main.upgrade-amount.slot")) {
                slot++;
            }

            if (slot >= size) break;

            inv.setItem(slot, createCropItem(barrel, key));
            slot++;
        }

        addUpgradeButton(inv, barrel, "upgrade-booster", "sell-booster");
        addUpgradeButton(inv, barrel, "upgrade-amount", "sell-amount");
    }

    public void openWithdrawMenu(Player player, BarrelData barrel, String key, int currentWithdraw) {
        ConfigManager cfg = plugin.getConfigManager();
        int size = cfg.getInt("menus.withdraw.size");
        String title = cfg.getString("menus.withdraw.title").replace("%item%", prettify(key.replace("_ORGANIC", "")));

        Inventory inv = Bukkit.createInventory(null, size, serializer.deserialize(title));

        if (cfg.getBoolean("menus.withdraw.fill-empty")) {
            fillInventory(inv, Material.valueOf(cfg.getString("menus.withdraw.fill-material")));
        }

        refreshWithdrawMenu(inv, player, barrel, key, currentWithdraw);

        player.openInventory(inv);
    }

    public void refreshWithdrawMenu(Inventory inv, Player player, BarrelData barrel, String key, int currentWithdraw) {
        ConfigManager cfg = plugin.getConfigManager();

        long totalStored = barrel.getAmount(key);
        ItemStack infoItem = barrel.createItemFromKey(key, 1);
        ItemMeta meta = infoItem.getItemMeta();
        meta.displayName(Component.text("Stats", NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.lore());
        lore.add(Component.text("Stored: " + String.format("%,d", totalStored), NamedTextColor.GRAY));
        lore.add(Component.text("Withdraw: " + String.format("%,d", currentWithdraw), NamedTextColor.AQUA));
        meta.lore(lore);
        infoItem.setItemMeta(meta);
        inv.setItem(cfg.getInt("menus.withdraw.info-slot"), infoItem);

        ConfigurationSection buttons = plugin.getConfig().getConfigurationSection("menus.withdraw.buttons");
        if (buttons != null) {
            for (String slotStr : buttons.getKeys(false)) {
                int s = Integer.parseInt(slotStr);
                int amount = buttons.getInt(slotStr);
                Material mat = amount > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
                String name = (amount > 0 ? "+" : "") + amount;
                List<String> buttonLore = Collections.singletonList("&7Current Withdraw: &b" + String.format("%,d", currentWithdraw));
                inv.setItem(s, createButton(mat, name, buttonLore));
            }
        }

        int maxSpace = calculateSpace(player, barrel.createItemFromKey(key, 1));
        int maxPossible = (int) Math.min(maxSpace, totalStored);
        boolean isFull = (currentWithdraw >= maxPossible && maxPossible > 0);

        Material bucketType = isFull ? Material.WATER_BUCKET : Material.BUCKET;
        String bucketName = isFull ? "Inventory Full Selected" : "Fill Inventory";

        inv.setItem(cfg.getInt("menus.withdraw.fill-inv-slot"), createButton(bucketType, bucketName, null));
        inv.setItem(cfg.getInt("menus.withdraw.confirm-slot"), createButton(Material.CHEST_MINECART, "Confirm Withdraw", Collections.singletonList("&7Click to take items")));
        inv.setItem(cfg.getInt("menus.withdraw.back-slot"), createButton(Material.ARROW, "Back", null));
    }

    private void addUpgradeButton(Inventory inv, BarrelData barrel, String menuKey, String upgradeKey) {
        ConfigManager cfg = plugin.getConfigManager();
        String menuPath = "menus.main." + menuKey + ".";
        String logicPath = "upgrades." + upgradeKey + ".";

        if (!cfg.getBoolean(logicPath + "enabled")) {
            return;
        }

        int level = (upgradeKey.equals("sell-booster")) ? barrel.getSellBoosterLevel() : barrel.getSellAmountLevel();
        int maxLevel = cfg.getInt(logicPath + "max-level");
        boolean isMaxed = level >= maxLevel;

        double currentPercent = level * cfg.getDouble(logicPath + "percent-per-level");
        long baseAmt = cfg.getInt(logicPath + "base-amount");
        long incAmt = cfg.getInt(logicPath + "amount-per-level");
        long currentAmt = baseAmt + (level * incAmt);

        double cost = 0;
        double nextPercent = 0;
        long nextAmt = 0;

        if (!isMaxed) {
            double baseCost = cfg.getDouble(logicPath + "start-cost");
            double multiplier = cfg.getDouble(logicPath + "cost-multiplier");
            cost = baseCost * Math.pow(multiplier, level);
            nextPercent = (level + 1) * cfg.getDouble(logicPath + "percent-per-level");
            nextAmt = baseAmt + ((level + 1) * incAmt);
        }

        ItemStack item = new ItemStack(Material.valueOf(cfg.getString(menuPath + "material")));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(serializer.deserialize(cfg.getString(menuPath + "name")));

        List<String> rawLore = plugin.getConfig().getStringList(menuPath + "lore");
        List<Component> lore = new ArrayList<>();

        for (String line : rawLore) {
            if (isMaxed) {
                if (line.toLowerCase().contains("next") || line.toLowerCase().contains("cost")) {
                    continue;
                }
                if (line.contains("%level%")) {
                    line = line.replace("%level%", String.valueOf(level) + " &c(MAXED)");
                }
            }

            String p = line.replace("%level%", String.valueOf(level))
                    .replace("%cost%", String.format("%,.0f", cost))
                    .replace("%percent%", String.valueOf(currentPercent))
                    .replace("%next_percent%", String.valueOf(nextPercent))
                    .replace("%amount%", String.valueOf(currentAmt))
                    .replace("%next_amount%", String.valueOf(nextAmt));

            lore.add(serializer.deserialize(p));
        }

        if (isMaxed) {
            lore.add(serializer.deserialize("&c&lMAX LEVEL REACHED"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);

        inv.setItem(cfg.getInt(menuPath + "slot"), item);
    }

    private ItemStack createCropItem(BarrelData barrel, String key) {
        ItemStack item = barrel.createItemFromKey(key, 1);
        ItemMeta meta = item.getItemMeta();

        String displayName = key.replace("_ORGANIC", "");
        meta.displayName(Component.text(prettify(displayName), NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.lore());
            lore.add(Component.empty());
        }
        lore.add(Component.text("Stored: " + String.format("%,d", barrel.getAmount(key)), NamedTextColor.WHITE));
        lore.add(Component.text("Right-click to withdraw", NamedTextColor.YELLOW));
        meta.lore(lore);
        org.bukkit.NamespacedKey nsk = new org.bukkit.NamespacedKey(plugin, "gui_crop_key");
        meta.getPersistentDataContainer().set(nsk, org.bukkit.persistence.PersistentDataType.STRING, key);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        if (loreLines != null) {
            List<Component> lore = new ArrayList<>();
            for(String s : loreLines) lore.add(serializer.deserialize(s));
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void fillInventory(Inventory inv, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) inv.setItem(i, item);
        }
    }

    private int calculateSpace(Player player, ItemStack prototype) {
        int space = 0;
        Material mat = prototype.getType();
        int maxStack = mat.getMaxStackSize();

        for (ItemStack i : player.getInventory().getStorageContents()) {
            if (i == null || i.getType() == Material.AIR) {
                space += maxStack;
            } else if (i.isSimilar(prototype)) {
                space += (maxStack - i.getAmount());
            }
        }
        return space;
    }

    private String prettify(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase().replace("_", " ");
    }
}
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

        int slot = cfg.getInt("menus.main.crop-slots-start");
        for (Material mat : cfg.getAllowedCrops()) {
            if (slot >= size) break;
            inv.setItem(slot, createCropItem(barrel, mat));
            slot++;
        }

        addUpgradeButton(inv, barrel, "upgrade-booster", "sell-booster");
        addUpgradeButton(inv, barrel, "upgrade-amount", "sell-amount");
    }

    public void openWithdrawMenu(Player player, BarrelData barrel, Material material, int currentWithdraw) {
        ConfigManager cfg = plugin.getConfigManager();
        int size = cfg.getInt("menus.withdraw.size");
        String title = cfg.getString("menus.withdraw.title").replace("%item%", prettify(material.name()));

        Inventory inv = Bukkit.createInventory(null, size, serializer.deserialize(title));

        if (cfg.getBoolean("menus.withdraw.fill-empty")) {
            fillInventory(inv, Material.valueOf(cfg.getString("menus.withdraw.fill-material")));
        }

        refreshWithdrawMenu(inv, player, barrel, material, currentWithdraw);

        player.openInventory(inv);
    }

    public void refreshWithdrawMenu(Inventory inv, Player player, BarrelData barrel, Material material, int currentWithdraw) {
        ConfigManager cfg = plugin.getConfigManager();

        long totalStored = barrel.getAmount(material);
        ItemStack infoItem = new ItemStack(material);
        ItemMeta meta = infoItem.getItemMeta();
        meta.displayName(Component.text("Stats", NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
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

        int maxSpace = calculateSpace(player, material);
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

        int level = (upgradeKey.equals("sell-booster")) ? barrel.getSellBoosterLevel() : barrel.getSellAmountLevel();
        double baseCost = cfg.getDouble(logicPath + "start-cost");
        double multiplier = cfg.getDouble(logicPath + "cost-multiplier");
        double cost = baseCost * Math.pow(multiplier, level);
        double currentPercent = level * cfg.getDouble(logicPath + "percent-per-level");
        double nextPercent = (level + 1) * cfg.getDouble(logicPath + "percent-per-level");
        long baseAmt = cfg.getInt(logicPath + "base-amount");
        long incAmt = cfg.getInt(logicPath + "amount-per-level");
        long currentAmt = baseAmt + (level * incAmt);
        long nextAmt = baseAmt + ((level + 1) * incAmt);

        ItemStack item = new ItemStack(Material.valueOf(cfg.getString(menuPath + "material")));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(serializer.deserialize(cfg.getString(menuPath + "name")));

        List<String> rawLore = plugin.getConfig().getStringList(menuPath + "lore");
        List<Component> lore = new ArrayList<>();
        for (String line : rawLore) {
            String p = line.replace("%level%", String.valueOf(level))
                    .replace("%cost%", String.format("%,.0f", cost))
                    .replace("%percent%", String.valueOf(currentPercent))
                    .replace("%next_percent%", String.valueOf(nextPercent))
                    .replace("%amount%", String.valueOf(currentAmt))
                    .replace("%next_amount%", String.valueOf(nextAmt));
            lore.add(serializer.deserialize(p));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(cfg.getInt(menuPath + "slot"), item);
    }

    private ItemStack createCropItem(BarrelData barrel, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(prettify(mat.name()), NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Stored: " + String.format("%,d", barrel.getAmount(mat)), NamedTextColor.WHITE));
        lore.add(Component.text("Right-click to withdraw", NamedTextColor.YELLOW));
        meta.lore(lore);
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

    private int calculateSpace(Player player, Material mat) {
        int space = 0;
        for (ItemStack i : player.getInventory().getStorageContents()) {
            if (i == null || i.getType() == Material.AIR) {
                space += mat.getMaxStackSize();
            } else if (i.getType() == mat) {
                space += (mat.getMaxStackSize() - i.getAmount());
            }
        }
        return space;
    }

    private String prettify(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase().replace("_", " ");
    }
}
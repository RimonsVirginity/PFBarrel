package rimon.pFBarrel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GUIManager {

    private final PFBarrelPlugin plugin;

    public GUIManager(PFBarrelPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player, BarrelData barrel) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("PF Barrel Storage"));

        List<Material> allowedCrops = plugin.getConfigManager().getAllowedCrops();
        for (Material mat : allowedCrops) {
            long amount = barrel.getAmount(mat);
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(prettify(mat.name()), NamedTextColor.GOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Stored: " + String.format("%,d", amount), NamedTextColor.WHITE));
            lore.add(Component.text("Price: $" + plugin.getConfigManager().getPrice(mat), NamedTextColor.GREEN));
            lore.add(Component.empty());
            lore.add(Component.text("Right-Click to Withdraw", NamedTextColor.YELLOW));

            meta.lore(lore);
            item.setItemMeta(meta);
            inv.addItem(item);
        }

        double boosterCost = 30000000.0 * Math.pow(2, barrel.getSellBoosterLevel());
        ItemStack boosterItem = createButton(Material.EMERALD, "Upgrade Sell Booster",
                "Current Lvl: " + barrel.getSellBoosterLevel(),
                "Next: +" + ((barrel.getSellBoosterLevel() + 1) * 2) + "% Value",
                "Cost: $" + String.format("%,.0f", boosterCost));
        inv.setItem(48, boosterItem);

        double amountCost = 2000000.0 * Math.pow(2, barrel.getSellAmountLevel());
        long nextAmount = 3456L * (barrel.getSellAmountLevel() + 2);
        ItemStack amountItem = createButton(Material.HOPPER, "Upgrade Sell Amount",
                "Current Lvl: " + barrel.getSellAmountLevel(),
                "Next: " + String.format("%,d", nextAmount) + " items/shard",
                "Cost: $" + String.format("%,.0f", amountCost));
        inv.setItem(50, amountItem);

        player.openInventory(inv);
    }

    public void openWithdrawMenu(Player player, BarrelData barrel, Material material, int currentWithdraw) {
        Inventory inv = Bukkit.createInventory(null, 45, Component.text("Withdraw: " + prettify(material.name())));

        long totalStored = barrel.getAmount(material);
        ItemStack infoItem = new ItemStack(material);
        ItemMeta meta = infoItem.getItemMeta();
        meta.displayName(Component.text("Status", NamedTextColor.GOLD));
        meta.lore(Arrays.asList(
                Component.text("Total Stored: " + String.format("%,d", totalStored), NamedTextColor.GRAY),
                Component.text("Withdraw Amount: " + String.format("%,d", currentWithdraw), NamedTextColor.AQUA)
        ));
        infoItem.setItemMeta(meta);
        inv.setItem(22, infoItem);

        inv.setItem(10, createButton(Material.RED_STAINED_GLASS_PANE, "-64 Items", ""));
        inv.setItem(11, createButton(Material.RED_STAINED_GLASS_PANE, "-16 Items", ""));
        inv.setItem(12, createButton(Material.RED_STAINED_GLASS_PANE, "-1 Item", ""));
        inv.setItem(14, createButton(Material.LIME_STAINED_GLASS_PANE, "+1 Item", ""));
        inv.setItem(15, createButton(Material.LIME_STAINED_GLASS_PANE, "+16 Items", ""));
        inv.setItem(16, createButton(Material.LIME_STAINED_GLASS_PANE, "+64 Items", ""));
        inv.setItem(31, createButton(Material.BUCKET, "Fill Inventory", "Calculates max space available"));
        inv.setItem(40, createButton(Material.CHEST_MINECART, "Confirm Withdraw", "Click to take items"));
        inv.setItem(36, createButton(Material.ARROW, "Back", ""));

        player.openInventory(inv);
    }

    private ItemStack createButton(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        for (String s : loreLines) {
            lore.add(Component.text(s, NamedTextColor.GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String prettify(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase().replace("_", " ");
    }
}

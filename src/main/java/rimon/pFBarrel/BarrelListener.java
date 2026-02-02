package rimon.pFBarrel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BarrelListener implements Listener {

    private final PFBarrelPlugin plugin;
    private final Map<UUID, Integer> withdrawAmounts = new HashMap<>();
    private final Map<UUID, Material> selectedMaterial = new HashMap<>();
    private final Map<UUID, BarrelData> openBarrels = new HashMap<>();

    public BarrelListener(PFBarrelPlugin plugin) {
        this.plugin = plugin;
    }


    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        if (dest.getType() != InventoryType.BARREL) return;
        if (dest.getLocation() == null) return;
        BarrelData barrel = plugin.getBarrelManager().getBarrel(dest.getLocation());

        if (barrel == null) return;
        ItemStack movingItem = event.getItem();
        if (plugin.getConfigManager().isAllowedCrop(movingItem.getType())) {
            barrel.addItem(movingItem.getType(), movingItem.getAmount());
            event.setCancelled(true);
            event.getSource().removeItem(movingItem);

        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBarrelInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) return;
        BarrelData barrel = plugin.getBarrelManager().getBarrel(block.getLocation());
        if (barrel == null) return;
        Player player = event.getPlayer();

        if (player.isSneaking()) {
            ItemStack handItem = event.getItem();
            if (handItem != null && handItem.getType() == Material.HOPPER) {
                return;
            }
            return;
        }
        event.setCancelled(true);
        openBarrels.put(player.getUniqueId(), barrel);
        plugin.getGuiManager().openMainMenu(player, barrel);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Component title = event.getView().title();
        String titleStr = title.toString();

        if (event.getView().getTitle().contains("PF Barrel Storage")) {
            event.setCancelled(true);
            handleMainMenuClick(event, player);
        } else if (event.getView().getTitle().contains("Withdraw:")) {
            event.setCancelled(true);
            handleWithdrawMenuClick(event, player);
        }
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player) {
        BarrelData barrel = openBarrels.get(player.getUniqueId());
        if (barrel == null) { player.closeInventory(); return; }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        if (event.getSlot() == 48) { // Upgrade Booster
            processUpgrade(player, barrel, "Booster");
        } else if (event.getSlot() == 50) { // Upgrade Amount
            processUpgrade(player, barrel, "Amount");
        } else {
            Material type = clicked.getType();
            if (plugin.getConfigManager().isAllowedCrop(type)) {
                selectedMaterial.put(player.getUniqueId(), type);
                withdrawAmounts.put(player.getUniqueId(), 1); // Default 1
                plugin.getGuiManager().openWithdrawMenu(player, barrel, type, 1);
            }
        }
    }

    private void handleWithdrawMenuClick(InventoryClickEvent event, Player player) {
        BarrelData barrel = openBarrels.get(player.getUniqueId());
        Material mat = selectedMaterial.get(player.getUniqueId());
        int current = withdrawAmounts.getOrDefault(player.getUniqueId(), 1);
        long maxStored = barrel.getAmount(mat);

        switch (event.getSlot()) {
            case 10: current -= 64; break;
            case 11: current -= 16; break;
            case 12: current -= 1; break;
            case 14: current += 1; break;
            case 15: current += 16; break;
            case 16: current += 64; break;
            case 31:
                current = calculateSpace(player, mat, maxStored);
                break;
            case 36:
                plugin.getGuiManager().openMainMenu(player, barrel);
                return;
            case 40:
                executeWithdraw(player, barrel, mat, current);
                return;
        }
        if (current < 1) current = 1;
        if (current > maxStored) current = (int) maxStored;
        int invSpace = calculateSpace(player, mat, maxStored);
        if (current > invSpace) current = invSpace;

        withdrawAmounts.put(player.getUniqueId(), current);
        plugin.getGuiManager().openWithdrawMenu(player, barrel, mat, current);
    }

    private int calculateSpace(Player player, Material mat, long maxStored) {
        int space = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) {
                space += mat.getMaxStackSize();
            } else if (item.getType() == mat) {
                space += (mat.getMaxStackSize() - item.getAmount());
            }
        }
        return (int) Math.min(space, maxStored);
    }

    private void executeWithdraw(Player player, BarrelData barrel, Material mat, int amount) {
        if (amount <= 0) return;
        if (barrel.getAmount(mat) >= amount) {
            barrel.removeItem(mat, amount);
            player.getInventory().addItem(new ItemStack(mat, amount));
            player.sendMessage(Component.text("Withdrew " + amount + " " + mat.name(), NamedTextColor.GREEN));
            plugin.getGuiManager().openMainMenu(player, barrel);
        }
    }

    private void processUpgrade(Player player, BarrelData barrel, String type) {
        double cost = 0;
        if (type.equals("Booster")) {
            cost = 30000000.0 * Math.pow(2, barrel.getSellBoosterLevel());
        } else {
            cost = 2000000.0 * Math.pow(2, barrel.getSellAmountLevel());
        }

        if (PFBarrelPlugin.getEconomy().getBalance(player) >= cost) {
            PFBarrelPlugin.getEconomy().withdrawPlayer(player, cost);
            if (type.equals("Booster")) barrel.upgradeSellBooster();
            else barrel.upgradeSellAmount();

            player.sendMessage(Component.text("Upgrade successful!", NamedTextColor.GREEN));
            plugin.getGuiManager().openMainMenu(player, barrel);
        } else {
            player.sendMessage(Component.text("Insufficient funds!", NamedTextColor.RED));
        }
    }
    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.BARREL) return;
        if (!item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(PFBarrelCommand.BARREL_KEY, org.bukkit.persistence.PersistentDataType.BYTE)) {
            plugin.getBarrelManager().createBarrel(event.getBlock().getLocation());
            event.getPlayer().sendMessage(Component.text("Infinite Barrel Placed!", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BARREL) return;

        BarrelData data = plugin.getBarrelManager().getBarrel(event.getBlock().getLocation());
        if (data != null) {

            plugin.getBarrelManager().removeBarrel(event.getBlock().getLocation());
            event.getPlayer().sendMessage(Component.text("Infinite Barrel Removed.", NamedTextColor.RED));
        }
    }
}
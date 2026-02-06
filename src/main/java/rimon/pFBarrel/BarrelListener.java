package rimon.pFBarrel;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BarrelListener implements Listener {

    private final PFBarrelPlugin plugin;
    private final Map<UUID, Integer> withdrawAmounts = new HashMap<>();
    private final Map<UUID, Material> selectedMaterial = new HashMap<>();
    private final Map<UUID, BarrelData> openBarrels = new HashMap<>();
    private final NamespacedKey KEY_ITEMS = new NamespacedKey("pfbarrel", "stored_items");
    private final NamespacedKey KEY_LEVELS = new NamespacedKey("pfbarrel", "upgrade_levels");

    public BarrelListener(PFBarrelPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBarrelInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) return;

        BarrelData barrel = plugin.getBarrelManager().getBarrel(block.getLocation());
        if (barrel == null) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return;

        if (!canAccess(player, block)) {
            player.sendMessage(Component.text("You do not have permission to access this container.", NamedTextColor.RED));
            return;
        }

        event.setCancelled(true);
        openBarrels.put(player.getUniqueId(), barrel);
        playSound(player, "click");
        plugin.getGuiManager().openMainMenu(player, barrel);
    }

    private boolean canAccess(Player player, Block block) {
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) {
            return true;
        }
        return new GPHook().canAccess(player, block);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;

        openBarrels.remove(player.getUniqueId());
        withdrawAmounts.remove(player.getUniqueId());
        selectedMaterial.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openBarrels.containsKey(player.getUniqueId())) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openBarrels.containsKey(player.getUniqueId())) return;

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            event.setCancelled(true);
        } else if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        } else {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        int mainSize = plugin.getConfigManager().getInt("menus.main.size");

        if (top.getSize() == mainSize && !event.getView().getTitle().contains("Withdraw")) {
            handleMainMenuClick(event, player);
        } else {
            handleWithdrawMenuClick(event, player);
        }
    }

    private void handleMainMenuClick(InventoryClickEvent event, Player player) {
        BarrelData barrel = openBarrels.get(player.getUniqueId());
        ConfigManager cfg = plugin.getConfigManager();
        int slot = event.getSlot();

        if (slot == cfg.getInt("menus.main.upgrade-booster.slot")) {
            processUpgrade(player, barrel, "sell-booster");
        } else if (slot == cfg.getInt("menus.main.upgrade-amount.slot")) {
            processUpgrade(player, barrel, "sell-amount");
        } else {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && cfg.isAllowedCrop(clicked.getType())) {
                playSound(player, "click");
                selectedMaterial.put(player.getUniqueId(), clicked.getType());
                withdrawAmounts.put(player.getUniqueId(), 1);
                plugin.getGuiManager().openWithdrawMenu(player, barrel, clicked.getType(), 1);
            }
        }
    }

    private void handleWithdrawMenuClick(InventoryClickEvent event, Player player) {
        BarrelData barrel = openBarrels.get(player.getUniqueId());
        if (!selectedMaterial.containsKey(player.getUniqueId())) {
            plugin.getGuiManager().openMainMenu(player, barrel);
            return;
        }

        Material mat = selectedMaterial.get(player.getUniqueId());
        int current = withdrawAmounts.getOrDefault(player.getUniqueId(), 1);
        long maxStored = barrel.getAmount(mat);
        ConfigManager cfg = plugin.getConfigManager();
        int slot = event.getSlot();

        boolean updateMenu = false;
        ConfigurationSection btnSec = plugin.getConfig().getConfigurationSection("menus.withdraw.buttons");

        if (btnSec != null && btnSec.contains(String.valueOf(slot))) {
            int change = btnSec.getInt(String.valueOf(slot));
            current += change;
            updateMenu = true;
            playSound(player, "click");
        }
        else if (slot == cfg.getInt("menus.withdraw.back-slot")) {
            playSound(player, "click");
            plugin.getGuiManager().openMainMenu(player, barrel);
            return;
        }
        else if (slot == cfg.getInt("menus.withdraw.fill-inv-slot")) {
            int invSpace = calculateSpace(player, mat);
            int maxPossible = (int) Math.min(invSpace, maxStored);

            if (current == maxPossible && current > 0) {
                current = 1;
            } else {
                current = maxPossible;
            }

            updateMenu = true;
            playSound(player, "click");
        }
        else if (slot == cfg.getInt("menus.withdraw.confirm-slot")) {
            executeWithdraw(player, barrel, mat, current);
            return;
        }

        if (updateMenu) {
            if (current < 1) current = 1;
            if (current > maxStored) current = (int) maxStored;
            int invSpace = calculateSpace(player, mat);
            if (current > invSpace && invSpace > 0) current = invSpace;
            if (current == 0 && invSpace == 0) current = 1;

            withdrawAmounts.put(player.getUniqueId(), current);
            plugin.getGuiManager().openWithdrawMenu(player, barrel, mat, current);
        }
    }

    private void executeWithdraw(Player player, BarrelData barrel, Material mat, int amount) {
        if (barrel.getAmount(mat) <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("barrel-empty"));
            playSound(player, "error");
            return;
        }

        int space = calculateSpace(player, mat);
        if (space <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("inventory-full"));
            playSound(player, "error");
            return;
        }

        if (amount > space) amount = space;
        if (amount > barrel.getAmount(mat)) amount = (int) barrel.getAmount(mat);

        if (amount > 0) {
            barrel.removeItem(mat, amount);
            player.getInventory().addItem(new ItemStack(mat, amount));
            String msg = plugin.getConfig().getString("messages.withdraw-success")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%item%", mat.name());
            player.sendMessage(plugin.getConfigManager().getMessageRaw(msg));
            playSound(player, "success");
            plugin.getGuiManager().openMainMenu(player, barrel);
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

    private void processUpgrade(Player player, BarrelData barrel, String key) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "upgrades." + key + ".";
        int currentLevel = (key.equals("sell-booster")) ? barrel.getSellBoosterLevel() : barrel.getSellAmountLevel();
        double base = cfg.getDouble(path + "start-cost");
        double mult = cfg.getDouble(path + "cost-multiplier");
        double cost = base * Math.pow(mult, currentLevel);

        if (PFBarrelPlugin.getEconomy().getBalance(player) >= cost) {
            PFBarrelPlugin.getEconomy().withdrawPlayer(player, cost);
            if (key.equals("sell-booster")) barrel.upgradeSellBooster();
            else barrel.upgradeSellAmount();
            String msg = plugin.getConfig().getString("messages.upgrade-success").replace("%level%", String.valueOf(currentLevel + 1));
            player.sendMessage(plugin.getConfigManager().getMessageRaw(msg));
            playSound(player, "success");
            plugin.getGuiManager().openMainMenu(player, barrel);
        } else {
            String msg = plugin.getConfig().getString("messages.insufficient-funds").replace("%cost%", String.format("%,.0f", cost));
            player.sendMessage(plugin.getConfigManager().getMessageRaw(msg));
            playSound(player, "error");
        }
    }

    private void playSound(Player p, String type) {
        String soundName = plugin.getConfig().getString("sounds." + type);
        if (soundName != null) {
            try { p.playSound(p.getLocation(), Sound.valueOf(soundName), 1f, 1f); } catch (Exception e) {}
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.BARREL || !item.hasItemMeta()) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        if (pdc.has(PFBarrelCommand.BARREL_KEY, PersistentDataType.BYTE)) {
            plugin.getBarrelManager().createBarrel(event.getBlock().getLocation());
            BarrelData newBarrel = plugin.getBarrelManager().getBarrel(event.getBlock().getLocation());

            if (pdc.has(KEY_ITEMS, PersistentDataType.STRING)) {
                newBarrel.loadFromSerialization(pdc.get(KEY_ITEMS, PersistentDataType.STRING));
            }
            if (pdc.has(KEY_LEVELS, PersistentDataType.STRING)) {
                String[] lvls = pdc.get(KEY_LEVELS, PersistentDataType.STRING).split(":");
                newBarrel.setLevels(Integer.parseInt(lvls[0]), Integer.parseInt(lvls[1]));
            }

            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("barrel-placed"));
            playSound(event.getPlayer(), "success");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BARREL) return;
        BarrelData data = plugin.getBarrelManager().getBarrel(event.getBlock().getLocation());
        if (data != null) {
            if (!canAccess(event.getPlayer(), event.getBlock())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("You do not have permission to break this.", NamedTextColor.RED));
                return;
            }

            ItemStack drop = new ItemStack(Material.BARREL);
            ItemMeta meta = drop.getItemMeta();

            String name = plugin.getConfigManager().getString("barrel-block.name");
            meta.displayName(plugin.getConfigManager().getMessageRaw(name));

            List<Component> lore = plugin.getConfigManager().getLore("barrel-block.lore");
            lore.add(Component.empty());
            lore.add(Component.text("§7Contains saved items/upgrades"));
            meta.lore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PFBarrelCommand.BARREL_KEY, PersistentDataType.BYTE, (byte) 1);

            String levels = data.getSellBoosterLevel() + ":" + data.getSellAmountLevel();
            pdc.set(KEY_LEVELS, PersistentDataType.STRING, levels);

            String items = data.serializeItems();
            if (!items.isEmpty()) {
                pdc.set(KEY_ITEMS, PersistentDataType.STRING, items);
            }

            drop.setItemMeta(meta);

            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);

            plugin.getBarrelManager().removeBarrel(event.getBlock().getLocation());
            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("barrel-removed"));
        }
    }

    @EventHandler
    public void onInventoryMove(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (event.getDestination().getType() != org.bukkit.event.inventory.InventoryType.BARREL) return;
        org.bukkit.Location loc = event.getDestination().getLocation();
        if (loc == null) return;

        BarrelData barrel = plugin.getBarrelManager().getBarrel(loc);
        if (barrel == null) return;

        ItemStack item = event.getItem();
        if (!plugin.getConfigManager().isAllowedCrop(item.getType())) return;

        event.setCancelled(true);
        barrel.addItem(item.getType(), item.getAmount());

        final ItemStack itemToRemove = item.clone();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            event.getSource().removeItem(itemToRemove);
        });
    }
    public Map<UUID, BarrelData> getOpenBarrels() {
        return openBarrels;
    }

    public Map<UUID, Material> getSelectedMaterials() {
        return selectedMaterial;
    }

    public Map<UUID, Integer> getWithdrawAmounts() {
        return withdrawAmounts;
    }
}
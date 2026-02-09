package rimon.pFBarrel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BarrelListener implements Listener {

    private final PFBarrelPlugin plugin;
    private final Map<UUID, Integer> withdrawAmounts = new HashMap<>();
    private final Map<UUID, String> selectedCropKey = new HashMap<>();
    private final Map<UUID, BarrelData> openBarrels = new HashMap<>();
    private final Set<String> validKeysCache = new HashSet<>();

    private final NamespacedKey KEY_ITEMS;
    private final NamespacedKey KEY_LEVELS;
    private final NamespacedKey KEY_IS_BARREL;
    private final NamespacedKey KEY_GUI_CROP;

    public BarrelListener(PFBarrelPlugin plugin) {
        this.plugin = plugin;
        this.KEY_ITEMS = new NamespacedKey(plugin, "stored_items");
        this.KEY_LEVELS = new NamespacedKey(plugin, "upgrade_levels");
        this.KEY_IS_BARREL = new NamespacedKey(plugin, "is_infinite_barrel");
        this.KEY_GUI_CROP = new NamespacedKey(plugin, "gui_crop_key");
        refreshCache();
    }

    public void refreshCache() {
        validKeysCache.clear();
        validKeysCache.addAll(plugin.getConfigManager().getAllowedCropKeys());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBarrelInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BARREL) return;

        BarrelData barrel = plugin.getBarrelManager().getBarrel(block.getLocation());
        if (barrel == null) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return;
        event.setCancelled(true);

        if (!canAccess(player, block)) {
            player.sendMessage(Component.text("You do not have permission to access this container.", NamedTextColor.RED));
            return;
        }

        openBarrels.put(player.getUniqueId(), barrel);
        playSound(player, "click");
        plugin.getGuiManager().openMainMenu(player, barrel);
    }

    private boolean canAccess(Player player, Block block) {
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return true;
        return new GPHook().canAccess(player, block);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;

        openBarrels.remove(player.getUniqueId());
        withdrawAmounts.remove(player.getUniqueId());
        selectedCropKey.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (openBarrels.containsKey(player.getUniqueId())) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < topSize) {
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

        Inventory top = event.getView().getTopInventory();

        if (event.getClickedInventory() == top) {
            event.setCancelled(true);
        } else if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        } else {
            return;
        }

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
            if (clicked == null || !clicked.hasItemMeta()) return;

            PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();

            if (pdc.has(KEY_GUI_CROP, PersistentDataType.STRING)) {
                String key = pdc.get(KEY_GUI_CROP, PersistentDataType.STRING);
                playSound(player, "click");
                selectedCropKey.put(player.getUniqueId(), key);
                withdrawAmounts.put(player.getUniqueId(), 1);
                plugin.getGuiManager().openWithdrawMenu(player, barrel, key, 1);
            }
        }
    }

    private void handleWithdrawMenuClick(InventoryClickEvent event, Player player) {
        BarrelData barrel = openBarrels.get(player.getUniqueId());
        String key = selectedCropKey.get(player.getUniqueId());
        if (key == null) {
            plugin.getGuiManager().openMainMenu(player, barrel);
            return;
        }

        int current = withdrawAmounts.getOrDefault(player.getUniqueId(), 1);
        long maxStored = barrel.getAmount(key);
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
            int invSpace = calculateSpace(player, barrel.createItemFromKey(key, 1));
            int maxPossible = (int) Math.min(invSpace, maxStored);
            if (current == maxPossible && current > 0) current = 1; else current = maxPossible;
            updateMenu = true;
            playSound(player, "click");
        }
        else if (slot == cfg.getInt("menus.withdraw.confirm-slot")) {
            executeWithdraw(player, barrel, key, current);
            return;
        }

        if (updateMenu) {
            if (current < 1) current = 1;
            if (current > maxStored) current = (int) maxStored;
            int invSpace = calculateSpace(player, barrel.createItemFromKey(key, 1));
            if (current > invSpace && invSpace > 0) current = invSpace;
            if (current == 0 && invSpace == 0) current = 1;

            withdrawAmounts.put(player.getUniqueId(), current);
            plugin.getGuiManager().openWithdrawMenu(player, barrel, key, current);
        }
    }

    private void executeWithdraw(Player player, BarrelData barrel, String key, int amount) {
        if (barrel.getAmount(key) <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("barrel-empty"));
            playSound(player, "error");
            return;
        }

        ItemStack toGive = barrel.createItemFromKey(key, 1);
        int space = calculateSpace(player, toGive);
        if (space <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("inventory-full"));
            playSound(player, "error");
            return;
        }

        if (amount > space) amount = space;
        if (amount > barrel.getAmount(key)) amount = (int) barrel.getAmount(key);

        if (amount > 0) {
            barrel.removeItem(key, amount);
            toGive.setAmount(amount);
            player.getInventory().addItem(toGive);

            String displayName = toGive.getType().name();
            if (key.endsWith("_ORGANIC")) displayName = "Organic " + displayName;

            String msg = plugin.getConfig().getString("messages.withdraw-success")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%item%", displayName);
            player.sendMessage(plugin.getConfigManager().getMessageRaw(msg));
            playSound(player, "success");
            plugin.getGuiManager().openMainMenu(player, barrel);
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

    private String identifyKeyFromItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (!item.hasItemMeta()) {
            String key = item.getType().name();
            if (validKeysCache.contains(key)) return key;
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) return null;
        if (meta.hasEnchants()) return null;
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && lore.size() == 1) {
                String line = lore.get(0);
                if (line.toLowerCase().contains("organic produce")) {
                    String organicKey = item.getType().name() + "_ORGANIC";
                    if (validKeysCache.contains(organicKey)) return organicKey;
                }
            }
            return null;
        }
        String key = item.getType().name();
        if (validKeysCache.contains(key)) return key;
        return null;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.BARREL || !item.hasItemMeta()) return;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(KEY_IS_BARREL, PersistentDataType.BYTE)) {
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
            meta.displayName(plugin.getConfigManager().getMessageRaw(plugin.getConfigManager().getString("barrel-block.name")));
            meta.getPersistentDataContainer().set(new NamespacedKey("pfbarrel", "is_infinite_barrel"), PersistentDataType.BYTE, (byte) 1);

            String levels = data.getSellBoosterLevel() + ":" + data.getSellAmountLevel();
            meta.getPersistentDataContainer().set(KEY_LEVELS, PersistentDataType.STRING, levels);

            String items = data.serializeItems();
            if (!items.isEmpty()) meta.getPersistentDataContainer().set(KEY_ITEMS, PersistentDataType.STRING, items);

            drop.setItemMeta(meta);
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), drop);

            plugin.getBarrelManager().removeBarrel(event.getBlock().getLocation());
            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("barrel-removed"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event.getDestination().getType() != InventoryType.BARREL) return;
        if (event.getDestination().getLocation() == null) return;
        ItemStack item = event.getItem();
        String key = identifyKeyFromItem(item);
        BarrelData barrel = plugin.getBarrelManager().getBarrel(event.getDestination().getLocation());
        if (barrel == null) return;
        if (key == null) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getConfigManager().getBoolean("settings.block-enchanted-items")) {
            if (!item.getEnchantments().isEmpty()) {
                event.setCancelled(true);
                return;
            }
        }
        barrel.addItem(key, item.getAmount());
        event.setCancelled(true);
        event.getSource().removeItem(item);
        }


    private void processUpgrade(Player player, BarrelData barrel, String key) {
        ConfigManager cfg = plugin.getConfigManager();
        String path = "upgrades." + key + ".";
        if (!cfg.getBoolean(path + "enabled")) return;
        int currentLevel = (key.equals("sell-booster")) ? barrel.getSellBoosterLevel() : barrel.getSellAmountLevel();
        int maxLevel = cfg.getInt(path + "max-level");
        if (currentLevel >= maxLevel) {
            player.sendMessage(plugin.getConfigManager().getMessage("upgrade-maxed"));
            playSound(player, "error");
            return;
        }
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

    public Map<UUID, BarrelData> getOpenBarrels() { return openBarrels; }
    public Map<UUID, String> getSelectedCropKeys() { return selectedCropKey; }
    public Map<UUID, Integer> getWithdrawAmounts() { return withdrawAmounts; }
}
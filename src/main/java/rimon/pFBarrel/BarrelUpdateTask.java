package rimon.pFBarrel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BarrelUpdateTask extends BukkitRunnable {

    private final PFBarrelPlugin plugin;

    public BarrelUpdateTask(PFBarrelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        BarrelListener listener = plugin.getBarrelListener();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (listener.getOpenBarrels().containsKey(uuid)) {
                BarrelData barrel = listener.getOpenBarrels().get(uuid);
                Inventory top = player.getOpenInventory().getTopInventory();

                int mainSize = plugin.getConfigManager().getInt("menus.main.size");
                boolean isWithdraw = player.getOpenInventory().getTitle().contains("Withdraw");

                if (!isWithdraw && top.getSize() == mainSize) {
                    plugin.getGuiManager().refreshMainMenu(top, barrel);
                }
                else if (isWithdraw) {
                    String key = listener.getSelectedCropKeys().get(uuid);
                    Integer amount = listener.getWithdrawAmounts().get(uuid);

                    if (key != null && amount != null) {
                        plugin.getGuiManager().refreshWithdrawMenu(top, player, barrel, key, amount);
                    }
                }
            }
        }
    }
}

package rimon.pFBarrel;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import us.thezircon.play.autopickup.AutoPickup;

public class AutoPickupHook {

    private final AutoPickup autoPickupPlugin;

    public AutoPickupHook() {
        this.autoPickupPlugin = AutoPickup.getInstance();
    }

    public void handleDrop(Location loc, Player player, ItemStack item) {
        if (autoPickupPlugin == null || item == null) return;

        if (autoPickupPlugin.autopickup_list.contains(player)) {

            player.getInventory().addItem(item).forEach((index, remaining) -> {
                player.getWorld().dropItemNaturally(loc, remaining);
            });

        } else {
            player.getWorld().dropItemNaturally(loc, item);
        }
    }
}

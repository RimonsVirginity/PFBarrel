package rimon.pFBarrel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class PFBarrelSellCommand implements CommandExecutor {

    private final PFBarrelPlugin plugin;

    public PFBarrelSellCommand(PFBarrelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("pfbarrel.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        // 1. Get the block the player is looking at (up to 5 blocks away)
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.BARREL) {
            player.sendMessage(Component.text("You must be looking at a PF Barrel!", NamedTextColor.RED));
            return true;
        }

        // 2. Validate it is a PF Barrel
        BarrelData barrel = plugin.getBarrelManager().getBarrel(target.getLocation());
        if (barrel == null) {
            player.sendMessage(Component.text("This is not a registered Infinite Barrel.", NamedTextColor.RED));
            return true;
        }

        // 3. CALL THE METHOD (Collects & Removes items)
        List<ItemStack> soldItems = barrel.collectSellableItems();

        if (soldItems.isEmpty()) {
            player.sendMessage(Component.text("Barrel is empty or sell limit reached!", NamedTextColor.YELLOW));
            return true;
        }

        // 4. Send List to Chat
        player.sendMessage(Component.text("--- Sold Items Batch ---", NamedTextColor.GOLD));
        for (ItemStack item : soldItems) {
            player.sendMessage(Component.text("- " + item.getAmount() + "x " + item.getType().name(), NamedTextColor.GREEN));
        }
        player.sendMessage(Component.text("------------------------", NamedTextColor.GOLD));

        return true;
    }
}

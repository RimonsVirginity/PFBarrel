package rimon.pFBarrel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class PFBarrelCommand implements CommandExecutor {

    private final PFBarrelPlugin plugin;
    public static final NamespacedKey BARREL_KEY = new NamespacedKey("pfbarrel", "is_infinite_barrel");

    public PFBarrelCommand(PFBarrelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pfbarrel.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /pfbarrel <get|reload>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().reload();
            sender.sendMessage(plugin.getConfigManager().getMessage("reload-success"));
            return true;
        }

        if (args[0].equalsIgnoreCase("get")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Player only.", NamedTextColor.RED));
                return true;
            }

            ItemStack barrelItem = new ItemStack(Material.BARREL);
            ItemMeta meta = barrelItem.getItemMeta();

            String name = plugin.getConfigManager().getString("barrel-block.name");
            meta.displayName(plugin.getConfigManager().getMessageRaw(name));
            meta.lore(plugin.getConfigManager().getLore("barrel-block.lore"));

            meta.getPersistentDataContainer().set(BARREL_KEY, PersistentDataType.BYTE, (byte) 1);

            barrelItem.setItemMeta(meta);
            player.getInventory().addItem(barrelItem);
            player.sendMessage(plugin.getConfigManager().getMessage("receive-barrel"));
            return true;
        }

        return true;
    }
}

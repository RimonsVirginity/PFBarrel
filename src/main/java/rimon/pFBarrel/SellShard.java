package rimon.pFBarrel;

import org.bukkit.entity.Player;
import org.bukkit.Material;

//public class SellShard {
//
//    public void processSale(Player player, BarrelData barrel) {
//        long itemsPerShard = barrel.getItemsSoldPerShard();
//        double multiplier = barrel.getSellMultiplier();
//
//        double totalValue = 0;
//        long itemsRemainingToSell = itemsPerShard;
//
//        for (Material mat : PFBarrelPlugin.getInstance().getConfigManager().getAllowedCrops()) {
//            if (itemsRemainingToSell <= 0) break;
//
//            long stored = barrel.getAmount(mat);
//            if (stored > 0) {
//                long take = Math.min(stored, itemsRemainingToSell);
//                double price = PFBarrelPlugin.getInstance().getConfigManager().getPrice(mat);
//                barrel.removeItem(mat, take);
//                totalValue += (take * price * multiplier);
//
//                itemsRemainingToSell -= take;
//            }
//        }
//
//        if (totalValue > 0) {
//            PFBarrelPlugin.getEconomy().depositPlayer(player, totalValue);
//            player.sendMessage("§aSold items for $" + String.format("%,.2f", totalValue));
//        } else {
//            player.sendMessage("§cBarrel is empty!");
//        }
//    }
//}

package rimon.pFBarrel;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class GPHook {

    public boolean canAccess(Player player, Block block) {
        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(block.getLocation(), false, null);
            if (claim != null) {
                return claim.allowContainers(player) == null;
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }
}

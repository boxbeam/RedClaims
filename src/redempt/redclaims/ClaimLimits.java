package redempt.redclaims;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ClaimLimits {
	
	private static NamespacedKey key;
	private static int defaultClaimBlocks;
	
	public static void init(RedClaims plugin, int defaultClaimBlocks) {
		key = new NamespacedKey(plugin, "claimMax");
		ClaimLimits.defaultClaimBlocks = defaultClaimBlocks;
	}
	
	public static int getClaimLimit(Player player) {
		PersistentDataContainer pdc = player.getPersistentDataContainer();
		Integer num = pdc.get(key, PersistentDataType.INTEGER);
		return (num == null ? 0 : num) + defaultClaimBlocks;
	}
	
	public static int getRemainingClaimLimit(Player player) {
		int used = RedClaims.getInstance().getClaimStorage().getClaimedBlocks(player.getUniqueId());
		return getClaimLimit(player) - used;
	}
	
	public static void setClaimLimit(Player player, int claimLimit) {
		claimLimit -= defaultClaimBlocks;
		PersistentDataContainer pdc = player.getPersistentDataContainer();
		pdc.set(key, PersistentDataType.INTEGER, claimLimit);
	}
	
	public static void addClaimLimit(Player player, int claimLimit) {
		setClaimLimit(player, getClaimLimit(player) + claimLimit);
	}
	
}

package redempt.redclaims.claim;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import redempt.redclaims.RedClaims;

import java.util.UUID;

public class MiscProtections implements Listener {
	
	private RedClaims plugin;
	
	public MiscProtections(RedClaims plugin) {
		Bukkit.getPluginManager().registerEvents(this, plugin);
		this.plugin = plugin;
	}
	
	@EventHandler
	public void onSpawn(EntitySpawnEvent e) {
		if (e.getEntityType() != EntityType.FALLING_BLOCK) {
			return;
		}
		Claim claim = ClaimMap.getClaim(e.getLocation());
		if (claim != null) {
			e.getEntity().addScoreboardTag("claim=" + claim.getOwner().getUniqueId() + ":" + claim.getName());
		}
	}
	
	@EventHandler
	public void onChangeBlock(EntityChangeBlockEvent e) {
		if (e.getEntityType() != EntityType.FALLING_BLOCK) {
			return;
		}
		String tag = e.getEntity().getScoreboardTags().stream().filter(s -> s.startsWith("claim=")).findFirst().orElse(null);
		Claim in = ClaimMap.getClaim(e.getBlock().getLocation());
		if (tag == null) {
			if (in != null) {
				e.setCancelled(true);
			}
			return;
		}
		tag = tag.substring("claim=".length());
		String[] split = tag.split(":");
		UUID id = UUID.fromString(split[0]);
		Claim claim = plugin.getClaimStorage().getClaim(id, split[1]);
		if (!claim.equals(in)) {
			e.setCancelled(true);
		}
	}
	
}

package redempt.redclaims.claim;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import redempt.redclaims.RedClaims;
import redempt.redlib.commandmanager.Messages;

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
	
	@EventHandler
	public void onDamage(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof Monster) {
			return;
		}
		Claim claim = ClaimMap.getClaim(e.getEntity().getLocation());
		if (claim == null) {
			return;
		}
		Location loc = e.getEntity().getLocation();
		if ((e.getEntityType() == EntityType.PLAYER && !claim.flagApplies(loc, "pvp"))
				|| (e.getEntityType() != EntityType.PLAYER && !claim.flagApplies(loc, "animals"))) {
			return;
		}
		Entity damager = e.getDamager();
		Player player = null;
		if (damager instanceof Player) {
			player = (Player) damager;
		}
		if (damager instanceof Projectile) {
			Projectile proj = (Projectile) damager;
			if (proj.getShooter() instanceof Player) {
				player = (Player) proj.getShooter();
			}
		}
		if (player == null) {
			return;
		}
		e.setCancelled(true);
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		Claim to = ClaimMap.getClaim(e.getTo());
		Claim from = ClaimMap.getClaim(e.getFrom());
		if (to != null && to != from) {
			e.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR,
					new TextComponent(Messages.msg("enteringClaim").replace("%player%", to.getOwner().getName()).replace("%claim%", to.getName())));
			return;
		}
		if (to == null && from != null) {
			e.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(Messages.msg("exitingClaim")));
			return;
		}
	}
	
}

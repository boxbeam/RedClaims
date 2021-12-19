package redempt.redclaims.claim;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
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
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;
import redempt.redclaims.ClaimBypass;
import redempt.redclaims.RedClaims;
import redempt.redlib.commandmanager.Messages;
import redempt.redlib.region.CuboidRegion;

import java.util.UUID;
import java.util.stream.Stream;

public class MiscProtections implements Listener {

	private static boolean hasAtLeast(Claim claim, Player player, ClaimRank rank) {
		if (claim.getRank(player).getRank() >= rank.getRank()) {
			return true;
		}
		return ClaimBypass.hasBypass(player.getUniqueId());
	}
	
	private static Player getPlayer(Entity entity) {
		if (entity instanceof Player) {
			return (Player) entity;
		}
		if (!(entity instanceof Projectile)) {
			return null;
		}
		Projectile proj = (Projectile) entity;
		return proj.getShooter() instanceof Player ? (Player) proj.getShooter() : null;
	}
	
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
	public void onVehicleDamage(VehicleDestroyEvent e) {
		if (e.getAttacker() == null) {
			return;
		}
		Claim claim = ClaimMap.getClaim(e.getVehicle().getLocation());
		if (claim == null || !claim.flagApplies(e.getVehicle().getLocation(), "animals")) {
			return;
		}
		if (!(e.getAttacker() instanceof Player)) {
			e.setCancelled(true);
			return;
		}
		Player player = (Player) e.getAttacker();
		if (!hasAtLeast(claim, player, ClaimRank.MEMBER)) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onDamage(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof Monster && e.getEntity().getCustomName() == null) {
			return;
		}
		Claim claim = ClaimMap.getClaim(e.getEntity().getLocation());
		if (claim == null) {
			return;
		}
		Player player = getPlayer(e.getDamager());
		if (player != null && e.getEntity() instanceof Player) {
			if (claim.flagApplies(e.getEntity().getLocation(), "pvp")) {
				e.setCancelled(true);
				return;
			}
		}
		if (!claim.flagApplies(e.getEntity().getLocation(), "animals")) {
			return;
		}
		if (e.getDamager() instanceof Projectile) {
			ProjectileSource shooter = ((Projectile) e.getDamager()).getShooter();
			Claim shooterClaim = ClaimMap.getClaim(getLocation(shooter));
			if (canDamage(shooter, shooterClaim, e.getEntity().getLocation())) {
				return;
			}
		}
		if (player == null && !(e.getEntity() instanceof Player)) {
			e.setCancelled(true);
			return;
		}
		if (player != null && !hasAtLeast(claim, player, ClaimRank.MEMBER)) {
			e.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onPotionSplash(PotionSplashEvent e) {
		ProjectileSource shooter = e.getEntity().getShooter();
		Claim claim = ClaimMap.getClaim(getLocation(shooter));
		e.getAffectedEntities().forEach(en -> {
			if (en.equals(shooter)) return;
			if (!canDamage(shooter, claim, en.getLocation())) {
				e.setIntensity(en, 0);
			}
		});
	}
	
	@EventHandler
	public void onLingeringPotionSplash(LingeringPotionSplashEvent e) {
		ProjectileSource shooter = e.getEntity().getShooter();
		Claim claim = ClaimMap.getClaim(getLocation(shooter));
		AreaEffectCloud cloud = e.getAreaEffectCloud();
		double r = cloud.getRadius();
		Location min = cloud.getLocation().subtract(r, r, r);
		Location max = cloud.getLocation().add(r, r, r);
		Stream.of(new CuboidRegion(min, max).getCorners()).forEach(l -> {
			if (!canDamage(shooter, claim, l)) {
				e.setCancelled(true);
			}
		});
	}
	
	private Location getLocation(ProjectileSource source) {
		if (source instanceof BlockProjectileSource) {
			return ((BlockProjectileSource) source).getBlock().getLocation();
		}
		if (source instanceof Entity) {
			return ((Entity) source).getLocation();
		}
		return null;
	}
	
	private boolean canDamage(ProjectileSource source, Claim claim, Location loc) {
		Claim insideClaim = ClaimMap.getClaim(loc);
		if (insideClaim == null) {
			return true;
		}
		if (source instanceof Player) {
			return hasAtLeast(insideClaim, (Player) source, ClaimRank.MEMBER);
		}
		return insideClaim.equals(claim);
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

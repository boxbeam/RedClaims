package redempt.redclaims;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import redempt.redclaims.claim.Claim;
import redempt.redclaims.claim.ClaimMap;
import redempt.redlib.commandmanager.Messages;
import redempt.redlib.enchants.events.PlayerChangedHeldItemEvent;
import redempt.redlib.itemutils.ItemUtils;
import redempt.redlib.misc.LocationUtils;
import redempt.redlib.misc.Task;
import redempt.redlib.region.CuboidRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ClaimTool implements Listener {
	
	private ItemStack item;
	private Map<UUID, Location[]> selections = new HashMap<>();
	private RedClaims plugin;
	
	public ClaimTool(RedClaims plugin, ItemStack item) {
		this.item = item;
		Bukkit.getPluginManager().registerEvents(this, plugin);
		this.plugin = plugin;
	}
	
	@EventHandler
	public void onClick(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
			return;
		}
		ItemStack item = e.getItem();
		if (!ItemUtils.compare(item, this.item)) {
			return;
		}
		e.setCancelled(true);
		Location[] locs = selections.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new Location[2]);
		Location loc = e.getClickedBlock().getLocation();
		if (locs[0] != null && !locs[0].getWorld().equals(loc.getWorld())) {
			locs[0] = null;
		}
		if (locs[0] != null && locs[1] != null) {
			locs[0] = null;
			locs[1] = null;
		}
		if (locs[0] == null) {
			locs[0] = loc;
			e.getPlayer().sendMessage(Messages.msg("firstCornerSelected"));
			new ClaimHelper(e.getPlayer());
			return;
		}
		locs[1] = loc;
		e.getPlayer().sendMessage(Messages.msg("secondCornerSelected"));
	}
	
	public CuboidRegion getSelection(UUID id) {
		Location[] locs = selections.get(id);
		if (locs == null || locs[0] == null || locs[1] == null) {
			return null;
		}
		return new CuboidRegion(locs[0], locs[1]).expand(1, 0, 1, 0, 1, 0);
	}
	
	public void clearSelection(UUID id) {
		selections.remove(id);
	}
	
	@EventHandler
	public void onHotbarSwap(PlayerChangedHeldItemEvent e) {
		boolean cur = ItemUtils.compare(e.getNewItem(), item);
		boolean prev = ItemUtils.compare(e.getPreviousItem(), item);
		System.out.println(cur + " " + prev);
		if (!cur && !prev) {
			return;
		}
		Claim claim = ClaimMap.getClaim(e.getPlayer().getLocation());
		if (claim == null) {
			return;
		}
		if (cur) {
			claim.visualize(e.getPlayer(), false);
			return;
		}
		claim.unvisualize(e.getPlayer());
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		if (!ItemUtils.compare(item, e.getPlayer().getInventory().getItemInMainHand())) {
			return;
		}
		Claim to = ClaimMap.getClaim(e.getTo());
		Claim from = ClaimMap.getClaim(e.getFrom());
		if (Objects.equals(to, from)) {
			return;
		}
		if (to == null) {
			from.unvisualize(e.getPlayer());
			return;
		}
		to.visualize(e.getPlayer(), false);
	}
	
	@EventHandler
	public void onLeave(PlayerQuitEvent e) {
		selections.remove(e.getPlayer().getUniqueId());
	}
	
	private class ClaimHelper {
		
		private BlockDisplayer displayer;
		private Player player;
		private Task task;
		private int budget;
		
		public ClaimHelper(Player player) {
			displayer = new BlockDisplayer(player);
			this.player = player;
			task = Task.syncRepeating(plugin, this::tick, 1, 1);
			budget = ClaimLimits.getRemainingClaimLimit(player);
		}
		
		private void cancel() {
			task.cancel();
			displayer.clear();
		}
		
		private void tick() {
			if (!player.isOnline()) {
				cancel();
				return;
			}
			Location[] locs = selections.get(player.getUniqueId());
			if (locs == null || locs[0] == null || !locs[0].getWorld().equals(player.getWorld())) {
				cancel();
				return;
			}
			ItemStack item = player.getInventory().getItemInMainHand();
			if (!ItemUtils.compare(item, ClaimTool.this.item)) {
				displayer.clear();
				return;
			}
			Location loc = locs[1];
			if (loc == null) {
				Block block = player.getTargetBlockExact(4);
				if (block == null) {
					return;
				}
				loc = block.getLocation();
			}
			showSelection(locs[0], loc);
		}
		
		private void showSelection(Location loc1, Location loc2) {
			CuboidRegion region = new CuboidRegion(loc1, loc2);
			region.expand(1, 0, 1, 0, 1, 0);
			int[] dim = region.getBlockDimensions();
			int blocksClaimed = dim[0] * dim[2];
			if (budget < blocksClaimed) {
				showCorners(Messages.msg("claimTooLarge").replace("%blocks%", blocksClaimed + "").replace("%budget%", budget + ""), loc1, loc2, Material.REDSTONE_BLOCK);
				return;
			}
			Set<Claim> overlap = ClaimMap.getClaims(region);
			OverlapResult overlapResult = checkOverlap(overlap, loc1, loc2);
			if (overlapResult == OverlapResult.OVERLAPS) {
				showCorners(Messages.msg("claimOverlap"), loc1, loc2, Material.REDSTONE_BLOCK);
				return;
			}
			if (overlapResult == OverlapResult.CLAIM && (dim[0] < 10 || dim[2] < 10)) {
				showCorners(Messages.msg("claimTooSmall").replace("%dims%", dim[0] + "x" + dim[2]), loc1, loc2, Material.REDSTONE_BLOCK);
				return;
			}
			if (overlapResult == OverlapResult.SUBCLAIM && (dim[0] < 3 || dim[1] < 3 || dim[2] < 3)) {
				showCorners(Messages.msg("subclaimTooSmallTool").replace("%dims%", dim[0] + "x" + dim[1] + "x" + dim[2]), loc1, loc2, Material.REDSTONE_BLOCK);
				return;
			}
			if (overlapResult == OverlapResult.CLAIM) {
				showCorners(Messages.msg("claimToolInfo").replace("%dims%", dim[0] + "x" + dim[2]).replace("%blocks%", blocksClaimed + "").replace("%budget%", budget + ""), loc1, loc2, Material.EMERALD_BLOCK);
				return;
			}
			Claim claim = overlap.iterator().next();
			if (claim.getSubclaims().stream().anyMatch(c -> c.getRegion().overlaps(region))) {
				showCorners(Messages.msg("subclaimOverlaps"), loc1, loc2, Material.REDSTONE_BLOCK);
				return;
			}
			showCorners(Messages.msg("subclaimValid"), loc1, loc2, Material.EMERALD_BLOCK);
		}
		
		private OverlapResult checkOverlap(Set<Claim> claims, Location loc1, Location loc2) {
			if (claims.size() == 0) {
				return OverlapResult.CLAIM;
			}
			if (claims.size() > 1) {
				return OverlapResult.OVERLAPS;
			}
			Claim claim = claims.iterator().next();
			if (!claim.getOwner().equals(player)) {
				return OverlapResult.OVERLAPS;
			}
			CuboidRegion region = claim.getRegion();
			if (region.contains(loc1) && region.contains(loc2)) {
				return OverlapResult.SUBCLAIM;
			}
			return OverlapResult.OVERLAPS;
		}
		
		private final BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
		
		private void showCorners(String message, Location loc1, Location loc2, Material type) {
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
			List<Location> locs = new ArrayList<>();
			CuboidRegion region = new CuboidRegion(loc1, loc2).expand(1, 0, 1000, 1000, 1, 0);
			locs.add(new Location(loc1.getWorld(), loc1.getX(), 0, loc2.getZ()));
			locs.add(new Location(loc1.getWorld(), loc2.getX(), 0, loc1.getZ()));
			locs.forEach(l -> l.setY(l.getWorld().getHighestBlockYAt(l)));
			locs.add(loc1);
			locs.add(loc2);
			for (Location loc : locs) {
				displayer.display(loc.getBlock(), type);
				Block block = loc.getBlock();
				for (BlockFace face : faces) {
					Block rel = block.getRelative(face);
					if (!region.contains(LocationUtils.center(rel))) {
						continue;
					}
					rel = rel.getWorld().getHighestBlockAt(rel.getLocation());
					displayer.display(rel, Material.WAXED_OXIDIZED_CUT_COPPER);
				}
			}
			displayer.show();
		}
		
	}
	
	protected enum OverlapResult {
		
		OVERLAPS,
		CLAIM,
		SUBCLAIM
		
	}
	
}

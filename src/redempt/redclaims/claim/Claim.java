package redempt.redclaims.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redempt.redclaims.ClaimBypass;
import redempt.redclaims.ClaimFlag;
import redempt.redclaims.RedClaims;
import redempt.redlib.protection.ProtectedRegion;
import redempt.redlib.protection.ProtectionPolicy;
import redempt.redlib.protection.ProtectionPolicy.ProtectionType;
import redempt.redlib.region.CuboidRegion;
import redempt.redlib.region.Region;
import redempt.redlib.sql.SQLHelper;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class Claim {
	
	private static final ProtectionType[] DEFAULT_PROTECTIONS = {
			ProtectionType.WITHER,
			ProtectionType.SILVERFISH,
			ProtectionType.PISTONS_IN,
			ProtectionType.FLOW_IN,
			ProtectionType.STRUCTURE_GROWTH_IN,
			ProtectionType.PORTAL_PAIRING,
			ProtectionType.DISPENSER_PLACE_IN,
	};
	
	private ProtectedRegion region;
	private UUID owner;
	private String name;
	private Map<UUID, ClaimRank> members = new LinkedHashMap<>();
	private Set<ClaimFlag> flags = new LinkedHashSet<>();
	private List<Subclaim> subclaims = new ArrayList<>();
	protected SQLHelper sql;

	protected Claim(SQLHelper sql, String name, CuboidRegion region, UUID owner) {
		this.sql = sql;
		this.name = name;
		this.owner = owner;
		Collections.addAll(flags, ClaimFlag.ALL);
		if (region != null) {
			updateRegion(region);
		}
		ClaimMap.register(this);
	}
	
	protected Claim(SQLHelper sql, String name, CuboidRegion region, UUID owner, Collection<ClaimFlag> flags) {
		this.sql = sql;
		this.name = name;
		this.owner = owner;
		this.flags.addAll(flags);
		if (region != null) {
			updateRegion(region);
		}
		ClaimMap.register(this);
	}
	
	public Subclaim getSubclaim(String name) {
		return subclaims.stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	public Subclaim getSubclaim(Location loc) {
		return subclaims.stream().filter(s -> s.getRegion().contains(loc)).findFirst().orElse(null);
	}

	public String getFullName() {
		return getOwner().getName() + ":" + name;
	}

	public void remove() {
		ClaimMap.unregister(this);
		if (region != null) {
			region.unprotect();
		}
		sql.execute("DELETE FROM claims WHERE name=? AND owner=?;", name, owner.toString());
	}
	
	public void updateRegion() {
		if (region != null) {
			ClaimMap.unregister(this);
			updateRegion(region.getRegion().toCuboid());
		}
		ClaimMap.register(this);
	}
	
	public void addFlag(ClaimFlag... flags) {
		Collections.addAll(this.flags, flags);
		updateFlags();
		if (region != null) {
			Arrays.stream(flags).forEach(f -> region.getPolicy().addProtectionTypes(f.getProtectionTypes()));
		}
	}
	
	public void removeFlag(ClaimFlag... flags) {
		Arrays.stream(flags).forEach(this.flags::remove);
		updateFlags();
		if (region != null) {
			Arrays.stream(flags).forEach(f -> region.getPolicy().removeProtectionTypes(f.getProtectionTypes()));
		}
	}
	
	protected void updateFlags() {
		sql.execute("UPDATE claims SET flags=? WHERE name=? AND owner=? AND parent IS NULL;",
				flags.stream().map(ClaimFlag::getName).collect(Collectors.joining(",")),
				name, owner);
	}
	
	public Set<ClaimFlag> getFlags() {
		return flags;
	}
	
	public boolean hasAtLeast(CommandSender sender, ClaimRank rank) {
		if (sender.hasPermission("redclaims.admin")) {
			return true;
		}
		if (!(sender instanceof Player)) {
			return false;
		}
		Player player = (Player) sender;
		return getRank(player).getRank() >= rank.getRank();
	}
	
	public void updateRegion(CuboidRegion region) {
		if (this instanceof Subclaim) {
			return;
		}
		if (this.region != null) {
			this.region.unprotect();
		}
		this.region = new ProtectedRegion(region, flags.stream().flatMap(c -> Arrays.stream(c.getProtectionTypes())).toArray(ProtectionType[]::new));
		ProtectionPolicy policy = this.region.getPolicy();
		policy.addProtectionTypes(DEFAULT_PROTECTIONS);
		policy.addBypassPolicy((p, t) -> p != null && getRank(p).getRank() >= ClaimRank.MEMBER.getRank());
		policy.addBypassPolicy((p, t) -> p != null && ClaimBypass.hasBypass(p.getUniqueId()));
		policy.addBypassPolicy((p, t, b) -> subclaims.stream().anyMatch(c ->
				c.getRegion().contains(b) && (!c.getFlags().contains(ClaimFlag.BY_TYPE.get(t)) || (p != null && c.getRank(p).getRank() >= ClaimRank.MEMBER.getRank()))));
		flags.forEach(f -> policy.addProtectionTypes(f.getProtectionTypes()));
	}
	
	public void initQuery() {
		sql.execute("INSERT INTO claims VALUES (?, ?, NULL, ?, ?);",
				owner, name, flags.stream().map(ClaimFlag::getName).collect(Collectors.joining(",")), region.getRegion().toString());
	}
	
	public Map<UUID, ClaimRank> getAllMembers() {
		return members;
	}
	
	private BlockFace[] FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
	
	public void visualize(Player player, boolean subclaims) {
		visualize(player, subclaims, player::sendBlockChange);
	}
	
	public void unvisualize(Player player) {
		visualize(player, true, (l, b) -> player.sendBlockChange(l, l.getBlock().getBlockData()));
	}
	
	protected void visualize(Player player, boolean subclaims, BiConsumer<Location, BlockData> updater) {
		CuboidRegion region = getRegion();
		Location[] corners = region.clone().expand(-1, 0, -1, 0, -1, 0).getCorners();
		List<Location> locations = new ArrayList<>();
		Collections.addAll(locations, corners);
		locations.removeIf(l -> l.getBlockY() == region.getEnd().getBlockY());
		for (Location location : locations) {
			Location loc = raise(location);
			updater.accept(loc, Material.SHROOMLIGHT.createBlockData());
			for (BlockFace face : FACES) {
				Block rel = loc.getBlock().getRelative(face);
				if (!region.contains(rel)) {
					continue;
				}
				updater.accept(raise(rel.getLocation()), Material.WAXED_OXIDIZED_CUT_COPPER.createBlockData());
			}
		}
		corners = new Location[] {region.getStart(), region.getEnd().subtract(1, 1, 1)};
		for (Location start : corners) {
			for (BlockFace face : FACES) {
				Block rel = start.getBlock().getRelative(face);
				if (!region.contains(rel)) {
					continue;
				}
				Vector v = face.getDirection().multiply(5);
				Location loc = start.clone().add(v);
				while (region.contains(loc.clone().add(face.getDirection()))) {
					updater.accept(raise(loc), Material.WAXED_OXIDIZED_CUT_COPPER.createBlockData());
					loc = loc.add(v);
				}
			}
		}
		if (subclaims) {
			this.subclaims.forEach(s -> s.visualize(player, false, updater));
		}
	}
	
	public boolean flagApplies(Location loc, String name) {
		ClaimFlag flag = ClaimFlag.BY_NAME.get(name);
		if (!getRegion().contains(loc) || !flags.contains(flag)) {
			return false;
		}
		if (subclaims.stream().anyMatch(s -> s.getRegion().contains(loc) && !s.getFlags().contains(flag))) {
			return false;
		}
		return true;
	}
	
	private Location raise(Location loc) {
		return loc.getWorld().getHighestBlockAt(loc).getLocation();
	}
	
	public boolean hasFlag(String name) {
		return flags.contains(ClaimFlag.BY_NAME.get(name));
	}
	
	public void setName(String name) {
		sql.execute("UPDATE claims SET name=? WHERE owner=? AND name=?;", name, owner, this.name);
		this.name = name;
	}
	
	public void setRank(OfflinePlayer player, ClaimRank rank) {
		if (rank == ClaimRank.VISITOR) {
			members.remove(player.getUniqueId());
			sql.execute("DELETE FROM members WHERE owner=? AND name=? AND member=?;", owner, name, player.getUniqueId());
			return;
		}
		if (rank == ClaimRank.OWNER) {
			ClaimStorage storage = RedClaims.getInstance().getClaimStorage();
			if (storage.getClaim(player.getUniqueId(), name) != null) {
				throw new IllegalArgumentException("Player already has a claim with the same name");
			}
			RedClaims.getInstance().getClaimStorage().updateOwner(this, player.getUniqueId());
			UUID oldOwner = owner;
			setRank(getOwner(), ClaimRank.TRUSTED);
			setRank(player, ClaimRank.VISITOR);
			owner = player.getUniqueId();
			sql.execute("UPDATE claims SET owner=? WHERE owner=? AND name=?;", player.getUniqueId(), oldOwner, name);
			return;
		}
		boolean exists = members.containsKey(player.getUniqueId());
		
		members.put(player.getUniqueId(), rank);
		if (exists) {
			sql.execute("UPDATE members SET rank=? WHERE owner=? AND name=?;", rank.toString(), owner, name);
			return;
		}
		sql.execute("INSERT INTO members VALUES (?, ?, ?, ?);", owner, name, player.getUniqueId(), rank.toString());
	}
	
	public ClaimRank getRank(OfflinePlayer player) {
		if (player.getUniqueId().equals(owner)) {
			return ClaimRank.OWNER;
		}
		return members.getOrDefault(player.getUniqueId(), ClaimRank.VISITOR);
	}
	
	public Subclaim createSubclaim(String name, CuboidRegion region) {
		if (name.toLowerCase().equals(this.name)) {
			throw new IllegalArgumentException("Subclaims may not have the same name as their parent");
		}
		if (subclaims.size() >= 10) {
			throw new IllegalArgumentException("Claim may not have more than 10 subclaims");
		}
		if (getRegion().getIntersection(region).getBlockVolume() != region.getBlockVolume()) {
			throw new IllegalArgumentException("Subclaim is not entirely contained in parent");
		}
		if (subclaims.stream().anyMatch(s -> s.getRegion().overlaps(region))) {
			throw new IllegalArgumentException("Subclaim intersects with an existing subclaim");
		}
		Subclaim subclaim = new Subclaim(name, region, owner, this);
		subclaims.add(subclaim);
		subclaim.initQuery();
		return subclaim;
	}
	
	public ProtectedRegion getProtectedRegion() {
		return region;
	}
	
	public CuboidRegion getRegion() {
		return region.getRegion().toCuboid();
	}
	
	public List<Subclaim> getSubclaims() {
		return subclaims;
	}
	
	public String getName() {
		return name;
	}
	
	public OfflinePlayer getOwner() {
		return Bukkit.getOfflinePlayer(owner);
	}
	
}

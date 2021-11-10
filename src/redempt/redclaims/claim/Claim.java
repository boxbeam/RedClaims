package redempt.redclaims.claim;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redempt.redclaims.ClaimFlag;
import redempt.redlib.protection.ProtectedRegion;
import redempt.redlib.protection.ProtectionPolicy;
import redempt.redlib.protection.ProtectionPolicy.ProtectionType;
import redempt.redlib.region.CuboidRegion;
import redempt.redlib.sql.SQLHelper;

import java.util.*;
import java.util.stream.Collectors;

public class Claim {
	
	private static final ProtectionType[] DEFAULT_PROTECTIONS = {
			ProtectionType.WITHER,
			ProtectionType.SILVERFISH,
			ProtectionType.PISTONS_IN,
			ProtectionType.FLOW_IN,
			ProtectionType.STRUCTURE_GROWTH_IN,
			ProtectionType.PORTAL_PAIRING,
	};
	
	private ProtectedRegion region;
	private OfflinePlayer owner;
	private String name;
	private Map<UUID, ClaimRank> members = new LinkedHashMap<>();
	private Set<ClaimFlag> flags = new LinkedHashSet<>();
	private List<Subclaim> subclaims = new ArrayList<>();
	protected SQLHelper sql;

	protected Claim(SQLHelper sql, String name, CuboidRegion region, OfflinePlayer owner) {
		this.sql = sql;
		this.name = name;
		this.owner = owner;
		Collections.addAll(flags, ClaimFlag.ALL);
		updateRegion(region);
		ClaimMap.register(this);
	}
	
	protected Claim(SQLHelper sql, String name, CuboidRegion region, OfflinePlayer owner, Collection<ClaimFlag> flags) {
		this.sql = sql;
		this.name = name;
		this.owner = owner;
		this.flags.addAll(flags);
		updateRegion(region);
		ClaimMap.register(this);
	}
	
	public Subclaim getSubclaim(String name) {
		return subclaims.stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}
	
	public void remove() {
		ClaimMap.unregister(this);
		if (region != null) {
			region.unprotect();
		}
		sql.execute("DELETE FROM claims WHERE name=? AND owner=?;", name, owner.getUniqueId().toString());
	}
	
	public void updateRegion() {
		if (region != null) {
			ClaimMap.unregister(this);
			updateRegion(region.getRegion().toCuboid());
		}
		ClaimMap.register(this);
	}
	
	public void addFlag(ClaimFlag flag) {
		flags.add(flag);
		updateFlags();
		region.getPolicy().addProtectionTypes(flag.getProtectionTypes());
	}
	
	public void removeFlag(ClaimFlag flag) {
		flags.remove(flag);
		updateFlags();
		region.getPolicy().removeProtectionTypes(flag.getProtectionTypes());
	}
	
	private void updateFlags() {
		sql.execute("UPDATE claims SET flags=? WHERE name=? AND owner=? AND parent=NULL;",
				flags.stream().map(ClaimFlag::getName).collect(Collectors.joining(",")),
				name, owner.getUniqueId());
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
		policy.addBypassPolicy((p, t, b) -> subclaims.stream().anyMatch(c -> c.getRegion().contains(b) && !c.getFlags().contains(ClaimFlag.BY_TYPE.get(t))));
		flags.forEach(f -> policy.addProtectionTypes(f.getProtectionTypes()));
	}
	
	public void initQuery() {
		sql.execute("INSERT INTO claims VALUES (?, ?, NULL, ?, ?);",
				owner.getUniqueId().toString(), name, flags.stream().map(ClaimFlag::getName).collect(Collectors.joining(",")), region.getRegion().toString());
	}
	
	public Map<UUID, ClaimRank> getAllMembers() {
		return members;
	}
	
	public void setRank(OfflinePlayer player, ClaimRank rank) {
		if (rank == ClaimRank.VISITOR) {
			members.remove(player.getUniqueId());
			sql.execute("DELETE FROM members WHERE owner=? AND name=? AND member=?;", owner.getUniqueId().toString(), name, player.getUniqueId());
			return;
		}
		if (rank == ClaimRank.OWNER) {
			UUID oldUUID = owner.getUniqueId();
			setRank(owner, ClaimRank.TRUSTED);
			setRank(player, ClaimRank.VISITOR);
			sql.execute("UPDATE claims SET owner=? WHERE owner=? AND name=?;", oldUUID.toString(), owner.getUniqueId().toString(), name);
			owner = player;
			return;
		}
		members.put(player.getUniqueId(), rank);
		if (members.containsKey(player.getUniqueId())) {
			sql.execute("UPDATE members SET rank=? WHERE owner=? AND name=?;", rank.toString(), owner.getUniqueId(), name);
			return;
		}
		sql.execute("INSERT INTO members VALUES (?, ?, ?, ?);", owner.getUniqueId(), name, player.getUniqueId().toString(), rank.toString());
	}
	
	public ClaimRank getRank(OfflinePlayer player) {
		if (player.equals(owner)) {
			return ClaimRank.OWNER;
		}
		return members.getOrDefault(player.getUniqueId(), ClaimRank.VISITOR);
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
		return owner;
	}
	
}

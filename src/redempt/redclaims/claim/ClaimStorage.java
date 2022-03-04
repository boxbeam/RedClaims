package redempt.redclaims.claim;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import redempt.redclaims.ClaimFlag;
import redempt.redclaims.ClaimLimits;
import redempt.redclaims.RedClaims;
import redempt.redlib.misc.LocationUtils;
import redempt.redlib.region.CuboidRegion;
import redempt.redlib.sql.SQLHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClaimStorage {

	private Map<UUID, Map<String, Claim>> claims = new HashMap<>();
	
	private SQLHelper sql;
	
	public ClaimStorage(Path path) {
		sql = new SQLHelper(SQLHelper.openSQLite(path));
		sql.setCommitInterval(20 * 60 * 5);
		sql.execute("PRAGMA foreign_keys = ON;");
		sql.execute("CREATE TABLE IF NOT EXISTS claims (owner STRING, name STRING, parent STRING, flags STRING, region STRING," +
				"PRIMARY KEY (owner, name)," +
				"FOREIGN KEY (owner, parent) REFERENCES claims(owner, name) ON UPDATE CASCADE ON DELETE CASCADE);");
		sql.execute("CREATE TABLE IF NOT EXISTS members (owner STRING, name STRING, member STRING, rank STRING," +
				"PRIMARY KEY (owner, name, member)," +
				"FOREIGN KEY (owner, name) REFERENCES claims(owner, name) ON UPDATE CASCADE ON DELETE CASCADE);");
	}
	
	public Claim getClaim(UUID owner, String name) {
		Map<String, Claim> owned = claims.get(owner);
		if (owned == null) {
			return null;
		}
		return owned.get(name.toLowerCase());
	}
	
	public void unregister(Claim claim) {
		ClaimMap.unregister(claim);
		claims.get(claim.getOwner().getUniqueId()).remove(claim.getName().toLowerCase());
	}
	
	public void register(Claim claim) {
		ClaimMap.register(claim);
		claims.get(claim.getOwner().getUniqueId()).put(claim.getName().toLowerCase(), claim);
	}
	
	public int getClaimBlocks(Claim claim) {
		return getClaimBlocks(claim.getRegion());
	}
	
	public int getClaimBlocks(CuboidRegion region) {
		int[] dim = region.getBlockDimensions();
		return dim[0] * dim[2];
	}
	
	public int getClaimedBlocks(UUID player) {
		Map<String, Claim> claims = getClaims(player);
		if (claims == null) {
			return 0;
		}
		return claims.values().stream().mapToInt(this::getClaimBlocks).sum();
	}
	
	public Map<String, Claim> getClaims(UUID player) {
		return claims.get(player);
	}
	
	public void updateOwner(Claim claim, UUID owner) {
		claims.get(claim.getOwner().getUniqueId()).remove(claim.getName());
		claims.computeIfAbsent(owner, k -> new HashMap<>()).put(claim.getName(), claim);
	}
	
	public void loadAll() {
		Map<String, List<Subclaim>> subclaims = new HashMap<>();
		sql.queryResults("SELECT * FROM claims;").forEach(r -> {
			UUID id = UUID.fromString(r.getString(1));
			String name = r.getString(2);
			String parent = r.getString(3);
			Set<ClaimFlag> flags = Arrays.stream(r.getString(4).split(","))
				.filter(s -> s.length() != 0)
				.map(ClaimFlag.BY_NAME::get)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			String regionString = r.getString(5);
			String worldName = regionString.substring(0, regionString.indexOf(' '));
			LocationUtils.waitForWorld(worldName, w -> {
				CuboidRegion region = CuboidRegion.fromString(regionString);
				if (parent != null) {
					Subclaim sub = new Subclaim(sql, name, region, id, flags);
					Claim parentClaim = getClaim(id, parent);
					if (parentClaim != null) {
						sub.setParent(parentClaim);
						parentClaim.getSubclaims().add(sub);
					} else {
						subclaims.computeIfAbsent(parent.toLowerCase(), k -> new ArrayList<>()).add(sub);
					}
					return;
				}
				Claim claim = new Claim(sql, name, region, id, flags);
				claims.computeIfAbsent(id, k -> new HashMap<>()).put(name.toLowerCase(), claim);
				subclaims.getOrDefault(name, new ArrayList<>()).forEach(sub -> {
					sub.setParent(claim);
					claim.getSubclaims().add(sub);
				});
				subclaims.remove(name);
			});
		});
		sql.queryResults("SELECT * FROM members;").forEach(r -> {
			UUID owner = UUID.fromString(r.getString(1));
			String name = r.getString(2);
			UUID member = UUID.fromString(r.getString(3));
			ClaimRank rank = ClaimRank.valueOf(r.getString(4));
			Claim claim = getClaim(owner, name);
			claim.getAllMembers().put(member, rank);
		});
	}
	
	public Claim createClaim(Player owner, String name, CuboidRegion region, boolean dummy) {
		if (RedClaims.getInstance().config().unclaimableWorlds.contains(region.getWorld().getName())) {
			throw new IllegalArgumentException("This world cannot be claimed");
		}
		int[] dim = region.getBlockDimensions();
		if (dim[0] < 10 || dim[2] < 10) {
			throw new IllegalArgumentException("Claim is too small, must be at least 10x10");
		}
		int claimBlocks = dim[0] * dim[2];
		if (claimBlocks > ClaimLimits.getRemainingClaimLimit(owner)) {
			throw new IllegalArgumentException("Insufficient claim budget to create this claim");
		}
		if (ClaimMap.getClaims(region).size() != 0) {
			throw new IllegalArgumentException("Claim would overlap an existing claim");
		}
		if (getClaim(owner.getUniqueId(), name) != null) {
			throw new IllegalArgumentException("Player has already created a claim with that name");
		}
		if (!dummy) {
			Claim claim = new Claim(sql, name, region, owner.getUniqueId());
			claim.initQuery();
			claims.computeIfAbsent(owner.getUniqueId(), k -> new HashMap<>()).put(name.toLowerCase(), claim);
			return claim;
		}
		return null;
	}
	
	public void renameClaim(Claim claim, String name) {
		if (getClaim(claim.getOwner().getUniqueId(), name) != null) {
			throw new IllegalArgumentException("Player has already created a claim with that name");
		}
		Map<String, Claim> map = claims.get(claim.getOwner().getUniqueId());
		map.remove(claim.getName());
		map.put(name.toLowerCase(), claim);
		claim.setName(name);
	}
	
	public void deleteClaim(Claim claim) {
		claim.remove();
		claims.get(claim.getOwner().getUniqueId()).remove(claim.getName().toLowerCase());
	}
	
	public void close() {
		sql.commit();
		sql.close();
	}
	
}

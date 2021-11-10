package redempt.redclaims.claim;

import org.bukkit.Location;
import redempt.redlib.region.CuboidRegion;
import redempt.redlib.region.RegionMap;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ClaimMap {
	
	private static RegionMap<Claim> claims = new RegionMap<>(100);
	
	public static void register(Claim claim) {
		if (claim instanceof Subclaim) {
			return;
		}
		claims.set(claim.getRegion(), claim);
	}
	
	public static void unregister(Claim claim) {
		if (claim instanceof Subclaim) {
			return;
		}
		claims.remove(claim.getRegion(), claim);
	}
	
	public static Set<Claim> getClaims(CuboidRegion region) {
		Location center = region.getCenter();
		int radius = Arrays.stream(region.getBlockDimensions()).max().getAsInt();
		return claims.getNearby(center, radius).stream().filter(c -> c.getRegion().overlaps(region)).collect(Collectors.toSet());
	}
	
	public static Claim getClaim(Location loc) {
		return claims.get(loc).stream().filter(c -> c.getRegion().contains(loc)).findFirst().orElse(null);
	}
	
}

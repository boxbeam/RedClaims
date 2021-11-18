package redempt.redclaims;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimBypass {
	
	private static Set<UUID> bypass = new HashSet<>();
	
	public static boolean hasBypass(UUID id) {
		return bypass.contains(id);
	}
	
	public static boolean toggle(UUID id) {
		boolean add;
		if (!(add = bypass.add(id))) {
			bypass.remove(id);
		}
		return add;
	}
	
}

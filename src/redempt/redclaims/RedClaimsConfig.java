package redempt.redclaims;

import org.bukkit.Material;
import redempt.redlib.config.annotations.ConfigMappable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigMappable
public class RedClaimsConfig {

	public int defaultClaimBlocks = 1000;
	public Material claimTool = Material.GOLDEN_SHOVEL;
	public Set<ClaimFlag> defaultDisabledFlags = new LinkedHashSet<>(List.of(ClaimFlag.BY_NAME.get("noannounce")));
	public Set<String> unclaimableWorlds = new LinkedHashSet<>();
	
}

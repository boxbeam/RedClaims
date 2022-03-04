package redempt.redclaims;

import org.bukkit.Material;
import redempt.redlib.config.annotations.ConfigMappable;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigMappable
public class RedClaimsConfig {

	public int defaultClaimBlocks = 1000;
	public Material claimTool = Material.GOLDEN_SHOVEL;
	public Set<ClaimFlag> defaultDisabledFlags = new LinkedHashSet<>();
	public Set<String> unclaimableWorlds = new LinkedHashSet<>();
	
}

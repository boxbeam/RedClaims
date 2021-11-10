package redempt.redclaims.claim;

import org.bukkit.OfflinePlayer;
import redempt.redclaims.ClaimFlag;
import redempt.redlib.region.CuboidRegion;
import redempt.redlib.sql.SQLHelper;

import java.util.Collection;
import java.util.Set;

public class Subclaim extends Claim {
	
	private Claim parent;
	
	public Subclaim(String name, CuboidRegion region, OfflinePlayer owner, Claim parent) {
		super(parent.sql, name, region, owner, parent.getFlags());
		this.parent = parent;
	}
	
	public Subclaim(SQLHelper sql, String name, CuboidRegion region, OfflinePlayer owner, Set<ClaimFlag> flags) {
		super(sql, name, region, owner, flags);
	}
	
	public void setParent(Claim parent) {
		this.parent = parent;
	}
	
	public Claim getParent() {
		return parent;
	}
	
}

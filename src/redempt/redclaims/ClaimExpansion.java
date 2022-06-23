package redempt.redclaims;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import redempt.redclaims.claim.Claim;
import redempt.redclaims.claim.ClaimMap;
import redempt.redclaims.claim.ClaimStorage;

public class ClaimExpansion extends PlaceholderExpansion {

    private final RedClaims redClaims = RedClaims.getInstance();

    @Override
    public String getAuthor() {
        return "UntouchedOdin0";
    }

    @Override
    public String getIdentifier() {
        return "redclaims";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // This is required or else PlaceholderAPI will unregister the Expansion on reload
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String placeholder) {
        Player player = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        ClaimStorage claimStorage = RedClaims.getInstance().getClaimStorage();
        switch (placeholder) {
            case "budget":
                return player == null ? null : Integer.toString(ClaimLimits.getClaimLimit(player));
            case "remaining_budget":
                return player == null ? null : Integer.toString(ClaimLimits.getRemainingClaimLimit(player));
            case "used_budget":
                return Integer.toString(claimStorage.getClaimedBlocks(offlinePlayer.getUniqueId()));
            case "current_claim":
                if (player == null) {
                    return null;
                }
                Claim claim = ClaimMap.getClaim(player.getLocation());
                return claim == null ? null : claim.getFullName();
        }
        return null;
    }
}

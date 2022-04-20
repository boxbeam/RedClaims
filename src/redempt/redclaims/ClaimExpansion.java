package redempt.redclaims;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import redempt.redclaims.claim.ClaimMap;

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
        Player player = offlinePlayer.getPlayer();

        // Check if the player isn't null, because you can't do anything if they are.

        if (player != null) {
            // Make the local variables with the values, budget, budget used & location.
            int budget = ClaimLimits.getClaimLimit(player);
            int budgetUsed = redClaims.getClaimStorage().getClaimedBlocks(player.getUniqueId());
            Location location = player.getLocation();

            // Do the check to see which placeholder the user wants and return the value.
            if (placeholder.equalsIgnoreCase("player_budget")) {
                return Integer.toString(budget);
            } else if (placeholder.equalsIgnoreCase("player_budget_used")) {
                return Integer.toString(budgetUsed);
            } else if (placeholder.equalsIgnoreCase("claim_player_is_in")) {
                return ClaimMap.getClaim(location).getName();
            }
        }
        return null; // Placeholder is unknown by the Expansion
    }
}

package redempt.redclaims;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimTool implements Listener {

	private ItemStack item;
	private Map<UUID, Location[]> selections = new HashMap<>();
	
	public ClaimTool(RedClaims plugin, ItemStack item) {
		this.item = item;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

}

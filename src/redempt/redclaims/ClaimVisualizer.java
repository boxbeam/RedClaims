package redempt.redclaims;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import redempt.redlib.misc.EventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimVisualizer {

    private static Map<UUID, BlockDisplayer> displayers = new HashMap<>();

    public static void init() {
        new EventListener<>(PlayerQuitEvent.class, e -> displayers.remove(e.getPlayer().getUniqueId()));
    }

    public static BlockDisplayer getDisplayer(Player player) {
        return displayers.computeIfAbsent(player.getUniqueId(), k -> new BlockDisplayer(player));
    }

}

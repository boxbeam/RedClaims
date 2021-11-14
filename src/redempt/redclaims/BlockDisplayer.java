package redempt.redclaims;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockDisplayer {
	
	private Set<Block> displayed = new HashSet<>();
	private Map<Block, BlockData> buffer = new HashMap<>();
	private Player player;
	
	public BlockDisplayer(Player player) {
		this.player = player;
	}
	
	public void display(Block block, BlockData data) {
		buffer.put(block, data);
	}
	
	public void display(Block block, Material type) {
		display(block, type.createBlockData());
	}
	
	public void show() {
		displayed.removeAll(buffer.keySet());
		displayed.forEach(b -> player.sendBlockChange(b.getLocation(), b.getBlockData()));
		buffer.forEach((b, d) -> player.sendBlockChange(b.getLocation(), d));
		displayed.clear();
		displayed.addAll(buffer.keySet());
		buffer.clear();
	}
	
	public void clear() {
		displayed.forEach(b -> player.sendBlockChange(b.getLocation(), b.getBlockData()));
		buffer.clear();
		displayed.clear();
	}
	
}

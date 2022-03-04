package redempt.redclaims;

import org.bukkit.Material;
import redempt.redlib.protection.ProtectionPolicy.ProtectionType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ClaimFlag {
	
	public static final ClaimFlag[] ALL = {
			new ClaimFlag(Material.STONE, "place-blocks", ProtectionType.PLACE_BLOCK, ProtectionType.ENTITY_FORM_BLOCK),
			new ClaimFlag(Material.DIAMOND_PICKAXE, "break-blocks", ProtectionType.BREAK_BLOCK, ProtectionType.USE_BUCKETS),
			new ClaimFlag(Material.TNT, "explosions", ProtectionType.ENTITY_EXPLOSION, ProtectionType.BLOCK_EXPLOSION),
			new ClaimFlag(Material.FLINT_AND_STEEL, "fire", ProtectionType.FIRE),
			new ClaimFlag(Material.OAK_DOOR, "interact", ProtectionType.INTERACT, ProtectionType.PLACE_ENTITY, ProtectionType.INTERACT_ENTITY),
			new ClaimFlag(Material.CHEST, "containers", ProtectionType.CONTAINER_ACCESS),
			new ClaimFlag(Material.DIAMOND_SWORD, "pvp"),
			new ClaimFlag(Material.PORKCHOP, "animals")
	};
	
	public static final Map<String, ClaimFlag> BY_NAME = Arrays.stream(ALL).collect(HashMap::new, (m, c) -> m.put(c.name, c), HashMap::putAll);
	public static final Map<ProtectionType, ClaimFlag> BY_TYPE = Arrays.stream(ALL).collect(HashMap::new, (m, c) -> Arrays.stream(c.types).forEach(f -> m.put(f, c)), HashMap::putAll);
	
	private ProtectionType[] types;
	private Material icon;
	private String name;
	
	public ClaimFlag(Material icon, String name, ProtectionType... types) {
		this.types = types;
		this.icon = icon;
		this.name = name;
	}
	
	public Material getIcon() {
		return icon;
	}
	
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ClaimFlag flag)) {
			return false;
		}
		return flag.name.equals(name);
	}
	
	public ProtectionType[] getProtectionTypes() {
		return types;
	}
	
}

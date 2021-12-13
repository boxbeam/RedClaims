package redempt.redclaims;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import redempt.redclaims.claim.ClaimStorage;
import redempt.redclaims.claim.MiscProtections;
import redempt.redlib.commandmanager.CommandParser;
import redempt.redlib.commandmanager.Messages;
import redempt.redlib.configmanager.ConfigManager;
import redempt.redlib.configmanager.annotations.ConfigValue;
import redempt.redlib.misc.UserCache;

import java.nio.file.Path;

public class RedClaims extends JavaPlugin implements Listener {
	
	private static RedClaims plugin;
	
	public static RedClaims getInstance() {
		return plugin;
	}
	
	private ClaimStorage storage;
	
	@ConfigValue
	private int defaultClaimBlocks = 1000;
	@ConfigValue
	private Material claimTool = Material.GOLDEN_SHOVEL;
	
	@Override
	public void onEnable() {
		plugin = this;
		Bukkit.getPluginManager().registerEvents(this, this);
		getDataFolder().mkdirs();
		Path path = getDataFolder().toPath().resolve("claims.db");
		storage = new ClaimStorage(path);
		storage.loadAll();
		UserCache.asyncInit();
		Messages.load(this);
		new CommandListener(this).register();
		new MiscProtections(this);
		new ConfigManager(this).register(this).saveDefaults().load();
		ClaimLimits.init(this, defaultClaimBlocks);
		ClaimVisualizer.init();
	}
	
	public Material getClaimToolMaterial() {
		return claimTool;
	}
	
	@Override
	public void onDisable() {
		storage.close();
	}
	
	public ClaimStorage getClaimStorage() {
		return storage;
	}
	
}

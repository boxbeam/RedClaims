package redempt.redclaims;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import redempt.redclaims.claim.ClaimStorage;
import redempt.redclaims.claim.MiscProtections;
import redempt.redlib.commandmanager.Messages;
import redempt.redlib.config.ConfigManager;
import redempt.redlib.misc.UserCache;

import java.nio.file.Path;

public class RedClaims extends JavaPlugin implements Listener {
	
	private static RedClaims plugin;
	
	public static RedClaims getInstance() {
		return plugin;
	}
	
	private ClaimStorage storage;
	
	private static RedClaimsConfig config = new RedClaimsConfig();
	
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
		ConfigManager.create(this).target(new RedClaimsConfig()).saveDefaults().load();
		ClaimLimits.init(this, config().defaultClaimBlocks());
		ClaimVisualizer.init();
	}
	
	public RedClaimsConfig config() {
		return config;
	}
	
	@Override
	public void onDisable() {
		storage.close();
	}
	
	public ClaimStorage getClaimStorage() {
		return storage;
	}
	
}

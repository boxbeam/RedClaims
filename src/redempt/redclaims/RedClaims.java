package redempt.redclaims;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import redempt.redclaims.claim.ClaimStorage;
import redempt.redclaims.claim.MiscProtections;
import redempt.redlib.commandmanager.CommandParser;
import redempt.redlib.commandmanager.Messages;
import redempt.redlib.misc.UserCache;

import java.nio.file.Path;

public class RedClaims extends JavaPlugin implements Listener {
	
	private ClaimStorage storage;
	
	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		getDataFolder().mkdirs();
		Path path = getDataFolder().toPath().resolve("claims.db");
		storage = new ClaimStorage(path);
		storage.loadAll();
		UserCache.asyncInit();
		Messages.load(this);
		new CommandListener(this).register();
		new MiscProtections(this);
	}
	
	@Override
	public void onDisable() {
		storage.close();
	}
	
	public ClaimStorage getClaimStorage() {
		return storage;
	}
	
}

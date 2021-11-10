package redempt.redclaims;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redempt.redclaims.claim.Claim;
import redempt.redclaims.claim.ClaimMap;
import redempt.redclaims.claim.ClaimRank;
import redempt.redclaims.claim.ClaimStorage;
import redempt.redclaims.claim.Subclaim;
import redempt.redlib.commandmanager.ArgType;
import redempt.redlib.commandmanager.CommandHook;
import redempt.redlib.commandmanager.CommandParser;
import redempt.redlib.commandmanager.ContextProvider;
import redempt.redlib.commandmanager.Messages;
import redempt.redlib.itemutils.ItemBuilder;
import redempt.redlib.itemutils.ItemUtils;
import redempt.redlib.misc.UserCache;
import redempt.redlib.region.CuboidRegion;
import redempt.redlib.region.SelectionTool;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandListener {
	
	private RedClaims plugin;
	private SelectionTool tool;
	
	public CommandListener(RedClaims plugin) {
		this.plugin = plugin;
	}
	
	public void register() {
		ClaimStorage storage = plugin.getClaimStorage();
		ArgType<Claim> claimType = new ArgType<>("claim", (c, s) -> {
			int index = s.indexOf(':');
			if (index == -1) {
				if (!(c instanceof Player)) {
					return null;
				}
				Player player = (Player) c;
				return storage.getClaim(player.getUniqueId(), s);
			}
			String playerName = s.substring(0, index);
			String claimName = s.substring(index + 1);
			OfflinePlayer player = UserCache.getOfflinePlayer(playerName);
			if (player == null) {
				return null;
			}
			return storage.getClaim(player.getUniqueId(), claimName);
		}).tabStream((c, s) -> {
			String last = s[s.length - 1];
			int index = last.indexOf(':');
			if (index == -1) {
				if (!(c instanceof Player)) {
					return null;
				}
				Player player = (Player) c;
				Map<String, Claim> claims = storage.getClaims(player.getUniqueId());
				if (claims == null) {
					return null;
				}
				return claims.keySet().stream();
			}
			String playerName = last.substring(0, index);
			OfflinePlayer player = UserCache.getOfflinePlayer(playerName);
			if (player == null) {
				return null;
			}
			Map<String, Claim> claims = storage.getClaims(player.getUniqueId());
			if (claims == null) {
				return null;
			}
			return claims.keySet().stream().map(n -> player.getName() + ":" + n);
		});
		
		ArgType<Subclaim> subclaimType = claimType.subType("subclaim", (s, c) -> c.getSubclaim(s))
				.tabStream((s, c, a) -> c.getSubclaims().stream().map(Subclaim::getName));
		ArgType<ClaimFlag> flagType = ArgType.of("flag", ClaimFlag.BY_NAME);
		ArgType<OfflinePlayer> userType = new ArgType<>("user", UserCache::getOfflinePlayer).tabStream(c -> Bukkit.getOnlinePlayers().stream().map(Player::getName));
		ArgType<ClaimRank> rankType = new ArgType<>("role", s -> ClaimRank.valueOf(s.toUpperCase())).tabStream(c -> Arrays.stream(ClaimRank.values()).map(r -> r.name().toLowerCase()));
		
		ContextProvider<CuboidRegion> selectionProvider = new ContextProvider<>("selection", Messages.msg("noSelection"), c -> tool.getRegion(c.getUniqueId()));
		ContextProvider<Claim> currentClaimProvider = new ContextProvider<>("currentClaim", Messages.msg("notInClaim"), c -> ClaimMap.getClaim(c.getLocation()));
		
		tool = new SelectionTool(new ItemBuilder(Material.STICK).setName(ChatColor.GREEN + "Claiming tool"));
		
		new CommandParser(plugin.getResource("command.rdcml"))
				.setArgTypes(claimType, subclaimType, flagType, userType, rankType)
				.setContextProviders(selectionProvider, currentClaimProvider)
				.parse().register("redclaims", this);
	}
	
	@CommandHook("createClaim")
	public void createClaim(Player sender, String name, CuboidRegion selection) {
		try {
			plugin.getClaimStorage().createClaim(sender, name, selection);
			sender.sendMessage(Messages.msg("claimCreated"));
		} catch (IllegalArgumentException e) {
			sender.sendMessage(Messages.msg("errorColor") + e.getMessage());
		}
	}
	
	@CommandHook("claimInfo")
	public void claimInfo(CommandSender sender, Claim claim) {
		sender.sendMessage(claim.getName() + " is owned by " + claim.getOwner().getName());
		sender.sendMessage("flags: " + claim.getFlags().stream().map(ClaimFlag::getName).collect(Collectors.joining(", ")));
	}
	
	@CommandHook("deleteClaim")
	public void deleteClaim(CommandSender sender, Claim claim) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		plugin.getClaimStorage().deleteClaim(claim);
		sender.sendMessage(Messages.msg("claimDeleted"));
	}
	
	@CommandHook("setRole")
	public void setRole(CommandSender sender, Claim claim, OfflinePlayer user, ClaimRank rank) {
		if (user.equals(claim.getOwner())) {
			sender.sendMessage(Messages.msg("cannotSetOwnerRole"));
			return;
		}
		switch (rank) {
			case VISITOR:
			case MEMBER:
				if (!claim.hasAtLeast(sender, ClaimRank.TRUSTED)
						|| (sender instanceof Player && claim.getRank(user).getRank() >= claim.getRank((Player) sender).getRank())) {
					sender.sendMessage(Messages.msg("insufficientPermission"));
					return;
				}
				break;
			case TRUSTED:
				if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
					sender.sendMessage(Messages.msg("insufficientPermission"));
					return;
				}
				break;
		}
		claim.setRank(user, rank);
		sender.sendMessage(Messages.msg("roleSet"));
	}
	
	@CommandHook("addClaimFlag")
	public void addClaimFlag(CommandSender sender, Claim claim, ClaimFlag flag) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		claim.addFlag(flag);
		sender.sendMessage(Messages.msg("protectionAdded"));
	}
	
	@CommandHook("removeClaimFlag")
	public void removeClaimFlag(CommandSender sender, Claim claim, ClaimFlag flag) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		claim.removeFlag(flag);
		sender.sendMessage(Messages.msg("protectionRemoved"));
	}
	
	@CommandHook("currentClaimInfo")
	public void currentClaimInfo(Player player, Claim claim) {
		claimInfo(player, claim);
	}
	
	@CommandHook("tool")
	public void giveTool(CommandSender sender, Player target) {
		ItemUtils.give(target, tool.getItem());
	}
	
}

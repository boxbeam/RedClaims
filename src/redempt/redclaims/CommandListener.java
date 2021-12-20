package redempt.redclaims;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import redempt.redclaims.RedClaims.RedClaimsConfig;
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
import redempt.redlib.misc.Task;
import redempt.redlib.misc.UserCache;
import redempt.redlib.region.CuboidRegion;
import redempt.redlib.region.Region;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CommandListener {
	
	private RedClaims plugin;
	private ClaimTool tool;
	
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
		
		ContextProvider<CuboidRegion> selectionProvider = new ContextProvider<>("selection", Messages.msg("noSelection"), c -> tool.getSelection(c.getUniqueId()));
		ContextProvider<Claim> currentClaimProvider = new ContextProvider<>("currentClaim", Messages.msg("notInClaim"), c -> ClaimMap.getClaim(c.getLocation()));
		ContextProvider<Subclaim> currentSubclaimProvider = currentClaimProvider.map("currentSubclaim", Messages.msg("notInSubclaim"), (s, c) -> c.getSubclaim(s.getLocation()));
		
		tool = new ClaimTool(plugin, new ItemStack(RedClaims.getInstance().config().claimTool()));
		
		new CommandParser(plugin.getResource("command.rdcml"))
				.setArgTypes(claimType, subclaimType, flagType, userType, rankType)
				.setContextProviders(selectionProvider, currentClaimProvider, currentSubclaimProvider)
				.parse().register("redclaims", this);
	}
	
	private CuboidRegion expandVert(CuboidRegion region) {
		Location start = region.getStart();
		Location end = region.getEnd();
		start.setY(end.getWorld().getMinHeight());
		end.setY(end.getWorld().getMaxHeight());
		return new CuboidRegion(start, end);
	}
	
	private String createClaim(Player sender, String name, CuboidRegion selection, boolean dummy) {
		if (name.length() > 16) {
			return Messages.msg("claimNameTooLong");
		}
		selection = expandVert(selection);
		try {
			Claim claim = plugin.getClaimStorage().createClaim(sender, name, selection, dummy);
			tool.clearSelection(sender.getUniqueId());
			if (!dummy) {
				Task.syncDelayed(() -> claim.visualize(sender, true), 1);
			}
			return null;
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
	}
	
	@CommandHook("createClaim")
	public void createClaim(Player sender, String name, CuboidRegion selection) {
		String err = createClaim(sender, name, selection, false);
		sender.sendMessage(err == null ? Messages.msg("claimCreated") : ChatColor.RED + err);
	}
	
	@CommandHook("tp")
	public void tp(Player player, Claim claim) {
		Region region = claim.getRegion();
		Location loc = region.getCenter();
		loc.setY(loc.getWorld().getHighestBlockYAt(loc));
		player.teleport(loc);
	}
	
	@CommandHook("resizeClaim")
	public void resizeClaim(Player sender, Claim claim, CuboidRegion selection) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		ClaimStorage storage = plugin.getClaimStorage();
		storage.unregister(claim);
		selection = expandVert(selection);
		String err = createClaim(sender, claim.getName(), selection, true);
		storage.register(claim);
		if (err != null) {
			sender.sendMessage(ChatColor.RED + err);
			return;
		}
		claim.setRegion(selection);
		sender.sendMessage(Messages.msg("claimResized"));
		Task.syncDelayed(() -> claim.visualize(sender, false), 1);
		tool.clearSelection(sender.getUniqueId());
	}
	
	@CommandHook("createSubclaim")
	public void createSubclaim(Player sender, Claim claim, String name, CuboidRegion selection) {
		if (name.length() > 16) {
			sender.sendMessage(Messages.msg("claimNameTooLong"));
			return;
		}
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		if (Arrays.stream(selection.getBlockDimensions()).anyMatch(i -> i < 3)) {
			sender.sendMessage(Messages.msg("subclaimTooSmall"));
			return;
		}
		Region intersect = selection.getIntersection(claim.getRegion());
		if (intersect == null || intersect.getBlockVolume() < selection.getBlockVolume()) {
			sender.sendMessage(Messages.msg("subclaimOutsideParent"));
			return;
		}
		try {
			claim.createSubclaim(name, selection);
			sender.sendMessage(Messages.msg("subclaimCreated"));
			tool.clearSelection(sender.getUniqueId());
			Task.syncDelayed(() -> claim.visualize(sender, true), 1);
		} catch (IllegalArgumentException e) {
			sender.sendMessage(Messages.msg("errorColor") + e.getMessage());
		}
	}
	
	@CommandHook("deleteSubclaim")
	public void deleteSubclaim(CommandSender sender, Claim claim, Subclaim subclaim) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		claim.removeSubclaim(subclaim);
		sender.sendMessage(Messages.msg("subclaimDeleted"));
	}
	
	@CommandHook("resizeSubclaim")
	public void resizeSubclaim(Player sender, Claim claim, Subclaim subclaim, CuboidRegion selection) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		if (IntStream.of(selection.getBlockDimensions()).anyMatch(i -> i < 3)) {
			sender.sendMessage(Messages.msg("subclaimTooSmall"));
			return;
		}
		if (!claim.isFullyContained(selection)) {
			sender.sendMessage(Messages.msg("subclaimOutsideParent"));
			return;
		}
		if (claim.getSubclaims().stream().filter(s -> !s.equals(subclaim)).anyMatch(s -> s.getRegion().overlaps(selection))) {
			sender.sendMessage(Messages.msg("subclaimOverlaps"));
			return;
		}
		subclaim.setRegion(selection);
		tool.clearSelection(sender.getUniqueId());
		Task.syncDelayed(() -> claim.visualize(sender, true), 1);
		sender.sendMessage(Messages.msg("subclaimResized"));
	}
	
	@CommandHook("addSubclaimFlag")
	public void addSubclaimFlags(CommandSender sender, Claim claim, Subclaim subclaim, ClaimFlag[] flags) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		subclaim.addFlag(flags);
		sender.sendMessage(Messages.msg("protectionAdded"));
	}
	
	@CommandHook("removeSubclaimFlag")
	public void removeSubclaimFlags(CommandSender sender, Claim claim, Subclaim subclaim, ClaimFlag[] flags) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		subclaim.removeFlag(flags);
		sender.sendMessage(Messages.msg("protectionRemoved"));
	}

	public void showClaimInfo(CommandSender sender, Claim claim, boolean showMembers) {
		sender.sendMessage(Messages.msg("claimInfoHeader").replace("%name%", claim.getFullName()));
		String primary = Messages.msg("primaryColor");
		String secondary = Messages.msg("secondaryColor");
		ClaimStorage storage = plugin.getClaimStorage();
		sender.sendMessage(Messages.msg("claimBlocks").replace("%blocks%", storage.getClaimBlocks(claim) + ""));
		sender.sendMessage(Messages.msg("claimFlags").replace("%flags%", claim.getFlags().stream().map(f -> secondary + f.getName()).collect(Collectors.joining(primary + ", "))));
		if (showMembers) {
			sender.sendMessage(Messages.msg("claimMembers").replace("%members%", claim.getAllMembers().entrySet().stream()
					.filter(e -> e.getValue() == ClaimRank.MEMBER)
					.map(e -> secondary + Bukkit.getOfflinePlayer(e.getKey()).getName())
					.collect(Collectors.joining(primary + ", "))));
			sender.sendMessage(Messages.msg("claimTrusted").replace("%members%", claim.getAllMembers().entrySet().stream()
					.filter(e -> e.getValue() == ClaimRank.TRUSTED)
					.map(e -> secondary + Bukkit.getOfflinePlayer(e.getKey()).getName())
					.collect(Collectors.joining(primary + ", "))));
		}
	}

	@CommandHook("claimInfo")
	public void claimInfo(CommandSender sender, Claim claim) {
		showClaimInfo(sender, claim, true);
	}

	@CommandHook("claimInfoCtx")
	public void claimInfo(Player sender, boolean sub, Claim claim) {
		if (sub) {
			Subclaim subclaim = claim.getSubclaim(sender.getLocation());
			if (subclaim == null) {
				sender.sendMessage(Messages.msg("notInSubclaim"));
				return;
			}
			claim = subclaim;
		}
		showClaimInfo(sender, claim, !sub);
	}

	@CommandHook("subclaimInfo")
	public void subclaimInfo(CommandSender sender, Claim claim, Subclaim subclaim) {
		showClaimInfo(sender, subclaim, false);
	}
	
	@CommandHook("deleteClaim")
	public void deleteClaim(CommandSender sender, Claim claim) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		claim.getRegion().getPlayers().forEach(p -> ClaimVisualizer.getDisplayer(p).clear());
		plugin.getClaimStorage().deleteClaim(claim);
		sender.sendMessage(Messages.msg("claimDeleted"));
	}
	
	@CommandHook("setRole")
	public void setRole(CommandSender sender, Claim claim, OfflinePlayer user, ClaimRank rank) {
		if (user.equals(claim.getOwner())) {
			sender.sendMessage(Messages.msg("cannotSetOwnerRole"));
			return;
		}
		if (!sender.hasPermission("redclaims.admin")) {
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
		}
		try {
			claim.setRank(user, rank);
			sender.sendMessage(Messages.msg("roleSet"));
		} catch (IllegalArgumentException e) {
			sender.sendMessage(Messages.msg("cannotTransferOwnership"));
		}
	}
	
	@CommandHook("addClaimFlag")
	public void addClaimFlag(CommandSender sender, Claim claim, ClaimFlag[] flags) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		claim.addFlag(flags);
		sender.sendMessage(Messages.msg("protectionAdded"));
	}
	
	@CommandHook("removeClaimFlag")
	public void removeClaimFlag(CommandSender sender, Claim claim, ClaimFlag[] flags) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		claim.removeFlag(flags);
		sender.sendMessage(Messages.msg("protectionRemoved"));
	}
	
	@CommandHook("currentClaimInfo")
	public void currentClaimInfo(Player player, Claim claim) {
		claimInfo(player, claim);
	}
	
	@CommandHook("renameClaim")
	public void renameClaim(CommandSender sender, Claim claim, String name) {
		if (!claim.hasAtLeast(sender, ClaimRank.OWNER)) {
			sender.sendMessage(Messages.msg("notOwner"));
			return;
		}
		try {
			plugin.getClaimStorage().renameClaim(claim, name);
			sender.sendMessage(Messages.msg("claimRenamed"));
		}catch (IllegalArgumentException e) {
			sender.sendMessage(Messages.msg("errorColor") + e.getMessage());
		}
	}
	
	@CommandHook("visualize")
	public void visualize(Player player, Claim claim) {
		claim.visualize(player, true);
	}
	
	@CommandHook("unvisualize")
	public void unvisualize(Player player) {
		ClaimVisualizer.getDisplayer(player).clear();
	}
	
	@CommandHook("budget")
	public void budget(CommandSender sender, Player player) {
		int budget = ClaimLimits.getClaimLimit(player);
		int used = plugin.getClaimStorage().getClaimedBlocks(player.getUniqueId());
		sender.sendMessage(Messages.msg("claimBudget").replace("%budget%", used + " / " + budget));
	}
	
	@CommandHook("setBudget")
	public void setBudget(CommandSender sender, Player player, int budget) {
		ClaimLimits.setClaimLimit(player, budget);
		sender.sendMessage(ChatColor.GREEN + "Claim limit set!");
	}
	
	@CommandHook("addBudget")
	public void addBudget(CommandSender sender, Player player, int budget) {
		ClaimLimits.addClaimLimit(player, budget);
		sender.sendMessage(ChatColor.GREEN + "Claim limit set!");
	}
	
	@CommandHook("bypass")
	public void bypass(Player player) {
		player.sendMessage(ChatColor.GREEN + "Claim bypass " + (ClaimBypass.toggle(player.getUniqueId()) ? "enabled" : "disabled") + "!");
	}
	
}

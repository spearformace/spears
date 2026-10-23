package com.spears;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SpearCommand implements CommandExecutor, TabCompleter {

    private static final String RED = "\u00a7c";
    private static final String GREEN = "\u00a7a";
    private static final String GRAY = "\u00a77";

    private final SpearsPlugin plugin;

    public SpearCommand(SpearsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spears.admin")) {
            sender.sendMessage(RED + "You don't have permission.");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give":
                give(sender, args);
                break;
            case "book":
                book(sender, args);
                break;
            case "lunge":
                lunge(sender, args);
                break;
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage(GREEN + "Spears config reloaded.");
                break;
            default:
                usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        s.sendMessage(GRAY + "/spear give <player> <diamond|netherite> [lunge 0-3]");
        s.sendMessage(GRAY + "/spear book <player> <lunge 1-3>");
        s.sendMessage(GRAY + "/spear lunge <0-3>   (sets Lunge on the spear you hold)");
        s.sendMessage(GRAY + "/spear reload");
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(RED + "Player not found.");
            return;
        }
        SpearItems.Tier tier = SpearItems.Tier.byId(args[2].toLowerCase(Locale.ROOT));
        if (tier == null) {
            sender.sendMessage(RED + "Tier must be diamond or netherite.");
            return;
        }
        int lunge = args.length > 3 ? parseLevel(args[3], 0) : 0;
        if (lunge < 0) {
            sender.sendMessage(RED + "Lunge must be 0-3.");
            return;
        }
        giveItem(target, SpearItems.create(tier, lunge));
        sender.sendMessage(GREEN + "Gave " + target.getName() + " a " + tier.id + " spear"
                + (lunge > 0 ? " (Lunge " + SpearItems.roman(lunge) + ")" : "") + ".");
    }

    private void book(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(RED + "Player not found.");
            return;
        }
        int level = parseLevel(args[2], 1);
        if (level < 1) {
            sender.sendMessage(RED + "Lunge must be 1-3.");
            return;
        }
        giveItem(target, SpearItems.createLungeBook(level));
        sender.sendMessage(GREEN + "Gave " + target.getName() + " a Lunge " + SpearItems.roman(level)
                + " book. Combine it with a spear on an anvil.");
    }

    private void lunge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(RED + "Players only.");
            return;
        }
        Player p = (Player) sender;
        if (args.length < 2) {
            usage(sender);
            return;
        }
        int level = parseLevel(args[1], 0);
        if (level < 0) {
            sender.sendMessage(RED + "Lunge must be 0-3.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (SpearItems.getTier(hand) == null) {
            sender.sendMessage(RED + "Hold one of these spears first.");
            return;
        }
        SpearItems.setLunge(hand, level);
        p.getInventory().setItemInMainHand(hand);
        sender.sendMessage(GREEN + (level == 0 ? "Lunge removed." : "Lunge set to " + SpearItems.roman(level) + "."));
    }

    private int parseLevel(String text, int min) {
        try {
            int v = Integer.parseInt(text);
            return (v < min || v > 3) ? -1 : v;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void giveItem(Player target, ItemStack item) {
        for (ItemStack leftover : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("spears.admin")) return out;
        if (args.length == 1) {
            out.addAll(Arrays.asList("give", "book", "lunge", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("book"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lunge")) {
            out.addAll(Arrays.asList("0", "1", "2", "3"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.addAll(Arrays.asList("diamond", "netherite"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("book")) {
            out.addAll(Arrays.asList("1", "2", "3"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            out.addAll(Arrays.asList("0", "1", "2", "3"));
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}

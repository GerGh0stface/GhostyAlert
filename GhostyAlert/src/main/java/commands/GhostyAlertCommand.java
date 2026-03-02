package commands;

import gui.AlertGUI;
import managers.IPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GhostyAlertCommand implements CommandExecutor, TabCompleter {

    private final IPlugin plugin;
    private final AlertGUI gui;

    public GhostyAlertCommand(IPlugin plugin) {
        this.plugin = plugin;
        this.gui = new AlertGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = plugin.getLang().getPrefix();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix + plugin.getLang().get("messages.only-players"));
                return true;
            }
            if (!player.hasPermission("ghostyalert.use")) {
                player.sendMessage(prefix + plugin.getLang().get("messages.no-permission"));
                return true;
            }
            gui.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("ghostyalert.reload")) {
                    sender.sendMessage(prefix + plugin.getLang().get("messages.no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(prefix + plugin.getLang().get("messages.reload-success"));
            }
            case "help" -> sendHelp(sender, prefix);
            default -> sender.sendMessage(prefix + plugin.getLang().get("messages.unknown-command"));
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String prefix) {
        sender.sendMessage(plugin.getLang().get("help.separator"));
        sender.sendMessage(prefix + plugin.getLang().get("help.title"));
        sender.sendMessage(plugin.getLang().get("help.cmd-gui"));
        sender.sendMessage(plugin.getLang().get("help.cmd-reload"));
        sender.sendMessage(plugin.getLang().get("help.cmd-help"));
        sender.sendMessage(plugin.getLang().get("help.aliases"));
        sender.sendMessage(plugin.getLang().get("help.separator"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("reload", "help"));
            completions.removeIf(s -> !s.startsWith(args[0].toLowerCase()));
            return completions;
        }
        return new ArrayList<>();
    }
}

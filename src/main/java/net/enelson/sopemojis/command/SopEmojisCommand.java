package net.enelson.sopemojis.command;

import net.enelson.sopemojis.SopEmojis;
import net.enelson.sopemojis.chat.RenderedMessage;
import net.enelson.sopemojis.model.EmojiDefinition;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class SopEmojisCommand implements CommandExecutor, TabCompleter {

    private final SopEmojis plugin;

    public SopEmojisCommand(SopEmojis plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.color("&e/sopemojis reload"));
            sender.sendMessage(plugin.color("&e/sopemojis generatepack"));
            sender.sendMessage(plugin.color("&e/sopemojis list"));
            sender.sendMessage(plugin.color("&e/sopemojis debugrender <text>"));
            return true;
        }

        String subcommand = args[0].toLowerCase();
        if ("reload".equals(subcommand)) {
            if (!hasAdmin(sender)) {
                sender.sendMessage(plugin.color(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(plugin.color(plugin.getConfig().getString("messages.reloaded", "&aSopEmojis reloaded.")));
            return true;
        }

        if ("generatepack".equals(subcommand)) {
            if (!hasAdmin(sender)) {
                sender.sendMessage(plugin.color(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                return true;
            }
            try {
                Path file = plugin.generatePack();
                String raw = plugin.getConfig().getString("messages.pack-generated", "&aResource pack generated: &f{file}");
                sender.sendMessage(plugin.color(raw.replace("{file}", file.toString())));
            } catch (IOException exception) {
                String raw = plugin.getConfig().getString("messages.pack-generation-failed", "&cFailed to generate resource pack: &f{error}");
                sender.sendMessage(plugin.color(raw.replace("{error}", exception.getMessage())));
                plugin.getLogger().severe("Failed to generate resource pack: " + exception.getMessage());
            }
            return true;
        }

        if ("list".equals(subcommand)) {
            Collection<EmojiDefinition> visible = plugin.registry().visibleFor(sender);
            List<String> triggers = new ArrayList<String>();
            for (EmojiDefinition emoji : visible) {
                triggers.add(emoji.getTrigger());
            }
            String items = triggers.isEmpty() ? "-" : join(triggers, ", ");
            String raw = plugin.getConfig().getString("messages.list-header", "&6Available emojis: &f{items}");
            sender.sendMessage(plugin.color(raw.replace("{items}", items)));

            if (plugin.registry().countUnsupportedSpritesFor(sender) > 0 && (plugin.modernComponentBridge() == null || !plugin.modernComponentBridge().isSupported())) {
                sender.sendMessage(plugin.color(plugin.getConfig().getString("messages.sprite-warning", "&eSPRITE emojis are ignored in 1.16.5 chat rendering.")));
            }
            return true;
        }

        if ("debugrender".equals(subcommand)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.color(plugin.getConfig().getString("messages.only-player", "&cPlayers only.")));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(plugin.color("&e/sopemojis debugrender <text>"));
                return true;
            }

            String text = join(Arrays.asList(Arrays.copyOfRange(args, 1, args.length)), " ");
            if (!plugin.sendDebugRender(sender, text)) {
                Player player = (Player) sender;
                RenderedMessage rendered = plugin.chatRenderer().render(player, text);
                player.sendMessage(rendered.getText());
            }
            return true;
        }

        sender.sendMessage(plugin.color(plugin.getConfig().getString("messages.unknown-subcommand", "&cUnknown subcommand.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        List<String> all = Arrays.asList("reload", "generatepack", "list", "debugrender");
        List<String> result = new ArrayList<String>();
        String prefix = args[0].toLowerCase();
        for (String value : all) {
            if (value.startsWith(prefix)) {
                result.add(value);
            }
        }
        return result;
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("sopemojis.admin") || sender.hasPermission("semojis.admin");
    }

    private String join(Collection<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (!first) {
                builder.append(separator);
            }
            builder.append(value);
            first = false;
        }
        return builder.toString();
    }
}
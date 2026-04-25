package net.enelson.sopemojis.registry;

import net.enelson.sopemojis.model.EmojiDefinition;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class EmojiRegistry {

    private final List<EmojiDefinition> all;

    public EmojiRegistry(List<EmojiDefinition> all) {
        this.all = Collections.unmodifiableList(new ArrayList<EmojiDefinition>(all));
    }

    public List<EmojiDefinition> all() {
        return all;
    }

    public boolean canUse(CommandSender sender, EmojiDefinition emoji) {
        return sender.hasPermission("sopemojis.admin")
                || sender.hasPermission("semojis.admin")
                || sender.hasPermission("sopemojis.use.*")
                || sender.hasPermission("semojis.use.*")
                || sender.hasPermission(emoji.getPermission());
    }

    public Collection<EmojiDefinition> visibleFor(CommandSender sender) {
        List<EmojiDefinition> result = new ArrayList<EmojiDefinition>();
        for (EmojiDefinition emoji : all) {
            if (emoji.isEnabled() && canUse(sender, emoji)) {
                result.add(emoji);
            }
        }
        return result;
    }

    public List<EmojiDefinition> chatVisibleFor(CommandSender sender) {
        List<EmojiDefinition> result = new ArrayList<EmojiDefinition>();
        for (EmojiDefinition emoji : all) {
            if (emoji.isEnabled() && emoji.isFont() && canUse(sender, emoji)) {
                result.add(emoji);
            }
        }
        return result;
    }

    public int countUnsupportedSprites() {
        int count = 0;
        for (EmojiDefinition emoji : all) {
            if (emoji.isEnabled() && emoji.isSprite()) {
                count++;
            }
        }
        return count;
    }

    public int countUnsupportedSpritesFor(CommandSender sender) {
        int count = 0;
        for (EmojiDefinition emoji : all) {
            if (emoji.isEnabled() && emoji.isSprite() && canUse(sender, emoji)) {
                count++;
            }
        }
        return count;
    }
}
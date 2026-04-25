package net.enelson.sopemojis.chat;

import net.enelson.sopemojis.model.EmojiDefinition;
import net.enelson.sopemojis.registry.EmojiRegistry;
import org.bukkit.command.CommandSender;

public final class ChatEmojiRenderer {

    private final EmojiRegistry registry;

    public ChatEmojiRenderer(EmojiRegistry registry) {
        this.registry = registry;
    }

    public RenderedMessage render(CommandSender sender, String input) {
        if (input == null || input.isEmpty()) {
            return new RenderedMessage("", false);
        }

        String result = input;
        boolean changed = false;
        for (EmojiDefinition emoji : registry.chatVisibleFor(sender)) {
            if (result.contains(emoji.getTrigger())) {
                result = result.replace(emoji.getTrigger(), emoji.getUnicodeChar());
                changed = true;
            }
        }

        return new RenderedMessage(result, changed);
    }
}
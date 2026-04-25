package net.enelson.sopemojis.chat;

import net.enelson.sopemojis.SopEmojis;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class PlayerChatEmojiListener implements Listener {

    private final SopEmojis plugin;

    public PlayerChatEmojiListener(SopEmojis plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        RenderedMessage rendered = plugin.chatRenderer().render(event.getPlayer(), event.getMessage());
        if (rendered.isChanged()) {
            event.setMessage(rendered.getText());
        }
    }
}
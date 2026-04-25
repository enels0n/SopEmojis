package net.enelson.sopemojis.chat;

import net.enelson.sopemojis.SopEmojis;
import net.enelson.sopemojis.model.EmojiDefinition;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ModernChatCompletionSupport implements Listener {

    private final SopEmojis plugin;
    private final LuckPerms luckPerms;
    private final Method completionsMethod;
    private EventSubscription<UserDataRecalculateEvent> subscription;

    private ModernChatCompletionSupport(SopEmojis plugin, LuckPerms luckPerms, Method completionsMethod) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.completionsMethod = completionsMethod;
    }

    public static ModernChatCompletionSupport create(SopEmojis plugin) {
        Method method = findCompletionsMethod();
        if (method == null) {
            return null;
        }

        LuckPerms luckPerms = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            return null;
        }
        return new ModernChatCompletionSupport(plugin, luckPerms, method);
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.subscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            UUID uniqueId = event.getUser().getUniqueId();
            Player player = Bukkit.getPlayer(uniqueId);
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        refresh(player);
                    }
                });
            }
        });
        refreshAll();
    }

    public void unregister() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    public void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refresh(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                refresh(event.getPlayer());
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private void refresh(Player player) {
        List<String> completions = new ArrayList<String>();
        Collection<EmojiDefinition> visible = plugin.registry().visibleFor(player);
        for (EmojiDefinition emoji : visible) {
            completions.add(emoji.getTrigger());
        }
        invoke(player, completions);
    }

    private void clear(Player player) {
        invoke(player, new ArrayList<String>());
    }

    private void invoke(Player player, Collection<String> values) {
        try {
            completionsMethod.invoke(player, values);
        } catch (Throwable ignored) {
            // ignore runtime mismatch
        }
    }

    private static Method findCompletionsMethod() {
        for (Method method : Player.class.getMethods()) {
            if (!"setCustomChatCompletions".equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (Collection.class.isAssignableFrom(parameterType)) {
                return method;
            }
        }
        return null;
    }
}
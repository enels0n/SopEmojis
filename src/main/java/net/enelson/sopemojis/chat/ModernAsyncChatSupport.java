package net.enelson.sopemojis.chat;

import net.enelson.sopemojis.SopEmojis;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@SuppressWarnings("unchecked")
public final class ModernAsyncChatSupport implements Listener {

    private final SopEmojis plugin;
    private final ModernComponentBridge bridge;
    private boolean registered;

    public ModernAsyncChatSupport(SopEmojis plugin, ModernComponentBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public boolean register() {
        if (registered || bridge == null || !bridge.isSupported()) {
            return false;
        }

        try {
            final Class<?> asyncChatEventClass = Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            final Class<?> chatRendererClass = Class.forName("io.papermc.paper.chat.ChatRenderer");
            PluginManager pluginManager = plugin.getServer().getPluginManager();
            pluginManager.registerEvent((Class<? extends Event>) asyncChatEventClass, this, EventPriority.HIGHEST, new EventExecutor() {
                @Override
                public void execute(Listener listener, Event event) throws EventException {
                    handle(event, chatRendererClass);
                }
            }, plugin, true);
            registered = true;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void unregister() {
        registered = false;
    }

    private void handle(Object event, final Class<?> chatRendererClass) {
        if (!registered || !plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        try {
            final Object originalRenderer = invokeNoArgs(event, "renderer");
            if (originalRenderer == null) {
                return;
            }

            Object proxy = Proxy.newProxyInstance(chatRendererClass.getClassLoader(), new Class<?>[]{chatRendererClass}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxyObject, Method method, Object[] args) throws Throwable {
                    if ("render".equals(method.getName()) && args != null && args.length == 4) {
                        Object rendered = method.invoke(originalRenderer, args);
                        CommandSender source = args[0] instanceof CommandSender ? (CommandSender) args[0] : null;
                        return bridge.render(source, rendered);
                    }
                    return method.invoke(originalRenderer, args);
                }
            });

            invokeOneArg(event, "renderer", chatRendererClass, proxy);
        } catch (Throwable ignored) {
            // runtime API mismatch, keep default renderer
        }
    }

    private Object invokeNoArgs(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    private void invokeOneArg(Object target, String name, Class<?> parameterType, Object arg) throws Exception {
        Method method = target.getClass().getMethod(name, parameterType);
        method.invoke(target, arg);
    }
}
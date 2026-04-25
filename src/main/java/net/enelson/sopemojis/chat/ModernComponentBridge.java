package net.enelson.sopemojis.chat;

import net.enelson.sopemojis.model.EmojiDefinition;
import net.enelson.sopemojis.registry.EmojiRegistry;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class ModernComponentBridge {

    private final EmojiRegistry registry;
    private final Logger logger;
    private final boolean debugSpriteRendering;
    private final Class<?> componentClass;
    private final Class<?> textReplacementConfigClass;
    private final Class<?> objectContentsClass;
    private final Class<?> keyClass;

    public ModernComponentBridge(EmojiRegistry registry, Logger logger, boolean debugSpriteRendering) {
        this.registry = registry;
        this.logger = logger;
        this.debugSpriteRendering = debugSpriteRendering;
        this.componentClass = tryClass("net.kyori.adventure.text.Component");
        this.textReplacementConfigClass = tryClass("net.kyori.adventure.text.TextReplacementConfig");
        this.objectContentsClass = tryClass("net.kyori.adventure.text.object.ObjectContents");
        this.keyClass = tryClass("net.kyori.adventure.key.Key");
    }

    public boolean isSupported() {
        return componentClass != null && textReplacementConfigClass != null;
    }

    public Object render(CommandSender sender, Object inputComponent) {
        if (!isSupported() || inputComponent == null || !componentClass.isInstance(inputComponent)) {
            return inputComponent;
        }

        Object result = inputComponent;
        List<EmojiDefinition> visible = new ArrayList<EmojiDefinition>(registry.visibleFor(sender));
        for (EmojiDefinition emoji : visible) {
            Object replacement = emoji.isFont() ? createTextComponent(emoji.getUnicodeChar()) : createSpriteComponent(emoji);
            if (replacement == null) {
                debug("Skipping emoji '" + emoji.getId() + "' because replacement component could not be created.");
                continue;
            }

            try {
                result = invokeFlexible(result, "replaceText", new Class<?>[]{String.class, replacement.getClass()}, new Object[]{emoji.getTrigger(), replacement});
                continue;
            } catch (Throwable ignored) {
                // fall back to builder path for older runtime APIs
            }

            try {
                Object builder = invokeStatic(textReplacementConfigClass, "builder", new Class<?>[0], new Object[0]);
                invokeFlexible(builder, "matchLiteral", emoji.getTrigger());
                invokeFlexible(builder, "replacement", replacement);
                Object config = invokeFlexible(builder, "build");
                result = invokeFlexible(result, "replaceText", config);
            } catch (Throwable throwable) {
                debug("Failed to replace trigger '" + emoji.getTrigger() + "' for emoji '" + emoji.getId() + "': " + throwable.getClass().getSimpleName());
                // keep original component when runtime API diverges
            }
        }
        return result;
    }

    public boolean sendRenderedMessage(Player player, CommandSender sender, String text) {
        if (!isSupported()) {
            return false;
        }

        Object base = createTextComponent(text);
        if (base == null) {
            return false;
        }

        Object rendered = render(sender, base);
        try {
            invokeFlexible(player, "sendMessage", rendered);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object createTextComponent(String text) {
        try {
            return invokeStatic(componentClass, "text", new Class<?>[]{String.class}, new Object[]{text});
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object createSpriteComponent(EmojiDefinition emoji) {
        if (objectContentsClass == null || keyClass == null) {
            debug("Adventure sprite classes are unavailable for emoji '" + emoji.getId() + "'.");
            return null;
        }

        try {
            Object spriteKey = invokeStatic(keyClass, "key", new Class<?>[]{String.class, String.class},
                    new Object[]{"minecraft", "emoji/" + emoji.getSpriteName()});
            Object atlasKey = invokeStatic(keyClass, "key", new Class<?>[]{String.class, String.class},
                    new Object[]{"minecraft", "blocks"});

            Object spriteContents = null;
            for (Method method : objectContentsClass.getMethods()) {
                if (!"sprite".equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                try {
                    if (parameterTypes.length == 3) {
                        spriteContents = method.invoke(null, spriteKey, Float.valueOf(1.0F), Float.valueOf(1.0F));
                        break;
                    }
                    if (parameterTypes.length == 2) {
                        spriteContents = method.invoke(null, atlasKey, spriteKey);
                        break;
                    }
                    if (parameterTypes.length == 1) {
                        spriteContents = method.invoke(null, spriteKey);
                        break;
                    }
                } catch (Throwable ignored) {
                    // try next overload
                }
            }
            if (spriteContents == null) {
                debug("No usable ObjectContents.sprite overload for emoji '" + emoji.getId() + "'.");
                return null;
            }

            return invokeStatic(componentClass, "object", new Class<?>[]{objectContentsClass}, new Object[]{spriteContents});
        } catch (Throwable throwable) {
            debug("Failed to build sprite component for emoji '" + emoji.getId() + "': " + throwable.getClass().getSimpleName());
            return null;
        }
    }

    private Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object invokeStatic(Class<?> owner, String name, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method method = owner.getMethod(name, parameterTypes);
        return method.invoke(null, args);
    }

    private Object invokeFlexible(Object target, String methodName, Object arg) throws Exception {
        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            if (!methodName.equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            Class<?> parameterType = method.getParameterTypes()[0];
            if (arg == null || parameterType.isInstance(arg) || parameterType.isAssignableFrom(arg.getClass())) {
                return method.invoke(target, arg);
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private Object invokeFlexible(Object target, String methodName) throws Exception {
        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            if (methodName.equals(method.getName()) && method.getParameterTypes().length == 0) {
                return method.invoke(target);
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    private Object invokeFlexible(Object target, String methodName, Class<?>[] preferredTypes, Object[] args) throws Exception {
        try {
            Method preferred = target.getClass().getMethod(methodName, preferredTypes);
            return preferred.invoke(target, args);
        } catch (NoSuchMethodException ignored) {
            Method[] methods = target.getClass().getMethods();
            for (Method method : methods) {
                if (!methodName.equals(method.getName()) || method.getParameterTypes().length != args.length) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean match = true;
                for (int i = 0; i < parameterTypes.length; i++) {
                    Object arg = args[i];
                    if (arg != null && !parameterTypes[i].isInstance(arg) && !parameterTypes[i].isAssignableFrom(arg.getClass())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return method.invoke(target, args);
                }
            }
            throw new NoSuchMethodException(methodName);
        }
    }

    private void debug(String message) {
        if (debugSpriteRendering && logger != null) {
            logger.info("[sprite-debug] " + message);
        }
    }
}

package net.enelson.sopemojis;

import net.enelson.sopemojis.chat.ChatEmojiRenderer;
import net.enelson.sopemojis.chat.ModernAsyncChatSupport;
import net.enelson.sopemojis.chat.ModernChatCompletionSupport;
import net.enelson.sopemojis.chat.ModernComponentBridge;
import net.enelson.sopemojis.chat.PlayerChatEmojiListener;
import net.enelson.sopemojis.chat.RenderedMessage;
import net.enelson.sopemojis.command.SopEmojisCommand;
import net.enelson.sopemojis.config.EmojiConfigLoader;
import net.enelson.sopemojis.model.EmojiDefinition;
import net.enelson.sopemojis.pack.ResourcePackGenerator;
import net.enelson.sopemojis.registry.EmojiRegistry;
import net.enelson.sopemojis.storage.EmojiDatabaseStorage;
import net.enelson.sopli.lib.SopLib;
import net.enelson.sopli.lib.database.DatabaseConfig;
import net.enelson.sopli.lib.database.SopDatabase;
import net.enelson.sopli.lib.text.TextUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

public final class SopEmojis extends JavaPlugin {

    private final EmojiConfigLoader loader = new EmojiConfigLoader();
    private final ResourcePackGenerator generator = new ResourcePackGenerator();

    private EmojiRegistry registry;
    private ChatEmojiRenderer chatRenderer;
    private TextUtils textUtils;
    private ModernComponentBridge modernComponentBridge;
    private ModernAsyncChatSupport modernAsyncChatSupport;
    private ModernChatCompletionSupport modernChatCompletionSupport;
    private SopDatabase database;
    private EmojiDatabaseStorage databaseStorage;
    private boolean databaseEnabled;
    private BukkitTask databaseRefreshTask;

    @Override
    public void onEnable() {
        this.textUtils = resolveTextUtils();

        saveDefaultConfig();
        saveResourceIfMissing("emojis.yml");
        ensureTextureFolder();
        reloadPlugin();

        SopEmojisCommand command = new SopEmojisCommand(this);
        PluginCommand pluginCommand = getCommand("sopemojis");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new PlayerChatEmojiListener(this), this);

        this.modernAsyncChatSupport = new ModernAsyncChatSupport(this, modernComponentBridge);
        if (this.modernAsyncChatSupport.register()) {
            getLogger().info("Modern async chat renderer enabled.");
        }

        this.modernChatCompletionSupport = ModernChatCompletionSupport.create(this);
        if (this.modernChatCompletionSupport != null) {
            this.modernChatCompletionSupport.register();
            getLogger().info("Modern chat completions enabled.");
        }

        getLogger().info("SopEmojis enabled with " + registry.all().size() + " emoji definitions.");
    }

    @Override
    public void onDisable() {
        if (modernChatCompletionSupport != null) {
            modernChatCompletionSupport.unregister();
            modernChatCompletionSupport = null;
        }
        if (modernAsyncChatSupport != null) {
            modernAsyncChatSupport.unregister();
            modernAsyncChatSupport = null;
        }
        stopDatabaseRefreshTask();
        closeDatabase();
    }

    public void reloadPlugin() {
        reloadConfig();
        ensureTextureFolder();
        setupDatabase();

        int defaultHeight = getConfig().getInt("pack.font-default-height", 10);
        int defaultAscent = getConfig().getInt("pack.font-default-ascent", 8);
        List<EmojiDefinition> emojis;
        if (this.databaseEnabled && this.databaseStorage != null) {
            try {
                if (getConfig().getBoolean("database.sync-from-config", false)) {
                    List<EmojiDefinition> configEmojis = loader.load(
                            getDataFolder().toPath().resolve("emojis.yml").toFile(),
                            defaultHeight,
                            defaultAscent
                    );
                    this.databaseStorage.syncFromConfig(configEmojis);
                }
                emojis = this.databaseStorage.load(defaultHeight, defaultAscent);
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, "Failed to load emojis from database. Falling back to emojis.yml.", exception);
                emojis = loader.load(
                        getDataFolder().toPath().resolve("emojis.yml").toFile(),
                        defaultHeight,
                        defaultAscent
                );
            }
        } else {
            emojis = loader.load(
                    getDataFolder().toPath().resolve("emojis.yml").toFile(),
                    defaultHeight,
                    defaultAscent
            );
        }

        applyLoadedEmojis(emojis);
        restartDatabaseRefreshTask();
    }

    public EmojiRegistry registry() {
        return registry;
    }

    public ChatEmojiRenderer chatRenderer() {
        return chatRenderer;
    }

    public ModernComponentBridge modernComponentBridge() {
        return modernComponentBridge;
    }

    public Path generatePack() throws IOException {
        return generator.generate(getDataFolder().toPath(), getConfig(), registry.all());
    }

    public String color(String text) {
        return textUtils.color(text);
    }

    public TextUtils textUtils() {
        return textUtils;
    }

    public String resolveFontEmojis(CommandSender sender, String text) {
        RenderedMessage rendered = this.chatRenderer.render(sender, text);
        return rendered == null ? text : rendered.getText();
    }

    public String resolveFontEmoji(CommandSender sender, String emojiId) {
        if (sender == null || emojiId == null || emojiId.isEmpty() || this.registry == null) {
            return "";
        }
        for (EmojiDefinition emoji : this.registry.all()) {
            if (!emojiId.equalsIgnoreCase(emoji.getId())) {
                continue;
            }
            if (!emoji.isEnabled() || !emoji.isFont() || !this.registry.canUse(sender, emoji)) {
                return "";
            }
            return emoji.getUnicodeChar() == null ? "" : emoji.getUnicodeChar();
        }
        return "";
    }

    public boolean sendDebugRender(CommandSender sender, String text) {
        if (!(sender instanceof Player)) {
            return false;
        }
        if (modernComponentBridge == null || !modernComponentBridge.isSupported()) {
            return false;
        }
        return modernComponentBridge.sendRenderedMessage((Player) sender, sender, text);
    }

    private TextUtils resolveTextUtils() {
        SopLib sopLib = SopLib.getInstance();
        if (sopLib != null && sopLib.getTextUtils() != null) {
            return sopLib.getTextUtils();
        }
        return new TextUtils();
    }


    private void applyLoadedEmojis(List<EmojiDefinition> emojis) {
        this.registry = new EmojiRegistry(emojis);
        this.chatRenderer = new ChatEmojiRenderer(registry);
        this.modernComponentBridge = new ModernComponentBridge(
                registry,
                getLogger(),
                getConfig().getBoolean("debug.sprite-rendering", false)
        );

        if (this.modernChatCompletionSupport != null) {
            this.modernChatCompletionSupport.refreshAll();
        }

        if (registry.countUnsupportedSprites() > 0 && !modernComponentBridge.isSupported()) {
            getLogger().warning("SPRITE emoji definitions are present but the current runtime does not support modern component rendering.");
        }
    }

    private void restartDatabaseRefreshTask() {
        stopDatabaseRefreshTask();
        if (!this.databaseEnabled || this.databaseStorage == null) {
            return;
        }

        int seconds = getConfig().getInt("database.auto-refresh-seconds", 0);
        if (seconds <= 0) {
            return;
        }
        if (seconds < 30) {
            seconds = 30;
        }

        long periodTicks = seconds * 20L;
        this.databaseRefreshTask = getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                refreshFromDatabaseTask();
            }
        }, periodTicks, periodTicks);
    }

    private void stopDatabaseRefreshTask() {
        if (this.databaseRefreshTask != null) {
            this.databaseRefreshTask.cancel();
            this.databaseRefreshTask = null;
        }
    }

    private void refreshFromDatabaseTask() {
        if (!this.databaseEnabled || this.databaseStorage == null) {
            return;
        }

        final int defaultHeight = getConfig().getInt("pack.font-default-height", 10);
        final int defaultAscent = getConfig().getInt("pack.font-default-ascent", 8);
        try {
            final List<EmojiDefinition> emojis = this.databaseStorage.load(defaultHeight, defaultAscent);
            getServer().getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    applyLoadedEmojis(emojis);
                }
            });
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to auto-refresh emojis from database.", exception);
        }
    }
    private void setupDatabase() {
        this.databaseEnabled = false;
        this.databaseStorage = null;
        closeDatabase();
        if (!getConfig().getBoolean("database.enabled", false)) {
            return;
        }

        SopLib sopLib = SopLib.getInstance();
        if (sopLib == null || sopLib.getDatabaseService() == null) {
            getLogger().warning("SopLib DatabaseService is unavailable. Falling back to emojis.yml.");
            return;
        }

        try {
            DatabaseConfig config = DatabaseConfig.mysql(
                    getConfig().getString("database.host", "127.0.0.1"),
                    getConfig().getInt("database.port", 3306),
                    getConfig().getString("database.database", "minecraft")
            )
                    .credentials(
                            getConfig().getString("database.username", "root"),
                            getConfig().getString("database.password", "")
                    )
                    .poolName(getConfig().getString("database.pool-name", "SopEmojis"))
                    .maximumPoolSize(getConfig().getInt("database.maximum-pool-size", 10))
                    .minimumIdle(getConfig().getInt("database.minimum-idle", 2))
                    .connectionTimeout(getConfig().getLong("database.connection-timeout", 30000L))
                    .idleTimeout(getConfig().getLong("database.idle-timeout", 600000L))
                    .maxLifetime(getConfig().getLong("database.max-lifetime", 1800000L))
                    .build();

            this.database = sopLib.getDatabaseService().createDatabase(config);
            this.databaseStorage = new EmojiDatabaseStorage(this, this.database, getConfig().getString("database.table-prefix", "sopemojis_"));
            this.databaseStorage.initialize();
            this.databaseEnabled = true;
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize SopEmojis database mode. Falling back to emojis.yml.", exception);
            this.databaseEnabled = false;
            this.databaseStorage = null;
            closeDatabase();
        }
    }

    private void closeDatabase() {
        if (this.database != null) {
            try {
                this.database.close();
            } catch (Exception ignored) {
            }
            this.database = null;
        }
    }

    private void saveResourceIfMissing(String name) {
        Path path = getDataFolder().toPath().resolve(name);
        if (Files.notExists(path)) {
            saveResource(name, false);
        }
    }

    private void ensureTextureFolder() {
        try {
            Files.createDirectories(getDataFolder().toPath().resolve("emoji-textures"));
        } catch (IOException exception) {
            throw new RuntimeException("Unable to create emoji-textures directory", exception);
        }
    }
}

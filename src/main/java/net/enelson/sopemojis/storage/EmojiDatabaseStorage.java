package net.enelson.sopemojis.storage;

import net.enelson.sopemojis.SopEmojis;
import net.enelson.sopemojis.config.EmojiConfigLoader;
import net.enelson.sopemojis.model.EmojiDefinition;
import net.enelson.sopemojis.model.EmojiType;
import net.enelson.sopli.lib.database.SopDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EmojiDatabaseStorage {

    private final SopEmojis plugin;
    private final SopDatabase database;
    private final String table;
    private final EmojiConfigLoader loader = new EmojiConfigLoader();

    public EmojiDatabaseStorage(SopEmojis plugin, SopDatabase database, String tablePrefix) {
        this.plugin = plugin;
        this.database = database;
        this.table = normalizePrefix(tablePrefix) + "emojis";
    }

    public void initialize() throws SQLException {
        this.database.execute(
                "CREATE TABLE IF NOT EXISTS `" + this.table + "` ("
                        + "`id` VARCHAR(64) NOT NULL,"
                        + "`type` VARCHAR(16) NOT NULL,"
                        + "`trigger` VARCHAR(128) NOT NULL,"
                        + "`unicode_char` VARCHAR(32) NULL,"
                        + "`sprite_name` VARCHAR(128) NULL,"
                        + "`texture_file` VARCHAR(255) NOT NULL,"
                        + "`permission` VARCHAR(255) NOT NULL,"
                        + "`enabled` TINYINT(1) NOT NULL DEFAULT 1,"
                        + "`height` INT NOT NULL,"
                        + "`ascent` INT NOT NULL,"
                        + "PRIMARY KEY (`id`)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        );
    }

    public List<EmojiDefinition> load(int defaultHeight, int defaultAscent) throws SQLException {
        return this.database.withConnection(new net.enelson.sopli.lib.database.SqlFunction<Connection, List<EmojiDefinition>>() {
            @Override
            public List<EmojiDefinition> apply(Connection connection) throws SQLException {
                PreparedStatement statement = null;
                ResultSet resultSet = null;
                List<EmojiDefinition> emojis = new ArrayList<EmojiDefinition>();
                try {
                    statement = connection.prepareStatement(
                            "SELECT `id`, `type`, `trigger`, `unicode_char`, `sprite_name`, `texture_file`, `permission`, `enabled`, `height`, `ascent` FROM `" + table + "`"
                    );
                    resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        EmojiType type = EmojiType.valueOf(resultSet.getString("type").toUpperCase(Locale.ROOT));
                        emojis.add(new EmojiDefinition(
                                resultSet.getString("id"),
                                type,
                                resultSet.getString("trigger"),
                                valueOrDefault(resultSet.getString("unicode_char"), ""),
                                valueOrDefault(resultSet.getString("sprite_name"), resultSet.getString("id")),
                                valueOrDefault(resultSet.getString("texture_file"), ""),
                                valueOrDefault(resultSet.getString("permission"), "sopemojis.use." + resultSet.getString("id")),
                                resultSet.getBoolean("enabled"),
                                fallbackInt(resultSet.getInt("height"), defaultHeight),
                                fallbackInt(resultSet.getInt("ascent"), defaultAscent)
                        ));
                    }
                } finally {
                    closeQuietly(resultSet);
                    closeQuietly(statement);
                }
                return loader.validateAndSort(emojis);
            }
        });
    }

    public void syncFromConfig(final List<EmojiDefinition> emojis) throws SQLException {
        final List<EmojiDefinition> prepared = this.loader.validateAndSort(emojis);
        this.database.transaction(new net.enelson.sopli.lib.database.SqlFunction<Connection, Void>() {
            @Override
            public Void apply(Connection connection) throws SQLException {
                PreparedStatement clear = null;
                PreparedStatement insert = null;
                try {
                    clear = connection.prepareStatement("DELETE FROM `" + table + "`");
                    clear.executeUpdate();
                    insert = connection.prepareStatement(
                            "INSERT INTO `" + table + "` (`id`, `type`, `trigger`, `unicode_char`, `sprite_name`, `texture_file`, `permission`, `enabled`, `height`, `ascent`) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    );
                    for (EmojiDefinition emoji : prepared) {
                        insert.setString(1, emoji.getId());
                        insert.setString(2, emoji.getType().name());
                        insert.setString(3, emoji.getTrigger());
                        insert.setString(4, emptyToNull(emoji.getUnicodeChar()));
                        insert.setString(5, emptyToNull(emoji.getSpriteName()));
                        insert.setString(6, emoji.getTextureFile());
                        insert.setString(7, emoji.getPermission());
                        insert.setBoolean(8, emoji.isEnabled());
                        insert.setInt(9, emoji.getHeight());
                        insert.setInt(10, emoji.getAscent());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                } finally {
                    closeQuietly(insert);
                    closeQuietly(clear);
                }
                return null;
            }
        });
        this.plugin.getLogger().info("Synchronized " + prepared.size() + " emojis from emojis.yml to MySQL.");
    }

    private String normalizePrefix(String prefix) {
        return (prefix == null || prefix.trim().isEmpty()) ? "sopemojis_" : prefix;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private int fallbackInt(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
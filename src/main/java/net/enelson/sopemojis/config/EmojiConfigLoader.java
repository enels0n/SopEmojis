package net.enelson.sopemojis.config;

import net.enelson.sopemojis.model.EmojiDefinition;
import net.enelson.sopemojis.model.EmojiType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class EmojiConfigLoader {

    public List<EmojiDefinition> load(File file, int defaultHeight, int defaultAscent) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("emojis");
        if (root == null) {
            return Collections.emptyList();
        }

        List<EmojiDefinition> result = new ArrayList<EmojiDefinition>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            EmojiType type = EmojiType.valueOf(stringOrDefault(section.getString("type"), "FONT").toUpperCase(Locale.ROOT));
            String trigger = stringOrDefault(section.getString("trigger"), "");
            String unicodeChar = stringOrDefault(section.getString("char"), "");
            String sprite = stringOrDefault(section.getString("sprite"), id);
            String texture = stringOrDefault(section.getString("texture"), "");
            String permission = stringOrDefault(section.getString("permission"), "sopemojis.use." + id);
            boolean enabled = section.getBoolean("enabled", true);
            int height = section.getInt("height", defaultHeight);
            int ascent = section.getInt("ascent", defaultAscent);

            result.add(new EmojiDefinition(id, type, trigger, unicodeChar, sprite, texture, permission, enabled, height, ascent));
        }

        return validateAndSort(result);
    }

    public List<EmojiDefinition> validateAndSort(List<EmojiDefinition> emojis) {
        if (emojis == null || emojis.isEmpty()) {
            return Collections.emptyList();
        }

        List<EmojiDefinition> result = new ArrayList<EmojiDefinition>();
        Set<String> triggers = new HashSet<String>();
        Set<String> chars = new HashSet<String>();
        Set<String> sprites = new HashSet<String>();

        for (EmojiDefinition emoji : emojis) {
            String id = emoji.getId();
            String trigger = stringOrDefault(emoji.getTrigger(), "");
            String unicodeChar = stringOrDefault(emoji.getUnicodeChar(), "");
            String sprite = stringOrDefault(emoji.getSpriteName(), id);
            String texture = stringOrDefault(emoji.getTextureFile(), "");

            if (isBlank(trigger)) {
                throw new IllegalStateException("Emoji '" + id + "' has empty trigger");
            }
            if (isBlank(texture)) {
                throw new IllegalStateException("Emoji '" + id + "' has empty texture");
            }
            if (!triggers.add(trigger)) {
                throw new IllegalStateException("Duplicate trigger: " + trigger);
            }

            if (emoji.getType() == EmojiType.FONT) {
                if (isBlank(unicodeChar)) {
                    throw new IllegalStateException("FONT emoji '" + id + "' has empty char");
                }
                if (!chars.add(unicodeChar)) {
                    throw new IllegalStateException("Duplicate unicode char: " + unicodeChar);
                }
            }

            if (emoji.getType() == EmojiType.SPRITE) {
                if (isBlank(sprite)) {
                    throw new IllegalStateException("SPRITE emoji '" + id + "' has empty sprite");
                }
                if (!sprites.add(sprite)) {
                    throw new IllegalStateException("Duplicate sprite: " + sprite);
                }
            }

            result.add(emoji);
        }

        Collections.sort(result, new Comparator<EmojiDefinition>() {
            @Override
            public int compare(EmojiDefinition first, EmojiDefinition second) {
                return Integer.compare(second.getTrigger().length(), first.getTrigger().length());
            }
        });
        return Collections.unmodifiableList(new ArrayList<EmojiDefinition>(result));
    }

    private String stringOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
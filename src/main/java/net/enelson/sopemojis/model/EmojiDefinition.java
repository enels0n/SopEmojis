package net.enelson.sopemojis.model;

public final class EmojiDefinition {

    private final String id;
    private final EmojiType type;
    private final String trigger;
    private final String unicodeChar;
    private final String spriteName;
    private final String textureFile;
    private final String permission;
    private final boolean enabled;
    private final int height;
    private final int ascent;

    public EmojiDefinition(String id,
                           EmojiType type,
                           String trigger,
                           String unicodeChar,
                           String spriteName,
                           String textureFile,
                           String permission,
                           boolean enabled,
                           int height,
                           int ascent) {
        this.id = id;
        this.type = type;
        this.trigger = trigger;
        this.unicodeChar = unicodeChar;
        this.spriteName = spriteName;
        this.textureFile = textureFile;
        this.permission = permission;
        this.enabled = enabled;
        this.height = height;
        this.ascent = ascent;
    }

    public String getId() {
        return id;
    }

    public EmojiType getType() {
        return type;
    }

    public String getTrigger() {
        return trigger;
    }

    public String getUnicodeChar() {
        return unicodeChar;
    }

    public String getSpriteName() {
        return spriteName;
    }

    public String getTextureFile() {
        return textureFile;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getHeight() {
        return height;
    }

    public int getAscent() {
        return ascent;
    }

    public boolean isFont() {
        return type == EmojiType.FONT;
    }

    public boolean isSprite() {
        return type == EmojiType.SPRITE;
    }
}
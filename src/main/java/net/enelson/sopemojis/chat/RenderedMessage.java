package net.enelson.sopemojis.chat;

public final class RenderedMessage {

    private final String text;
    private final boolean changed;

    public RenderedMessage(String text, boolean changed) {
        this.text = text;
        this.changed = changed;
    }

    public String getText() {
        return text;
    }

    public boolean isChanged() {
        return changed;
    }
}
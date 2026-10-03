package com.xiaowu.game.starveil.render.engine;

/**
 * 文本段 - 带有样式的文本片段
 */
public class TextSegment {
    public final String text;
    public final ColorDef color;
    public final TextStyle style;
    public final String annotationText;
    public final String annotationPosition;

    public TextSegment(String text, ColorDef color) {
        this(text, color, null, null, null);
    }

    public TextSegment(String text, ColorDef color, TextStyle style) {
        this(text, color, style, null, null);
    }

    public TextSegment(String text, ColorDef color, TextStyle style, String annotationText, String annotationPosition) {
        this.text = text;
        this.color = color;
        this.style = style;
        this.annotationText = annotationText;
        this.annotationPosition = annotationPosition;
    }

    public boolean hasAnnotation() {
        return annotationText != null && !annotationText.isEmpty();
    }

    @Override
    public String toString() {
        return "TextSegment{text='" + text + "', color=" + color + "}";
    }
}

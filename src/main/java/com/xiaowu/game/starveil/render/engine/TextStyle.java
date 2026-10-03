package com.xiaowu.game.starveil.render.engine;

/**
 * 文本样式类 - 抽象文本渲染样式
 */
public class TextStyle {
    private final String fontFamily;
    private final double fontSize;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final boolean strikethrough;
    private final ColorDef fillColor;
    private final double letterSpacing;
    private final double lineHeight;

    public TextStyle(String fontFamily, double fontSize, boolean bold, boolean italic,
                     ColorDef fillColor, double letterSpacing, double lineHeight) {
        this(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, false, false);
    }

    public TextStyle(String fontFamily, double fontSize, boolean bold, boolean italic,
                     ColorDef fillColor, double letterSpacing, double lineHeight,
                     boolean underline, boolean strikethrough) {
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strikethrough = strikethrough;
        this.fillColor = fillColor;
        this.letterSpacing = letterSpacing;
        this.lineHeight = lineHeight;
    }

    /**
     * 创建简单文本样式
     */
    public static TextStyle simple(double fontSize, ColorDef fillColor) {
        return new TextStyle("System", fontSize, false, false, fillColor, 0, 1.5);
    }

    /**
     * 创建粗体样式
     */
    public static TextStyle bold(double fontSize, ColorDef fillColor) {
        return new TextStyle("System", fontSize, true, false, fillColor, 0, 1.5);
    }

    /**
     * 创建标题样式
     */
    public static TextStyle title(double fontSize, ColorDef fillColor) {
        return new TextStyle("System", fontSize, true, false, fillColor, 0, 1.5);
    }

    public TextStyle withUnderline(boolean underline) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, underline, this.strikethrough);
    }

    public TextStyle withStrikethrough(boolean strikethrough) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, this.underline, strikethrough);
    }

    public TextStyle withBold(boolean bold) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, underline, strikethrough);
    }

    public TextStyle withItalic(boolean italic) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, underline, strikethrough);
    }

    public TextStyle withColor(ColorDef fillColor) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, underline, strikethrough);
    }

    public TextStyle withFontFamily(String fontFamily) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, underline, strikethrough);
    }

    public TextStyle withFontSize(double fontSize) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, letterSpacing, lineHeight, underline, strikethrough);
    }

    // Getters
    public String getFontFamily() { return fontFamily; }
    public double getFontSize() { return fontSize; }
    public boolean isBold() { return bold; }
    public boolean isItalic() { return italic; }
    public boolean isUnderline() { return underline; }
    public boolean isStrikethrough() { return strikethrough; }
    public ColorDef getFillColor() { return fillColor; }
    public double getLetterSpacing() { return letterSpacing; }
    public double getLineHeight() { return lineHeight; }
}

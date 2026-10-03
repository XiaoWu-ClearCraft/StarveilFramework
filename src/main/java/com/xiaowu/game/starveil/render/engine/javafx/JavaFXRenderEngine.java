package com.xiaowu.game.starveil.render.engine.javafx;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.render.engine.*;
import com.xiaowu.game.starveil.render.engine.handles.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaFX 渲染引擎实现 - 封装所有 JavaFX 特定的渲染逻辑
 * 
 * 这个类是 RenderEngine 接口的 JavaFX 实现,包含了:
 * - 文本渲染(逐字动画、颜色解析)
 * - UI 组件渲染(进度条、按钮、标签、输入框)
 * - 特效渲染(圆形、线条、渐变)
 * - 动画系统(淡入淡出、缩放、顺序/并行动画)
 * - 摄像机系统(带死区和平滑)
 */
public class JavaFXRenderEngine implements RenderEngine {

    // ==================== 引擎状态 ====================
    private StackPane rootContainer;
    private StackPane effectContainer;
    private Pane overlay;

    private final DoubleProperty viewportWidth = new SimpleDoubleProperty(1200);
    private final DoubleProperty viewportHeight = new SimpleDoubleProperty(675);

    // 摄像机参数
    private final double cameraDeadZoneRadius = 100;
    private final double cameraSmoothing = 0.3;
    private double targetCameraMoveX = 0;
    private double targetCameraMoveY = 0;

    // 动画管理
    private final List<Timeline> activeTimelines = new ArrayList<>();
    private final List<Animation> activeAnimations = new ArrayList<>();
    private final List<EventHandler<KeyEvent>> activeKeyHandlers = new ArrayList<>();
    private final List<EventHandler<javafx.scene.input.MouseEvent>> activeMouseHandlers = new ArrayList<>();

    // 字体缓存
    private final Map<String, Font> fontCache = new HashMap<>();
    private Font defaultFont;

    // 颜色映射
    private static final Map<String, String> COLOR_NAME_MAP = new HashMap<>();
    static {
        COLOR_NAME_MAP.put("red", "#FF6B6B");
        COLOR_NAME_MAP.put("blue", "#6495ED");
        COLOR_NAME_MAP.put("green", "#4ADE80");
        COLOR_NAME_MAP.put("yellow", "#FFA500");
        COLOR_NAME_MAP.put("purple", "#9370DB");
        COLOR_NAME_MAP.put("cyan", "#00CED1");
        COLOR_NAME_MAP.put("orange", "#FFA500");
        COLOR_NAME_MAP.put("pink", "#FF69B4");
        COLOR_NAME_MAP.put("gray", "#A9A9A9");
        COLOR_NAME_MAP.put("white", "#FFFFFF");
        COLOR_NAME_MAP.put("black", "#000000");
        COLOR_NAME_MAP.put("reset", "#FFE4E1");
    }

    // ==================== 初始化与生命周期 ====================

    @Override
    public void initialize(double viewportWidth, double viewportHeight) {
        this.viewportWidth.set(viewportWidth);
        this.viewportHeight.set(viewportHeight);

        // 创建根容器
        rootContainer = new StackPane();
        rootContainer.setAlignment(Pos.CENTER);

        // 创建特效容器
        effectContainer = new StackPane();
        effectContainer.setAlignment(Pos.CENTER);
        effectContainer.setMouseTransparent(true);
        effectContainer.setPickOnBounds(false);
        effectContainer.setVisible(false);
        effectContainer.setStyle("-fx-background-color: transparent;");

        // 创建覆盖层
        overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.4);");
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);

        effectContainer.getChildren().add(overlay);
        rootContainer.getChildren().add(effectContainer);

        // 加载默认字体
        loadDefaultFont();
    }

    @Override
    public void dispose() {
        stopAllAnimations();
        clearAllHandlers();
        fontCache.clear();
    }

    @Override
    public Object getRootContainer() {
        return rootContainer;
    }

    @Override
    public Object getEffectContainer() {
        return effectContainer;
    }

    // ==================== 字体管理 ====================

    private void loadDefaultFont() {
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream("starveil:fonts/xiaolai-sc-regular.ttf")) {
            if (is != null) {
                defaultFont = Font.loadFont(is, 16);
                fontCache.put("default", defaultFont);
            } else {
                defaultFont = Font.font("System", 16);
            }
        } catch (Exception e) {
            defaultFont = Font.font("System", 16);
        }
    }

    private Font getFont(String family, double size) {
        String key = family + "_" + size;
        return fontCache.computeIfAbsent(key, k -> {
            try (java.io.InputStream is = ResourceResolver.getResourceAsStream("starveil:fonts/" + family.toLowerCase() + ".ttf")) {
                if (is != null) {
                    return Font.loadFont(is, size);
                }
            } catch (Exception ignored) {
            }
            return Font.font(family != null ? family : "System", size);
        });
    }

    // ==================== 视口与摄像机 ====================

    @Override
    public void setViewportSize(double width, double height) {
        viewportWidth.set(width);
        viewportHeight.set(height);
    }

    @Override
    public double getViewportWidth() {
        return viewportWidth.get();
    }

    @Override
    public double getViewportHeight() {
        return viewportHeight.get();
    }

    @Override
    public void updateCamera(Object worldNode, double playerCenterX, double playerCenterY, double deltaTime) {
        if (!(worldNode instanceof Pane worldMap)) return;

        double playerScreenX = playerCenterX + worldMap.getLayoutX();
        double playerScreenY = playerCenterY + worldMap.getLayoutY();

        double screenCenterX = viewportWidth.get() / 2;
        double screenCenterY = viewportHeight.get() / 2;

        double dx = playerScreenX - screenCenterX;
        double dy = playerScreenY - screenCenterY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double newTargetCameraMoveX = 0;
        double newTargetCameraMoveY = 0;

        if (distance > cameraDeadZoneRadius) {
            double unitX = dx / distance;
            double unitY = dy / distance;
            double targetX = screenCenterX + unitX * cameraDeadZoneRadius;
            double targetY = screenCenterY + unitY * cameraDeadZoneRadius;
            newTargetCameraMoveX = targetX - playerScreenX;
            newTargetCameraMoveY = targetY - playerScreenY;
        }

        double timeScale = deltaTime * 60.0;
        double adjustedSmoothing = 1.0 - Math.pow(1.0 - cameraSmoothing, timeScale);

        targetCameraMoveX += (newTargetCameraMoveX - targetCameraMoveX) * adjustedSmoothing;
        targetCameraMoveY += (newTargetCameraMoveY - targetCameraMoveY) * adjustedSmoothing;

        if (Math.abs(targetCameraMoveX) < 0.5) targetCameraMoveX = 0;
        if (Math.abs(targetCameraMoveY) < 0.5) targetCameraMoveY = 0;

        if (targetCameraMoveX != 0 || targetCameraMoveY != 0) {
            double newWorldX = worldMap.getLayoutX() + targetCameraMoveX;
            double newWorldY = worldMap.getLayoutY() + targetCameraMoveY;

            double[] clampedPos = clampCameraPosition(newWorldX, newWorldY, worldMap);
            worldMap.setLayoutX(clampedPos[0]);
            worldMap.setLayoutY(clampedPos[1]);

            targetCameraMoveX = 0;
            targetCameraMoveY = 0;
        }
    }

    @Override
    public void centerCamera(Object worldNode, double playerCenterX, double playerCenterY) {
        if (!(worldNode instanceof Pane worldMap)) return;

        double worldOffsetX = viewportWidth.get() / 2 - playerCenterX;
        double worldOffsetY = viewportHeight.get() / 2 - playerCenterY;

        double[] clampedPos = clampCameraPosition(worldOffsetX, worldOffsetY, worldMap);
        worldMap.setLayoutX(clampedPos[0]);
        worldMap.setLayoutY(clampedPos[1]);

        targetCameraMoveX = 0;
        targetCameraMoveY = 0;
    }

    private double[] clampCameraPosition(double worldX, double worldY, Pane worldMap) {
        double clampedX = worldX;
        double clampedY = worldY;

        int mapWidth = (int) worldMap.getPrefWidth();
        int mapHeight = (int) worldMap.getPrefHeight();
        double viewportW = viewportWidth.get();
        double viewportH = viewportHeight.get();

        if (mapWidth < viewportW) {
            clampedX = (viewportW - mapWidth) / 2;
        } else {
            double maxOffsetX = 0;
            double minOffsetX = viewportW - mapWidth;
            if (clampedX > maxOffsetX) clampedX = maxOffsetX;
            if (clampedX < minOffsetX) clampedX = minOffsetX;
        }

        if (mapHeight < viewportH) {
            clampedY = (viewportH - mapHeight) / 2;
        } else {
            double maxOffsetY = 0;
            double minOffsetY = viewportH - mapHeight;
            if (clampedY > maxOffsetY) clampedY = maxOffsetY;
            if (clampedY < minOffsetY) clampedY = minOffsetY;
        }

        return new double[]{clampedX, clampedY};
    }

    // ==================== 文本渲染 ====================

    // ---------- 格式状态（用于解析嵌套标签）----------

    private static class FormatState {
        String color = "#FFE4E1";
        String fontFamily;
        Double fontSize;
        boolean bold;
        boolean italic;
        boolean underline;
        boolean strikethrough;
        String annotationText;
        String annotationPosition = "top";

        FormatState copy() {
            FormatState s = new FormatState();
            s.color = this.color;
            s.fontFamily = this.fontFamily;
            s.fontSize = this.fontSize;
            s.bold = this.bold;
            s.italic = this.italic;
            s.underline = this.underline;
            s.strikethrough = this.strikethrough;
            s.annotationText = this.annotationText;
            s.annotationPosition = this.annotationPosition;
            return s;
        }
    }

    @Override
    public List<TextSegment> parseColoredText(String text) {
        List<TextSegment> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        java.util.ArrayDeque<FormatState> stack = new java.util.ArrayDeque<>();
        stack.push(new FormatState());

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '<') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    // 闭合标签 </tag>
                    int close = text.indexOf('>', i);
                    if (close == -1) { i++; continue; }
                    if (stack.size() > 1) stack.pop();
                    i = close + 1;
                } else {
                    // 开标签 <tag ...>
                    int close = text.indexOf('>', i);
                    if (close == -1) { i++; continue; }
                    String tagContent = text.substring(i + 1, close).trim();
                    int nameEnd = indexOfWhitespaceOrEnd(tagContent, 0);
                    String tagName = tagContent.substring(0, nameEnd);

                    java.util.Map<String, String> attrs = new java.util.HashMap<>();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w+)=\"([^\"]*)\"").matcher(tagContent);
                    while (m.find()) {
                        attrs.put(m.group(1).toLowerCase(), m.group(2));
                    }

                    FormatState newState = stack.peek().copy();
                    applyTag(newState, tagName, attrs);
                    stack.push(newState);
                    i = close + 1;
                }
            } else if (c == '{') {
                // 旧式颜色标签 {color} （修改当前作用域的颜色）
                int close = text.indexOf('}', i);
                if (close != -1) {
                    String tag = text.substring(i + 1, close);
                    FormatState state = stack.peek();
                    if (tag.startsWith("#") && isValidHexColor(tag)) {
                        state.color = tag;
                    } else if (COLOR_NAME_MAP.containsKey(tag)) {
                        state.color = COLOR_NAME_MAP.get(tag);
                    }
                    i = close + 1;
                } else {
                    i++;
                }
            } else {
                // 收集普通文本
                int next = findNextSpecialChar(text, i);
                String segText = text.substring(i, next);
                if (!segText.isEmpty()) {
                    FormatState state = stack.peek();
                    ColorDef color = state.color.startsWith("#")
                            ? ColorDef.hex(state.color)
                            : ColorDef.name(state.color);
                    TextStyle style = buildStyleFromState(state);

                    if (state.annotationText != null) {
                        result.add(new TextSegment(segText, color, style,
                                state.annotationText, state.annotationPosition));
                    } else {
                        result.add(new TextSegment(segText, color, style));
                    }
                }
                i = next;
            }
        }
        return result;
    }

    private int findNextSpecialChar(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<' || c == '{') return i;
        }
        return text.length();
    }

    private int indexOfWhitespaceOrEnd(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return s.length();
    }

    private void applyTag(FormatState state, String tagName, java.util.Map<String, String> attrs) {
        String name = tagName.toLowerCase();
        switch (name) {
            case "b": state.bold = true; break;
            case "i": state.italic = true; break;
            case "u": state.underline = true; break;
            case "s": case "strike": case "del": state.strikethrough = true; break;
            case "label":
                state.annotationText = attrs.get("text");
                String loc = attrs.get("location");
                if ("bottom".equalsIgnoreCase(loc)) {
                    state.annotationPosition = "bottom";
                } else {
                    state.annotationPosition = "top";
                }
                break;
            case "font":
                String src = attrs.get("src");
                state.fontFamily = (src == null || src.isEmpty()) ? null : src;
                String sizeStr = attrs.get("size");
                if (sizeStr != null) {
                    try {
                        state.fontSize = Double.parseDouble(sizeStr);
                    } catch (NumberFormatException ignored) {}
                }
                break;
            default:
                if (name.startsWith("#") && isValidHexColor(name)) {
                    state.color = name;
                } else if (COLOR_NAME_MAP.containsKey(name)) {
                    state.color = COLOR_NAME_MAP.get(name);
                }
                break;
        }
    }

    private TextStyle buildStyleFromState(FormatState state) {
        String fontFamily = state.fontFamily != null ? state.fontFamily : "System";
        double fontSize = state.fontSize != null ? state.fontSize : 16;
        ColorDef fillColor = state.color.startsWith("#")
                ? ColorDef.hex(state.color)
                : ColorDef.name(state.color);
        return new TextStyle(fontFamily, fontSize, state.bold, state.italic,
                fillColor, 0, 1.5, state.underline, state.strikethrough);
    }

    // ---------- 实际渲染 ----------

    private boolean isValidHexColor(String hex) {
        return hex != null && hex.matches("^#([A-Fa-f0-9]{3}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$");
    }

    private String formatHexColor(String hex) {
        if (!isValidHexColor(hex)) return "#FFE4E1";
        if (hex.length() == 4) {
            char r = hex.charAt(1), g = hex.charAt(2), b = hex.charAt(3);
            return "#" + r + r + g + g + b + b;
        }
        return hex;
    }

    private Node createNodeForSegment(TextSegment seg) {
        if (seg.hasAnnotation()) {
            return createRubyNode(seg);
        }
        Text t = new Text(seg.text);
        String clr = seg.color.isHex() ? formatHexColor(seg.color.getValue()) : seg.color.getValue();
        t.setStyle(buildTextStyle(seg.style, clr));
        return t;
    }

    private Node createRubyNode(TextSegment seg) {
        return createRubyNode(seg, seg.text);
    }

    private Node createRubyNode(TextSegment seg, String displayText) {
        double fontSize = seg.style != null ? seg.style.getFontSize() : 16;
        String clr = seg.color.isHex() ? formatHexColor(seg.color.getValue()) : seg.color.getValue();
        Text main = new Text(displayText);
        main.setStyle(buildTextStyle(seg.style, clr));
        javafx.scene.control.Label annotation = new javafx.scene.control.Label(seg.annotationText);
        annotation.setStyle("-fx-font-size: 10px; -fx-text-fill: #A0A0A0; -fx-font-family: 'System';" +
                "-fx-padding: 0; -fx-label-padding: 0;");
        VBox vb = new VBox(0);
        vb.setAlignment(Pos.CENTER);
        if ("bottom".equals(seg.annotationPosition)) {
            vb.getChildren().addAll(main, annotation);
        } else {
            vb.getChildren().addAll(annotation, main);
        }
        return vb;
    }

    @Override
    public Object createTextContainer(TextStyle style) {
        TextFlow textFlow = new TextFlow();
        textFlow.setMaxWidth(800);
        textFlow.setPadding(new Insets(10, 0, 0, 0));
        return textFlow;
    }

    @Override
    public void setTextContent(Object textContainer, List<TextSegment> segments) {
        if (!(textContainer instanceof TextFlow flow)) return;
        flow.getChildren().clear();
        for (TextSegment seg : segments) {
            flow.getChildren().add(createNodeForSegment(seg));
        }
    }

    @Override
    public void appendTextContent(Object textContainer, List<TextSegment> segments) {
        if (!(textContainer instanceof TextFlow flow)) return;
        for (TextSegment seg : segments) {
            flow.getChildren().add(createNodeForSegment(seg));
        }
    }

    @Override
    public void setPartialTextContent(Object textContainer, List<TextSegment> segments, int charCount) {
        if (!(textContainer instanceof TextFlow flow)) return;
        flow.getChildren().clear();
        int count = 0;
        for (TextSegment seg : segments) {
            int segLen = seg.text.length();
            if (count + segLen <= charCount) {
                flow.getChildren().add(createNodeForSegment(seg));
            } else if (count < charCount) {
                int partial = charCount - count;
                if (seg.hasAnnotation()) {
                    TextSegment partialSeg = new TextSegment(
                            seg.text.substring(0, partial), seg.color, seg.style,
                            seg.annotationText, seg.annotationPosition);
                    flow.getChildren().add(createRubyNode(partialSeg));
                } else {
                    Text t = new Text(seg.text.substring(0, partial));
                    String clr = seg.color.isHex() ? formatHexColor(seg.color.getValue()) : seg.color.getValue();
                    t.setStyle(buildTextStyle(seg.style, clr));
                    flow.getChildren().add(t);
                }
            }
            count += segLen;
        }
    }

    private String buildTextStyle(TextStyle style, String fillColor) {
        String fontFamily = style != null ? style.getFontFamily() : "System";
        double fontSize = style != null ? style.getFontSize() : 16;
        boolean bold = style != null && style.isBold();
        boolean italic = style != null && style.isItalic();
        boolean underline = style != null && style.isUnderline();
        boolean strikethrough = style != null && style.isStrikethrough();
        double lineHeight = style != null ? style.getLineHeight() : 1.5;

        return String.format("-fx-font-family: '%s'; -fx-fill: %s; -fx-font-size: %.0fpx; -fx-font-weight: %s; -fx-font-style: %s; -fx-underline: %s; -fx-strikethrough: %s; -fx-line-spacing: %.1f;",
                fontFamily, fillColor, fontSize,
                bold ? "bold" : "normal",
                italic ? "italic" : "normal",
                underline ? "true" : "false",
                strikethrough ? "true" : "false",
                lineHeight);
    }

    @Override
    public TextAnimationHandle startTypewriterAnimation(String message, Object textContainer,
                                                         Object promptContainer, Runnable onComplete) {
        return startTypewriterAnimation(message, textContainer, promptContainer, onComplete, 0, 30);
    }

    @Override
    public TextAnimationHandle startTypewriterAnimation(String message, Object textContainer,
                                                         Object promptContainer, Runnable onComplete,
                                                         int startCharOffset, int charIntervalMs) {
        if (!(textContainer instanceof TextFlow flow)) return null;

        List<TextSegment> segs = parseColoredText(message);
        int total = segs.stream().mapToInt(s -> s.text.length()).sum();

        AtomicInteger currentCharIndex = new AtomicInteger(startCharOffset);
        AtomicReference<Timeline> animation = new AtomicReference<>();

        Timeline timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);

        int animCharCount = total - startCharOffset;
        for (int i = 0; i <= animCharCount; i++) {
            final int displayCount = startCharOffset + i;
            KeyFrame kf = new KeyFrame(Duration.millis((long) charIntervalMs * (i + 1)), e -> {
                if (currentCharIndex.get() < total) {
                    setPartialTextContent(flow, segs, currentCharIndex.get());
                    currentCharIndex.incrementAndGet();
                } else {
                    setPartialTextContent(flow, segs, total);
                    animation.get().stop();
                    showPrompt(promptContainer);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
            timeline.getKeyFrames().add(kf);
        }

        animation.set(timeline);
        timeline.play();

        return new TextAnimationHandle(timeline, flow, message);
    }

    @Override
    public void skipTypewriterAnimation(TextAnimationHandle handle) {
        if (handle == null) return;
        Timeline timeline = (Timeline) handle.getNativeHandle();
        if (timeline != null && timeline.getStatus() == Animation.Status.RUNNING) {
            timeline.stop();
            List<TextSegment> segs = parseColoredText(handle.getFullMessage());
            setTextContent(handle.getTextContainer(), segs);
        }
    }

    @Override
    public void stopTypewriterAnimation(TextAnimationHandle handle) {
        if (handle != null && handle.getNativeHandle() instanceof Timeline timeline) {
            timeline.stop();
        }
    }

    private void showPrompt(Object promptContainer) {
        if (promptContainer instanceof HBox prompt) {
            if (!prompt.getChildren().isEmpty()) {
                if (prompt.getOpacity() >= 0.99) return;
                FadeTransition ft = new FadeTransition(Duration.millis(300), prompt);
                ft.setFromValue(0);
                ft.setToValue(1);
                ft.play();
            }
        }
    }

    // ==================== UI 组件渲染 ====================

    @Override
    public ProgressBarHandle createProgressBar(double width, double height, ColorDef accentColor, ColorDef backgroundColor) {
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefSize(width, height);
        progressBar.setStyle(String.format("-fx-accent: %s; -fx-background-color: %s;",
                accentColor.getValue(), backgroundColor != null ? backgroundColor.getValue() : "rgba(255, 255, 255, 0.2)"));
        return new ProgressBarHandle(progressBar);
    }

    @Override
    public void setProgressBarProgress(ProgressBarHandle handle, double progress) {
        if (handle.getNativeHandle() instanceof ProgressBar progressBar) {
            progressBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        }
    }

    @Override
    public void setProgressBarStyle(ProgressBarHandle handle, ColorDef accentColor) {
        if (handle.getNativeHandle() instanceof ProgressBar progressBar) {
            progressBar.setStyle(String.format("-fx-accent: %s; -fx-background-insets: 0; -fx-padding: 0;",
                    accentColor.getValue()));
        }
    }

    @Override
    public ButtonHandle createButton(String text, TextStyle style) {
        Button button = new Button(text);
        if (style != null) {
            // 设置文本颜色
            button.setTextFill(javafx.scene.paint.Color.web(style.getFillColor().getValue()));
            // 设置字体
            button.setFont(new javafx.scene.text.Font(style.getFontSize()));
            button.setStyle(buildButtonStyle(style));
        }
        return new ButtonHandle(button);
    }

    @Override
    public ButtonHandle createButton(String text, TextStyle style, ColorDef backgroundColor, ColorDef hoverColor, double cornerRadius) {
        Button button = new Button(text);
        if (style != null) {
            button.setTextFill(javafx.scene.paint.Color.web(style.getFillColor().getValue()));
            button.setFont(new javafx.scene.text.Font(style.getFontSize()));
        }

        String bgStyle = String.format("-fx-background-color: %s; -fx-background-radius: %.0f;",
                backgroundColor != null ? backgroundColor.getValue() : "transparent", cornerRadius);
        button.setStyle(bgStyle);

        // 悬停效果
        if (hoverColor != null) {
            final String hoverStyle = String.format("-fx-background-color: %s; -fx-background-radius: %.0f;",
                    hoverColor.getValue(), cornerRadius);
            final String defaultStyle = bgStyle;
            button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
            button.setOnMouseExited(e -> button.setStyle(defaultStyle));
        }

        return new ButtonHandle(button);
    }

    private String buildButtonStyle(TextStyle style) {
        return String.format("-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: %.0fpx; " +
                        "-fx-font-weight: %s; -fx-padding: 12 25; -fx-background-radius: 12;",
                style.getFillColor().getValue(), style.getFontSize(),
                style.isBold() ? "bold" : "normal");
    }

    @Override
    public void setButtonAction(ButtonHandle handle, Runnable action) {
        if (handle.getNativeHandle() instanceof Button button) {
            button.setOnAction(e -> action.run());
        }
    }

    @Override
    public LabelHandle createLabel(String text, TextStyle style) {
        Label label = new Label(text);
        if (style != null) {
            // 设置文本颜色
            label.setTextFill(javafx.scene.paint.Color.web(style.getFillColor().getValue()));
            
            // 加载字体
            if (style.getFontFamily() != null && !style.getFontFamily().isEmpty()) {
                try {
                    javafx.scene.text.Font font = javafx.scene.text.Font.loadFont(
                        ResourceResolver.getResourceAsStream(style.getFontFamily()), style.getFontSize());
                    if (font != null) {
                        label.setFont(font);
                    } else {
                        // 字体加载失败，使用系统字体
                        label.setFont(new javafx.scene.text.Font(style.getFontSize()));
                    }
                } catch (Exception e) {
                    label.setFont(new javafx.scene.text.Font(style.getFontSize()));
                }
            } else {
                label.setFont(new javafx.scene.text.Font(style.getFontSize()));
            }
        }
        return new LabelHandle(label);
    }

    private String buildLabelStyle(TextStyle style) {
        return String.format("-fx-text-fill: %s;", style.getFillColor().getValue());
    }

    @Override
    public void setLabelText(LabelHandle handle, String text) {
        if (handle.getNativeHandle() instanceof Label label) {
            label.setText(text);
        }
    }

    @Override
    public TextFieldHandle createTextField(TextStyle style, String promptText) {
        TextField textField = new TextField();
        textField.setPromptText(promptText);
        if (style != null) {
            textField.setStyle(buildTextFieldStyle(style));
        }
        return new TextFieldHandle(textField);
    }

    private String buildTextFieldStyle(TextStyle style) {
        return String.format("-fx-background-color: #2d1f2f; -fx-text-fill: %s; -fx-font-size: %.0fpx; " +
                        "-fx-border-color: #3d2d3d; -fx-border-radius: 12; -fx-border-width: 2; " +
                        "-fx-background-radius: 12; -fx-padding: 15;",
                style.getFillColor().getValue(), style.getFontSize());
    }

    @Override
    public String getTextFieldContent(TextFieldHandle handle) {
        if (handle.getNativeHandle() instanceof TextField textField) {
            return textField.getText().trim();
        }
        return "";
    }

    // ==================== 面板与布局 ====================

    @Override
    public Object createVBox(double spacing, double padding) {
        VBox vBox = new VBox(spacing);
        vBox.setAlignment(Pos.CENTER);
        vBox.setPadding(new Insets(padding));
        return vBox;
    }

    @Override
    public Object createHBox(double spacing, double padding) {
        HBox hBox = new HBox(spacing);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setPadding(new Insets(padding));
        return hBox;
    }

    @Override
    public Object createStackPane() {
        return new StackPane();
    }

    @Override
    public void addChild(Object parent, Object child) {
        if (parent instanceof Pane pane && child instanceof Node node) {
            pane.getChildren().add(node);
        }
    }

    @Override
    public void removeChild(Object parent, Object child) {
        if (parent instanceof Pane pane && child instanceof Node node) {
            pane.getChildren().remove(node);
        }
    }

    @Override
    public void clearChildren(Object parent) {
        if (parent instanceof Pane pane) {
            pane.getChildren().clear();
        }
    }

    @Override
    public void setPosition(Object node, double x, double y) {
        if (node instanceof javafx.scene.Node n) {
            // 使用 translateX/Y 而不是 layoutX/Y，以便与 TranslateTransition 兼容
            n.setTranslateX(x);
            n.setTranslateY(y);
        }
    }

    @Override
    public void setSize(Object node, double width, double height) {
        if (node instanceof javafx.scene.layout.Region region) {
            region.setPrefSize(width, height);
        }
    }

    @Override
    public void setOpacity(Object node, double opacity) {
        if (node instanceof javafx.scene.Node n) {
            n.setOpacity(opacity);
        }
    }

    @Override
    public void setVisible(Object node, boolean visible) {
        if (node instanceof javafx.scene.Node n) {
            n.setVisible(visible);
        }
    }

    @Override
    public void setStyle(Object node, String style) {
        if (node instanceof javafx.scene.Node n) {
            n.setStyle(style);
        }
    }

    // ==================== 图形与特效 ====================

    @Override
    public CircleHandle createCircle(double centerX, double centerY, double radius,
                                      ColorDef fillColor, ColorDef strokeColor, double strokeWidth) {
        Circle circle = new Circle(centerX, centerY, radius);
        circle.setFill(parseColor(fillColor));
        circle.setStroke(strokeColor != null ? parseColor(strokeColor) : Color.TRANSPARENT);
        circle.setStrokeWidth(strokeWidth);
        return new CircleHandle(circle, centerX, centerY, radius);
    }

    @Override
    public void setCirclePosition(CircleHandle handle, double centerX, double centerY) {
        if (handle.getNativeHandle() instanceof Circle circle) {
            circle.setCenterX(centerX);
            circle.setCenterY(centerY);
            handle.setCenterX(centerX);
            handle.setCenterY(centerY);
        }
    }

    @Override
    public void setCircleRadius(CircleHandle handle, double radius) {
        if (handle.getNativeHandle() instanceof Circle circle) {
            circle.setRadius(radius);
            handle.setRadius(radius);
        }
    }

    @Override
    public void setCircleColor(CircleHandle handle, ColorDef fillColor, ColorDef strokeColor) {
        if (handle.getNativeHandle() instanceof Circle circle) {
            circle.setFill(parseColor(fillColor));
            if (strokeColor != null) {
                circle.setStroke(parseColor(strokeColor));
            }
        }
    }

    @Override
    public LineHandle createLine(double startX, double startY, double endX, double endY,
                                  ColorDef color, double strokeWidth) {
        Line line = new Line(startX, startY, endX, endY);
        line.setStroke(parseColor(color));
        line.setStrokeWidth(strokeWidth);
        return new LineHandle(line, startX, startY, endX, endY);
    }

    @Override
    public Object createRadialGradientRect(double width, double height, double focusX, double focusY,
                                            List<GradientStop> stops) {
        Rectangle rect = new Rectangle();
        rect.setWidth(width);
        rect.setHeight(height);

        Stop[] javafxStops = stops.stream()
                .map(s -> new Stop(s.offset, parseColor(s.color)))
                .toArray(Stop[]::new);

        RadialGradient radial = new RadialGradient(0, 0, focusX, focusY, 1.2, true,
                CycleMethod.NO_CYCLE, javafxStops);
        rect.setFill(radial);
        return rect;
    }

    private Color parseColor(ColorDef colorDef) {
        if (colorDef.isHex()) {
            try {
                return Color.web(colorDef.getValue());
            } catch (Exception e) {
                return Color.WHITE;
            }
        }
        return switch (colorDef.getValue().toLowerCase()) {
            case "red" -> Color.RED;
            case "green" -> Color.GREEN;
            case "blue" -> Color.BLUE;
            case "yellow" -> Color.YELLOW;
            case "orange" -> Color.ORANGE;
            case "purple" -> Color.PURPLE;
            case "pink" -> Color.PINK;
            case "cyan" -> Color.CYAN;
            case "white" -> Color.WHITE;
            case "black" -> Color.BLACK;
            case "gray", "grey" -> Color.GRAY;
            case "transparent" -> Color.TRANSPARENT;
            default -> Color.WHITE;
        };
    }

    // ==================== 动画系统 ====================

    @Override
    public AnimationHandle createFadeIn(Object node, double durationMs, double fromOpacity, double toOpacity) {
        if (!(node instanceof javafx.scene.Node n)) return null;
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), n);
        ft.setFromValue(fromOpacity);
        ft.setToValue(toOpacity);
        activeAnimations.add(ft);
        return new AnimationHandle(ft);
    }

    @Override
    public AnimationHandle createFadeOut(Object node, double durationMs, double fromOpacity, double toOpacity) {
        if (!(node instanceof javafx.scene.Node n)) return null;
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), n);
        ft.setFromValue(fromOpacity);
        ft.setToValue(toOpacity);
        activeAnimations.add(ft);
        return new AnimationHandle(ft);
    }

    @Override
    public AnimationHandle createScaleAnimation(Object node, double durationMs,
                                                 double fromScaleX, double fromScaleY,
                                                 double toScaleX, double toScaleY) {
        if (!(node instanceof javafx.scene.Node n)) return null;
        ScaleTransition st = new ScaleTransition(Duration.millis(durationMs), n);
        st.setFromX(fromScaleX);
        st.setFromY(fromScaleY);
        st.setToX(toScaleX);
        st.setToY(toScaleY);
        activeAnimations.add(st);
        return new AnimationHandle(st);
    }

    @Override
    public void playAnimation(AnimationHandle handle) {
        if (handle.getNativeHandle() instanceof Animation animation) {
            animation.play();
        }
    }

    @Override
    public void stopAnimation(AnimationHandle handle) {
        if (handle.getNativeHandle() instanceof Animation animation) {
            animation.stop();
        }
    }

    @Override
    public AnimationHandle createSequentialAnimation(AnimationHandle... animations) {
        Animation[] nativeAnimations = Arrays.stream(animations)
                .map(AnimationHandle::getNativeHandle)
                .filter(Animation.class::isInstance)
                .map(Animation.class::cast)
                .toArray(Animation[]::new);

        SequentialTransition sequence = new SequentialTransition(nativeAnimations);
        activeAnimations.add(sequence);
        return new AnimationHandle(sequence);
    }

    @Override
    public AnimationHandle createParallelAnimation(AnimationHandle... animations) {
        Animation[] nativeAnimations = Arrays.stream(animations)
                .map(AnimationHandle::getNativeHandle)
                .filter(Animation.class::isInstance)
                .map(Animation.class::cast)
                .toArray(Animation[]::new);

        ParallelTransition parallel = new ParallelTransition(nativeAnimations);
        activeAnimations.add(parallel);
        return new AnimationHandle(parallel);
    }

    @Override
    public AnimationHandle createPause(double durationMs, Runnable onComplete) {
        PauseTransition pause = new PauseTransition(Duration.millis(durationMs));
        if (onComplete != null) {
            pause.setOnFinished(e -> onComplete.run());
        }
        activeAnimations.add(pause);
        return new AnimationHandle(pause);
    }

    private void stopAllAnimations() {
        for (Animation animation : activeAnimations) {
            if (animation != null) {
                try {
                    animation.stop();
                } catch (Exception ignored) {
                }
            }
        }
        activeAnimations.clear();

        for (Timeline timeline : activeTimelines) {
            if (timeline != null) {
                try {
                    timeline.stop();
                    timeline.getKeyFrames().clear();
                } catch (Exception ignored) {
                }
            }
        }
        activeTimelines.clear();
    }

    // ==================== 交互系统 ====================

    @Override
    public EventHandlerHandle registerKeyHandler(FunctionalCallback<KeyCodeEvent> handler) {
        Scene scene = getCurrentScene();
        if (scene == null) return null;

        EventHandler<KeyEvent> javaFxHandler = event -> {
            KeyCodeEvent keyCodeEvent = convertKeyCode(event.getCode());
            handler.run(keyCodeEvent);
            if (keyCodeEvent.isConsumed()) {
                event.consume();
            }
        };

        scene.addEventHandler(KeyEvent.KEY_PRESSED, javaFxHandler);
        activeKeyHandlers.add(javaFxHandler);
        return new EventHandlerHandle(javaFxHandler);
    }

    @Override
    public EventHandlerHandle registerMouseHandler(FunctionalCallback<com.xiaowu.game.starveil.render.engine.MouseEvent> handler) {
        Scene scene = getCurrentScene();
        if (scene == null) return null;

        EventHandler<MouseEvent> javaFxHandler = event -> {
            com.xiaowu.game.starveil.render.engine.MouseEvent.MouseButton button;
            switch (event.getButton()) {
                case PRIMARY -> button = com.xiaowu.game.starveil.render.engine.MouseEvent.MouseButton.PRIMARY;
                case SECONDARY -> button = com.xiaowu.game.starveil.render.engine.MouseEvent.MouseButton.SECONDARY;
                case MIDDLE -> button = com.xiaowu.game.starveil.render.engine.MouseEvent.MouseButton.MIDDLE;
                default -> button = null;
            }

            if (button != null) {
                com.xiaowu.game.starveil.render.engine.MouseEvent mouseEvent = new com.xiaowu.game.starveil.render.engine.MouseEvent(button, event.getSceneX(), event.getSceneY());
                handler.run(mouseEvent);
                if (mouseEvent.isConsumed()) {
                    event.consume();
                }
            }
        };

        scene.addEventHandler(MouseEvent.MOUSE_CLICKED, javaFxHandler);
        activeMouseHandlers.add(javaFxHandler);
        return new EventHandlerHandle(javaFxHandler);
    }

    @Override
    public void unregisterHandler(EventHandlerHandle handle) {
        if (handle.getNativeHandle() instanceof EventHandler<?> handler) {
            Scene scene = getCurrentScene();
            if (scene != null) {
                if (activeKeyHandlers.contains(handler)) {
                    scene.removeEventHandler(KeyEvent.KEY_PRESSED, (EventHandler<KeyEvent>) handler);
                    activeKeyHandlers.remove(handler);
                }
                if (activeMouseHandlers.contains(handler)) {
                    scene.removeEventHandler(MouseEvent.MOUSE_CLICKED, (EventHandler<MouseEvent>) handler);
                    activeMouseHandlers.remove(handler);
                }
            }
        }
    }

    private void clearAllHandlers() {
        Scene scene = getCurrentScene();
        if (scene != null) {
            for (EventHandler<KeyEvent> handler : activeKeyHandlers) {
                scene.removeEventHandler(KeyEvent.KEY_PRESSED, handler);
            }
            for (EventHandler<MouseEvent> handler : activeMouseHandlers) {
                scene.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler);
            }
        }
        activeKeyHandlers.clear();
        activeMouseHandlers.clear();
    }

    private KeyCodeEvent convertKeyCode(KeyCode code) {
        KeyCodeEvent.KeyCode abstractCode = switch (code) {
            case SPACE -> KeyCodeEvent.KeyCode.SPACE;
            case ENTER -> KeyCodeEvent.KeyCode.ENTER;
            case ESCAPE -> KeyCodeEvent.KeyCode.ESCAPE;
            case A -> KeyCodeEvent.KeyCode.A;
            case B -> KeyCodeEvent.KeyCode.B;
            case C -> KeyCodeEvent.KeyCode.C;
            case D -> KeyCodeEvent.KeyCode.D;
            case E -> KeyCodeEvent.KeyCode.E;
            case F -> KeyCodeEvent.KeyCode.F;
            case G -> KeyCodeEvent.KeyCode.G;
            case H -> KeyCodeEvent.KeyCode.H;
            case I -> KeyCodeEvent.KeyCode.I;
            case J -> KeyCodeEvent.KeyCode.J;
            case K -> KeyCodeEvent.KeyCode.K;
            case L -> KeyCodeEvent.KeyCode.L;
            case M -> KeyCodeEvent.KeyCode.M;
            case N -> KeyCodeEvent.KeyCode.N;
            case O -> KeyCodeEvent.KeyCode.O;
            case P -> KeyCodeEvent.KeyCode.P;
            case Q -> KeyCodeEvent.KeyCode.Q;
            case R -> KeyCodeEvent.KeyCode.R;
            case S -> KeyCodeEvent.KeyCode.S;
            case T -> KeyCodeEvent.KeyCode.T;
            case U -> KeyCodeEvent.KeyCode.U;
            case V -> KeyCodeEvent.KeyCode.V;
            case W -> KeyCodeEvent.KeyCode.W;
            case X -> KeyCodeEvent.KeyCode.X;
            case Y -> KeyCodeEvent.KeyCode.Y;
            case Z -> KeyCodeEvent.KeyCode.Z;
            case DIGIT0 -> KeyCodeEvent.KeyCode.DIGIT_0;
            case DIGIT1 -> KeyCodeEvent.KeyCode.DIGIT_1;
            case DIGIT2 -> KeyCodeEvent.KeyCode.DIGIT_2;
            case DIGIT3 -> KeyCodeEvent.KeyCode.DIGIT_3;
            case DIGIT4 -> KeyCodeEvent.KeyCode.DIGIT_4;
            case DIGIT5 -> KeyCodeEvent.KeyCode.DIGIT_5;
            case DIGIT6 -> KeyCodeEvent.KeyCode.DIGIT_6;
            case DIGIT7 -> KeyCodeEvent.KeyCode.DIGIT_7;
            case DIGIT8 -> KeyCodeEvent.KeyCode.DIGIT_8;
            case DIGIT9 -> KeyCodeEvent.KeyCode.DIGIT_9;
            case UP -> KeyCodeEvent.KeyCode.UP;
            case DOWN -> KeyCodeEvent.KeyCode.DOWN;
            case LEFT -> KeyCodeEvent.KeyCode.LEFT;
            case RIGHT -> KeyCodeEvent.KeyCode.RIGHT;
            default -> KeyCodeEvent.KeyCode.UNKNOWN;
        };
        return new KeyCodeEvent(abstractCode, code != null ? code.getName().charAt(0) : '\0');
    }

    // ==================== 工具方法 ====================

    @Override
    public void runLater(Runnable action) {
        Platform.runLater(action);
    }

    @Override
    public ColorDef createColor(String hexOrName) {
        if (hexOrName.startsWith("#")) {
            return ColorDef.hex(hexOrName);
        }
        return ColorDef.name(hexOrName);
    }

    @Override
    public TextStyle createTextStyle(String fontFamily, double fontSize, boolean bold,
                                      boolean italic, ColorDef fillColor) {
        return new TextStyle(fontFamily, fontSize, bold, italic, fillColor, 0, 1.5);
    }

    @Override
    public void setOverlayVisible(boolean visible, ColorDef overlayColor) {
        if (overlay != null) {
            overlay.setVisible(visible);
            if (visible && overlayColor != null) {
                overlay.setStyle(String.format("-fx-background-color: %s;", overlayColor.getValue()));
            }
        }
        if (effectContainer != null) {
            effectContainer.setVisible(visible);
            effectContainer.setMouseTransparent(!visible);
        }
    }

    private Scene getCurrentScene() {
        if (rootContainer != null && rootContainer.getScene() != null) {
            return rootContainer.getScene();
        }
        return null;
    }

    // ==================== 高级 UI 组件 ====================

    @Override
    public Object createGridPane(double hgap, double vgap) {
        GridPane grid = new GridPane();
        grid.setHgap(hgap);
        grid.setVgap(vgap);
        return grid;
    }

    @Override
    public void addToGrid(Object gridPane, Object child, int columnIndex, int rowIndex) {
        if (gridPane instanceof GridPane grid && child instanceof Node node) {
            grid.add(node, columnIndex, rowIndex);
        }
    }

    @Override
    public Object createScrollPane() {
        return new ScrollPane();
    }

    @Override
    public void setScrollPaneContent(Object scrollPane, Object content) {
        if (scrollPane instanceof ScrollPane sp && content instanceof Node node) {
            sp.setContent(node);
        }
    }

    @Override
    public Object createImageView(String imagePath, double fitWidth, double fitHeight) {
        try {
            Object node = com.xiaowu.game.starveil.render.TextureNodeFactory.load(imagePath, fitWidth, fitHeight);
            return node != null ? node : new ImageView();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object createSeparator(double width, ColorDef color) {
        Line line = new Line();
        line.setStroke(parseColor(color));
        line.setStrokeWidth(2);
        line.setStartX(-width / 2);
        line.setEndX(width / 2);
        return line;
    }

    @Override
    public void setAlignment(Object node, String alignment) {
        if (node instanceof Region region) {
            Pos pos = switch (alignment.toLowerCase()) {
                case "top_center" -> Pos.TOP_CENTER;
                case "top_right" -> Pos.TOP_RIGHT;
                case "top_left" -> Pos.TOP_LEFT;
                case "center" -> Pos.CENTER;
                case "center_left" -> Pos.CENTER_LEFT;
                case "center_right" -> Pos.CENTER_RIGHT;
                case "bottom_center" -> Pos.BOTTOM_CENTER;
                case "bottom_right" -> Pos.BOTTOM_RIGHT;
                case "bottom_left" -> Pos.BOTTOM_LEFT;
                default -> Pos.CENTER;
            };
            if (node instanceof VBox vbox) vbox.setAlignment(pos);
            else if (node instanceof HBox hbox) hbox.setAlignment(pos);
            else if (node instanceof StackPane sp) sp.setAlignment(pos);
        }
    }

    @Override
    public void setPadding(Object node, double top, double right, double bottom, double left) {
        if (node instanceof Region region) {
            region.setPadding(new Insets(top, right, bottom, left));
        }
    }

    @Override
    public void setBackground(Object node, String backgroundStyle) {
        if (node instanceof Region region) {
            region.setStyle(backgroundStyle);
        }
    }

    @Override
    public void setMouseTransparent(Object node, boolean transparent) {
        if (node instanceof javafx.scene.Node n) {
            n.setMouseTransparent(transparent);
        }
    }

    @Override
    public void setFocusTraversable(Object node, boolean focusable) {
        if (node instanceof Region region) {
            region.setFocusTraversable(focusable);
        }
    }

    @Override
    public void setCursor(Object node, String cursorType) {
        if (node instanceof javafx.scene.Node n) {
            javafx.scene.Cursor cursor = switch (cursorType.toLowerCase()) {
                case "hand" -> javafx.scene.Cursor.HAND;
                case "default" -> javafx.scene.Cursor.DEFAULT;
                case "text" -> javafx.scene.Cursor.TEXT;
                default -> javafx.scene.Cursor.DEFAULT;
            };
            n.setCursor(cursor);
        }
    }

    @Override
    public void requestFocus(Object node) {
        if (node instanceof javafx.scene.Node n) {
            n.requestFocus();
        }
    }

    @Override
    public void setUserData(Object node, Object data) {
        if (node instanceof javafx.scene.Node n) {
            n.setUserData(data);
        }
    }

    @Override
    public Object getUserData(Object node) {
        if (node instanceof javafx.scene.Node n) {
            return n.getUserData();
        }
        return null;
    }

    @Override
    public void bringToFront(Object node) {
        if (node instanceof javafx.scene.Node n && n.getParent() instanceof Pane parent) {
            n.toFront();
        }
    }

    @Override
    public double getNodeWidth(Object node) {
        if (node instanceof Region region) {
            return region.getWidth();
        }
        return 0;
    }

    @Override
    public double getNodeHeight(Object node) {
        if (node instanceof Region region) {
            return region.getHeight();
        }
        return 0;
    }

    @Override
    public double getNodeOpacity(Object node) {
        if (node instanceof javafx.scene.Node n) {
            return n.getOpacity();
        }
        return 1.0;
    }

    @Override
    public double getNodeTranslateX(Object node) {
        if (node instanceof javafx.scene.Node n) {
            return n.getTranslateX();
        }
        return 0;
    }

    @Override
    public double getNodeTranslateY(Object node) {
        if (node instanceof javafx.scene.Node n) {
            return n.getTranslateY();
        }
        return 0;
    }

    @Override
    public Object createText(String text, TextStyle style) {
        Text textNode = new Text(text);
        if (style != null) {
            // 设置文本颜色
            textNode.setFill(javafx.scene.paint.Color.web(style.getFillColor().getValue()));
            
            // 加载字体
            if (style.getFontFamily() != null && !style.getFontFamily().isEmpty()) {
                try {
                    javafx.scene.text.Font font = javafx.scene.text.Font.loadFont(
                        ResourceResolver.getResourceAsStream(style.getFontFamily()), style.getFontSize());
                    if (font != null) {
                        textNode.setFont(font);
                    } else {
                        textNode.setFont(new javafx.scene.text.Font(style.getFontSize()));
                    }
                } catch (Exception e) {
                    textNode.setFont(new javafx.scene.text.Font(style.getFontSize()));
                }
            } else {
                textNode.setFont(new javafx.scene.text.Font(style.getFontSize()));
            }
        }
        return textNode;
    }

    @Override
    public void setTextWrappingWidth(Object textNode, double width) {
        if (textNode instanceof Text t) {
            // Text 不支持自动换行，需要使用 BoundsType
            t.setWrappingWidth(width);
        }
    }

    @Override
    public Object createRegion(double minWidth, double minHeight) {
        Region region = new Region();
        region.setMinWidth(minWidth);
        region.setMinHeight(minHeight);
        return region;
    }

    // ==================== 高级 UI 控件 ====================

    @Override
    public ComboBoxHandle createComboBox(List<String> items, double prefWidth) {
        ComboBox<String> comboBox = new ComboBox<>();
        if (items != null) {
            comboBox.getItems().addAll(items);
        }
        comboBox.setPrefWidth(prefWidth);
        return new ComboBoxHandle(comboBox);
    }

    @Override
    public void setComboBoxItems(ComboBoxHandle handle, List<String> items) {
        if (handle.getNativeHandle() instanceof ComboBox<?> cbUnchecked) {
            @SuppressWarnings("unchecked")
            ComboBox<String> cb = (ComboBox<String>) cbUnchecked;
            cb.getItems().clear();
            if (items != null) {
                cb.getItems().addAll(items);
            }
        }
    }

    @Override
    public void setComboBoxSelectedValue(ComboBoxHandle handle, String value) {
        if (handle.getNativeHandle() instanceof ComboBox<?> cbUnchecked) {
            @SuppressWarnings("unchecked")
            ComboBox<String> cb = (ComboBox<String>) cbUnchecked;
            cb.setValue(value);
        }
    }

    @Override
    public String getComboBoxSelectedValue(ComboBoxHandle handle) {
        if (handle.getNativeHandle() instanceof ComboBox<?> cbUnchecked) {
            @SuppressWarnings("unchecked")
            ComboBox<String> cb = (ComboBox<String>) cbUnchecked;
            return cb.getValue();
        }
        return null;
    }

    @Override
    public void setComboBoxOnAction(ComboBoxHandle handle, Runnable action) {
        handle.setOnAction(action);
        if (handle.getNativeHandle() instanceof ComboBox<?> cbUnchecked) {
            @SuppressWarnings("unchecked")
            ComboBox<String> cb = (ComboBox<String>) cbUnchecked;
            cb.setOnAction(e -> {
                if (action != null) action.run();
            });
        }
    }

    @Override
    public CheckBoxHandle createCheckBox(String text) {
        CheckBox checkBox = new CheckBox(text);
        return new CheckBoxHandle(checkBox);
    }

    @Override
    public boolean isCheckBoxSelected(CheckBoxHandle handle) {
        if (handle.getNativeHandle() instanceof CheckBox cb) {
            return cb.isSelected();
        }
        return false;
    }

    @Override
    public void setCheckBoxSelected(CheckBoxHandle handle, boolean selected) {
        if (handle.getNativeHandle() instanceof CheckBox cb) {
            cb.setSelected(selected);
        }
    }

    @Override
    public void addCheckBoxListener(CheckBoxHandle handle, 
                                     java.util.function.BiConsumer<Boolean, Boolean> listener) {
        handle.setListener(listener);
        if (handle.getNativeHandle() instanceof CheckBox cb) {
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (listener != null) listener.accept(oldVal, newVal);
            });
        }
    }

    @Override
    public SliderHandle createSlider(double min, double max, double initialValue, double prefWidth) {
        Slider slider = new Slider(min, max, initialValue);
        slider.setPrefWidth(prefWidth);
        return new SliderHandle(slider);
    }

    @Override
    public void setSliderValue(SliderHandle handle, double value) {
        if (handle.getNativeHandle() instanceof Slider s) {
            s.setValue(value);
        }
    }

    @Override
    public double getSliderValue(SliderHandle handle) {
        if (handle.getNativeHandle() instanceof Slider s) {
            return s.getValue();
        }
        return 0;
    }

    @Override
    public void addSliderListener(SliderHandle handle, 
                                   java.util.function.BiConsumer<Double, Double> listener) {
        handle.setListener(listener);
        if (handle.getNativeHandle() instanceof Slider s) {
            s.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (listener != null) listener.accept(oldVal.doubleValue(), newVal.doubleValue());
            });
        }
    }

    @Override
    public void setSliderTickConfig(SliderHandle handle, boolean showTicks, boolean showLabels, 
                                     double majorUnit, int minorCount, boolean snapToTicks) {
        if (handle.getNativeHandle() instanceof Slider s) {
            s.setShowTickMarks(showTicks);
            s.setShowTickLabels(showLabels);
            s.setMajorTickUnit(majorUnit);
            s.setMinorTickCount(minorCount);
            s.setSnapToTicks(snapToTicks);
        }
    }

    // ==================== 动画增强 ====================

    @Override
    public AnimationHandle createTranslateAnimation(Object node, double durationMs, double toX, double toY) {
        return createTranslateAnimation(node, durationMs, toX, toY, "LINEAR");
    }

    @Override
    public AnimationHandle createTranslateAnimation(Object node, double durationMs, double toX, double toY, String interpolator) {
        if (node instanceof javafx.scene.Node n) {
            TranslateTransition transition = new TranslateTransition(Duration.millis(durationMs), n);
            transition.setToX(toX);
            transition.setToY(toY);
            
            // 设置插值器
            switch (interpolator) {
                case "EASE_OUT" -> transition.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
                case "EASE_IN" -> transition.setInterpolator(javafx.animation.Interpolator.EASE_IN);
                case "EASE_BOTH" -> transition.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
                default -> transition.setInterpolator(javafx.animation.Interpolator.LINEAR);
            }
            
            AnimationHandle handle = new AnimationHandle(transition);
            activeAnimations.add(transition);
            return handle;
        }
        return new AnimationHandle(null);
    }

    @Override
    public AnimationHandle createPauseAnimation(double durationMs, Runnable onFinished) {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(durationMs)));
        timeline.setOnFinished(e -> {
            if (onFinished != null) onFinished.run();
        });
        AnimationHandle handle = new AnimationHandle(timeline);
        activeTimelines.add(timeline);
        return handle;
    }

    // ==================== 事件处理增强 ====================

    @Override
    public void setButtonOnMouseEnter(ButtonHandle handle, Runnable action) {
        if (handle.getNativeHandle() instanceof Button btn) {
            btn.setOnMouseEntered(e -> {
                if (action != null) action.run();
            });
        }
    }

    @Override
    public void setButtonOnMouseExit(ButtonHandle handle, Runnable action) {
        if (handle.getNativeHandle() instanceof Button btn) {
            btn.setOnMouseExited(e -> {
                if (action != null) action.run();
            });
        }
    }

    // 注意：setNodeOnMouseClicked和clearNodeOnMouseClicked由于MouseEvent命名冲突暂时注释
    // 可以通过registerMouseHandler替代

    // ==================== 布局增强 ====================

    @Override
    public void setMaxWidth(Object node, double maxWidth) {
        if (node instanceof Region r) {
            r.setMaxWidth(maxWidth);
        }
    }

    @Override
    public void setMaxHeight(Object node, double maxHeight) {
        if (node instanceof Region r) {
            // 支持 USE_PREF_SIZE 常量
            if (maxHeight == -1) {
                r.setMaxHeight(Region.USE_PREF_SIZE);
            } else {
                r.setMaxHeight(maxHeight);
            }
        }
    }

    @Override
    public void setPrefWidth(Object node, double prefWidth) {
        if (node instanceof Region r) {
            r.setPrefWidth(prefWidth);
        }
    }

    @Override
    public void setPrefHeight(Object node, double prefHeight) {
        if (node instanceof Region r) {
            r.setPrefHeight(prefHeight);
        }
    }

    @Override
    public void setLayoutX(Object node, double x) {
        if (node instanceof javafx.scene.Node n) {
            n.setLayoutX(x);
        }
    }

    @Override
    public void setLayoutY(Object node, double y) {
        if (node instanceof javafx.scene.Node n) {
            n.setLayoutY(y);
        }
    }

    @Override
    public void setStackPaneAlignment(Object node, String alignment) {
        if (node instanceof javafx.scene.Node n) {
            Pos pos = switch (alignment.toLowerCase()) {
                case "center" -> Pos.CENTER;
                case "top_left" -> Pos.TOP_LEFT;
                case "top_center" -> Pos.TOP_CENTER;
                case "top_right" -> Pos.TOP_RIGHT;
                case "center_left" -> Pos.CENTER_LEFT;
                case "center_right" -> Pos.CENTER_RIGHT;
                case "bottom_left" -> Pos.BOTTOM_LEFT;
                case "bottom_center" -> Pos.BOTTOM_CENTER;
                case "bottom_right" -> Pos.BOTTOM_RIGHT;
                default -> Pos.CENTER;
            };
            StackPane.setAlignment(n, pos);
        }
    }

    @Override
    public void setStackPaneMargin(Object node, double top, double right, double bottom, double left) {
        if (node instanceof javafx.scene.Node n && n.getParent() instanceof StackPane parent) {
            StackPane.setMargin(n, new Insets(top, right, bottom, left));
        }
    }

    @Override
    public void setScrollPaneFitToWidth(Object scrollPane, boolean fit) {
        if (scrollPane instanceof ScrollPane sp) {
            sp.setFitToWidth(fit);
        }
    }

    @Override
    public void setScrollPaneBarPolicy(Object scrollPane, String hPolicy, String vPolicy) {
        if (scrollPane instanceof ScrollPane sp) {
            sp.setHbarPolicy(switch (hPolicy.toLowerCase()) {
                case "always" -> ScrollPane.ScrollBarPolicy.ALWAYS;
                case "never" -> ScrollPane.ScrollBarPolicy.NEVER;
                default -> ScrollPane.ScrollBarPolicy.AS_NEEDED;
            });
            sp.setVbarPolicy(switch (vPolicy.toLowerCase()) {
                case "always" -> ScrollPane.ScrollBarPolicy.ALWAYS;
                case "never" -> ScrollPane.ScrollBarPolicy.NEVER;
                default -> ScrollPane.ScrollBarPolicy.AS_NEEDED;
            });
        }
    }

    // ==================== 属性系统 ====================

    @Override
    public Object createBooleanProperty(boolean initial) {
        return new SimpleBooleanProperty(initial);
    }

    @Override
    public void setBooleanProperty(Object property, boolean value) {
        if (property instanceof SimpleBooleanProperty prop) {
            prop.set(value);
        }
    }

    @Override
    public boolean getBooleanProperty(Object property) {
        if (property instanceof SimpleBooleanProperty prop) {
            return prop.get();
        }
        return false;
    }

    @Override
    public void addBooleanPropertyListener(Object property, 
                                            java.util.function.BiConsumer<Boolean, Boolean> listener) {
        if (property instanceof SimpleBooleanProperty prop) {
            prop.addListener((obs, oldVal, newVal) -> {
                if (listener != null) listener.accept(oldVal, newVal);
            });
        }
    }

    // ==================== 节点查询 ====================

    @Override
    public boolean hasChild(Object parent, Object child) {
        if (parent instanceof Pane p) {
            return p.getChildren().contains(child);
        }
        return false;
    }

    @Override
    public Object getNodeScene(Object node) {
        if (node instanceof javafx.scene.Node n) {
            return n.getScene();
        }
        return null;
    }

    // ==================== 字体管理 ====================

    @Override
    public void loadFont(String resourcePath, double size) {
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Font.loadFont(is, size);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void loadFont(java.io.InputStream stream, double size) {
        try {
            Font.loadFont(stream, size);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void setFontFamily(Object node, String fontFamily) {
        if (node instanceof javafx.scene.control.Labeled labeled) {
            Font currentFont = labeled.getFont();
            labeled.setFont(Font.font(fontFamily, currentFont.getSize()));
        } else if (node instanceof Text t) {
            Font currentFont = t.getFont();
            t.setFont(Font.font(fontFamily, currentFont.getSize()));
        }
    }

    // ==================== 禁用/启用 ====================

    @Override
    public void setButtonDisabled(ButtonHandle handle, boolean disabled) {
        if (handle.getNativeHandle() instanceof Button btn) {
            btn.setDisable(disabled);
        }
    }
}

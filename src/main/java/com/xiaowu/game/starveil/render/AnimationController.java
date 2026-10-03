package com.xiaowu.game.starveil.render;

import com.xiaowu.game.starveil.render.engine.*;
import com.xiaowu.game.starveil.render.engine.handles.TextAnimationHandle;
import com.xiaowu.game.starveil.render.engine.handles.AnimationHandle;
import javafx.scene.text.TextFlow;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * 动画控制器 - 使用 RenderEngine 接口进行文本动画
 * 
 * 重构后不再直接依赖 JavaFX 渲染细节,而是通过 RenderEngine 接口进行渲染操作。
 * 这使得后期可以轻松替换为其他渲染引擎(如 LibGDX、LWJGL 等)。
 */
public class AnimationController {

    private final RenderEngine renderEngine;
    private TextAnimationHandle currentAnimationHandle;
    private boolean isAnimating = false;
    private String fullMessage = "";
    private int fullDisplayedLength;
    private Runnable onAnimationComplete;

    private final String TEXT_PRIMARY = "#FFE4E1";

    public AnimationController() {
        this(null);
    }

    /**
     * 创建动画控制器
     * @param renderEngine 渲染引擎实例,为 null 时使用默认 JavaFX 引擎
     */
    public AnimationController(RenderEngine renderEngine) {
        this.renderEngine = renderEngine != null ? renderEngine : createDefaultEngine();
    }

    private RenderEngine createDefaultEngine() {
        // 这里可以改为从依赖注入容器获取
        return null; // 如果为 null,则调用方需要确保传入非 null engine
    }

    /**
     * 启动文本逐字动画
     * @param msg 消息内容
     * @param flow 文本容器 (TextFlow)
     * @param prompt 提示容器 (HBox)
     * @param onComplete 完成回调
     */
    public void startTextAnimation(String msg, TextFlow flow, HBox prompt, Runnable onComplete) {
        if (renderEngine == null) {
            throw new IllegalStateException("RenderEngine 未初始化。请传入有效的 RenderEngine 实例。");
        }

        // 停止之前的动画
        if (currentAnimationHandle != null) {
            renderEngine.stopTypewriterAnimation(currentAnimationHandle);
        }

        fullMessage = msg;
        fullDisplayedLength = computeDisplayedLength(msg);
        isAnimating = true;
        onAnimationComplete = onComplete;

        // 使用 RenderEngine 启动逐字动画
        currentAnimationHandle = renderEngine.startTypewriterAnimation(
                msg,
                flow,
                prompt,
                () -> {
                    isAnimating = false;
                    if (onAnimationComplete != null) {
                        onAnimationComplete.run();
                    }
                },
                0,
                getTextSpeed()
        );
    }

    /**
     * 在已有文本基础上继续追加动画（用于 <more> 分段）
     * @param additionalText 追加的文本
     * @param flow 文本容器
     * @param prompt 提示容器
     * @param onComplete 完成回调
     */
    public void resumeTextAnimation(String additionalText, TextFlow flow, HBox prompt, Runnable onComplete) {
        if (renderEngine == null) {
            throw new IllegalStateException("RenderEngine 未初始化。请传入有效的 RenderEngine 实例。");
        }

        if (currentAnimationHandle != null) {
            renderEngine.stopTypewriterAnimation(currentAnimationHandle);
        }

        int previousDisplayedLength = fullDisplayedLength;
        String newMessage = fullMessage + additionalText;
        fullMessage = newMessage;
        fullDisplayedLength = computeDisplayedLength(newMessage);
        isAnimating = true;
        onAnimationComplete = onComplete;

        currentAnimationHandle = renderEngine.startTypewriterAnimation(
                newMessage, flow, prompt,
                () -> {
                    isAnimating = false;
                    if (onAnimationComplete != null) {
                        onAnimationComplete.run();
                    }
                },
                previousDisplayedLength,
                getTextSpeed()
        );
    }

    private int computeDisplayedLength(String rawText) {
        if (renderEngine == null) return rawText.length();
        try {
            List<com.xiaowu.game.starveil.render.engine.TextSegment> segments =
                    renderEngine.parseColoredText(rawText);
            return segments.stream().mapToInt(s -> s.text.length()).sum();
        } catch (Exception e) {
            return rawText.length();
        }
    }

    private int getTextSpeed() {
        try {
            String val = com.xiaowu.game.starveil.infrastructure.persistence.DataManager.get("starveil:setting.text_speed", "50");
            return Integer.parseInt(val);
        } catch (Exception e) {
            return 50;
        }
    }

    /**
     * 跳过正在进行的动画
     * @param flow 文本容器
     * @param prompt 提示容器
     */
    public void skipAnimation(TextFlow flow, HBox prompt) {
        if (isAnimating && currentAnimationHandle != null && renderEngine != null) {
            renderEngine.skipTypewriterAnimation(currentAnimationHandle);
            isAnimating = false;
            showContinuePrompt(prompt);
        }
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    public void stopAnimation() {
        if (renderEngine != null && currentAnimationHandle != null) {
            renderEngine.stopTypewriterAnimation(currentAnimationHandle);
        }
        currentAnimationHandle = null;
        isAnimating = false;
    }

    /**
     * 解析彩色文本
     * 这个方法保留为公共方法,因为其他类可能直接使用
     * @param text 带颜色标签的文本
     * @return 文本段列表 (engine.TextSegment)
     */
    public List<com.xiaowu.game.starveil.render.engine.TextSegment> parseColoredText(String text) {
        if (renderEngine != null) {
            return renderEngine.parseColoredText(text);
        }
        // 如果 engine 为 null,返回空列表
        return java.util.Collections.emptyList();
    }

    private void showContinuePrompt(HBox prompt) {
        if (renderEngine != null && prompt != null && !prompt.getChildren().isEmpty()) {
            if (prompt.getOpacity() >= 0.99) return;
            AnimationHandle fadeHandle = renderEngine.createFadeIn(prompt, 300, 0, 1);
            renderEngine.playAnimation(fadeHandle);
        }
    }

    /**
     * 获取渲染引擎实例
     */
    public RenderEngine getRenderEngine() {
        return renderEngine;
    }

    /**
     * 内部文本段类 - 保留兼容性,但建议使用 engine.TextSegment
     */
    @Deprecated
    public static class TextSegment {
        String text;
        String color;

        TextSegment(String text, String color) {
            this.text = text;
            this.color = color;
        }
    }
}

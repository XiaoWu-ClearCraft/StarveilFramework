package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SeqBuilder - 用于构建对话步骤列表的工具类
 * 兼容原有的构建模式，返回步骤列表供 DialogSequence 使用
 *
 * @deprecated 请改用 {@link StoryScript} + {@link StoryChapter}。
 *             本类仅为兼容旧内容保留。
 */
@Deprecated
public class SeqBuilder {

    private final List<DialogSequence.DialogStep> steps = new ArrayList<>();
    private DialogSequence.DialogStep lastAddedStep = null;
    private final Map<String, Consumer<Object>> stepCallbacks = new HashMap<>();

    public SeqBuilder addDialog(String sp, String txt) {
        return addDialog(sp, txt, null, "");
    }

    public SeqBuilder addDialog(String sp, String txt, String voice) {
        return addDialog(sp, txt, voice, "");
    }

    public SeqBuilder addDialog(String sp, String txt, String voice, String standeeImagePath) {
        DialogSequence.DialogStep step = new DialogSequence.DialogStep(sp, txt, null, null, voice);
        step.standeeImagePath = standeeImagePath;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public SeqBuilder addInput(String prompt) {
        return addInput(prompt, null);
    }

    public SeqBuilder addInput(String prompt, String simulatedText) {
        DialogSequence.DialogStep step = new DialogSequence.DialogStep(null, null, prompt, null, null);
        step.simulatedText = simulatedText;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public SeqBuilder addDateInput(String prompt) {
        DialogSequence.DialogStep step = new DialogSequence.DialogStep(null, null, prompt, null, null, true);
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public SeqBuilder addChoice(String speaker, String prompt, List<String> choices) {
        DialogSequence.DialogStep step = new DialogSequence.DialogStep(speaker, null, null, new DialogSequence.ChoiceStep(prompt, choices), null);
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public SeqBuilder addReturnStep(String anchorName) {
        DialogSequence.DialogStep step = DialogSequence.createReturnStep(anchorName);
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public SeqBuilder showImage(String path) {
        DialogSequence.DialogStep step = new DialogSequence.DialogStep(null, null, null, null, null);
        step.isShowImage = true;
        step.imagePath = path;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public SeqBuilder hideImage() {
        DialogSequence.DialogStep step = new DialogSequence.DialogStep(null, null, null, null, null);
        step.isHideImage = true;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public SeqBuilder addMapSelector() {
        return addMapSelector(null, null);
    }

    public SeqBuilder addMapSelector(List<String> allowedMaps, List<Integer> allowedButtons) {
        DialogSequence.DialogStep step = new DialogSequence.DialogStep(null, null, null, null, null, false, true, allowedMaps, allowedButtons);
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    /**
     * 为最后添加的步骤设置回调
     */
    public SeqBuilder withNextCallback(Consumer<Object> callback) {
        if (lastAddedStep == null) {
            throw new IllegalStateException("必须先添加步骤才能设置回调");
        }

        LoggerManager.Logger("DEBUG", "SeqBuilder 设置步骤回调，步骤ID: " + lastAddedStep.id +
                ", 类型: " + (lastAddedStep.isMapSelector ? "地图选择器" :
                lastAddedStep.message != null ? "对话" :
                        lastAddedStep.inputPrompt != null ? "输入" :
                                lastAddedStep.choiceStep != null ? "选择" : "未知"));

        stepCallbacks.put(lastAddedStep.id, callback);
        lastAddedStep.completionCallback = callback;
        return this;
    }

    public SeqBuilder setAnchor(String name) {
        ChatManager.getInstance().setAnchor(name, steps.size(), steps.size());
        return this;
    }

    public void returnAnchor(String name) {
        ChatManager.getInstance().returnAnchor(name);
    }

    /**
     * 构建步骤列表
     */
    public List<DialogSequence.DialogStep> build() {
        return steps;
    }

    /**
     * 获取步骤回调映射
     */
    public Map<String, Consumer<Object>> getCallbacks() {
        return stepCallbacks;
    }

    /**
     * 构建结果类，包含步骤列表和回调映射
     */
    public static class BuildResult {
        public final List<DialogSequence.DialogStep> steps;
        public final Map<String, Consumer<Object>> callbacks;

        public BuildResult(List<DialogSequence.DialogStep> steps, Map<String, Consumer<Object>> callbacks) {
            this.steps = steps;
            this.callbacks = callbacks;
        }
    }
}
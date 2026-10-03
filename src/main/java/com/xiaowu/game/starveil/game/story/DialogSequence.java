package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.game.world.MapManager;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import javafx.application.Platform;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 步骤式对话序列（旧版剧情写法）。
 *
 * @deprecated 请改用 {@link StoryScript} + {@link StoryChapter}。
 *             步骤列表 + {@code insertChain} + {@code setAnchor}/{@code addReturnStep}
 *             需要作者同时维护步骤索引、插入位置与回调时机，分支和循环都很难读；
 *             {@code StoryScript} 让剧情回到普通 Java 控制流（{@code while}/{@code if}），
 *             并且所有章节共用 {@link StoryScheduler} 的一条线程。
 *             本类仅为兼容旧内容保留。
 */
@Deprecated
public class DialogSequence {

    private String sequenceId;
    public final List<DialogStep> steps = new ArrayList<>();
    public int currentStep = 0;
    private CompletableFuture<Void> sequenceFuture;
    public final String callerClass;
    final String callerMethod;
    private final Map<String, Consumer<Object>> stepCallbacks = new HashMap<>();
    private DialogStep lastAddedStep = null;

    private ChatManager chatManager;
    public volatile boolean jumpRequested;
    public int originalStepCount;

    public DialogSequence(String cls, String method, ChatManager chatManager) {
        this.callerClass = cls;
        this.callerMethod = method;
        this.chatManager = chatManager;
    }

    public DialogSequence addDialog(String speaker, String message) {
        return addDialog(speaker, message, null, "");
    }

    public DialogSequence addDialog(String speaker, String message, String voicePath) {
        return addDialog(speaker, message, voicePath, "");
    }

    public DialogSequence addDialog(String speaker, String message, String voicePath, String standeeImagePath) {
        DialogStep step = new DialogStep(speaker, message, null, null, voicePath);
        step.standeeImagePath = standeeImagePath;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public DialogSequence addInput(String prompt) {
        return addInput(prompt, null);
    }

    public DialogSequence addInput(String prompt, String simulatedText) {
        DialogStep step = new DialogStep(null, null, prompt, null, null);
        step.simulatedText = simulatedText;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public DialogSequence addDateInput(String prompt) {
        DialogStep step = new DialogStep(null, null, prompt, null, null, true);
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public DialogSequence addChoice(String prompt, List<String> choices) {
        return addChoice(null, prompt, choices);
    }

    public DialogSequence addChoice(String speaker, String prompt, List<String> choices) {
        DialogStep step = new DialogStep(speaker, null, null, new ChoiceStep(prompt, choices), null);
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public DialogSequence showImage(String path) {
        DialogStep step = new DialogStep(null, null, null, null, null);
        step.isShowImage = true;
        step.imagePath = path;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public DialogSequence hideImage() {
        DialogStep step = new DialogStep(null, null, null, null, null);
        step.isHideImage = true;
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    public DialogSequence addMapSelector() {
        return addMapSelector(null, null);
    }

    public DialogSequence addMapSelector(List<String> allowedMaps, List<Integer> allowedButtons) {
        DialogStep step = new DialogStep(null, null, null, null, null, false, true, allowedMaps, allowedButtons);
        steps.add(step);
        lastAddedStep = step;
        return this;
    }

    @Deprecated
    public DialogSequence withStepCallback(int stepIndex, Consumer<Object> callback) {
        LoggerManager.Logger("WARN", "不推荐使用withStepCallback，请使用withNextCallback");
        if (stepIndex >= 0 && stepIndex < steps.size()) {
            DialogStep step = steps.get(stepIndex);
            stepCallbacks.put(step.id, callback);
        }
        return this;
    }

    public DialogSequence withNextCallback(Consumer<Object> callback) {
        if (lastAddedStep == null) {
            throw new IllegalStateException("必须先添加步骤才能设置回调");
        }

        LoggerManager.Logger("DEBUG", "设置步骤回调，步骤ID: " + lastAddedStep.id +
                ", 类型: " + (lastAddedStep.isMapSelector ? "地图选择器" :
                lastAddedStep.message != null ? "对话" :
                        lastAddedStep.inputPrompt != null ? "输入" :
                                lastAddedStep.choiceStep != null ? "选择" : "未知"));

        stepCallbacks.put(lastAddedStep.id, callback);
        return this;
    }

    private static String generateId(List<DialogStep> steps) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            StringBuilder sb = new StringBuilder();
            for (DialogStep s : steps) {
                sb.append(s.speaker).append('|')
                        .append(s.message).append('|')
                        .append(s.inputPrompt).append('|')
                        .append(s.voicePath).append('|')
                        .append(s.isDateInput).append('|');
                if (s.choiceStep != null) sb.append(s.choiceStep.prompt).append(s.choiceStep.choices);
            }
            byte[] hash = md.digest(sb.toString().getBytes());
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    public CompletableFuture<Void> start() {
        originalStepCount = steps.size();
        sequenceFuture = new CompletableFuture<>();
        chatManager.setRunningSequence(this);
        chatManager.blockGameLayer();
        executeNextStep();
        sequenceFuture.whenComplete((r, ex) -> chatManager.unblockGameLayer());
        return sequenceFuture;
    }

    /**
     * 中止序列（{@code ChatManager.forceCloseAll} / 读档时调用）。
     *
     * <p>让正在等待序列结束的调用方立即返回，避免操作权一直被占住。
     */
    public void abort() {
        jumpRequested = false;
        if (sequenceFuture != null && !sequenceFuture.isDone()) {
            sequenceFuture.complete(null);
        }
    }

    public void executeNextStep() {
        LoggerManager.Logger("DEBUG", "=== executeNextStep 开始，步骤: " + currentStep + "/" + steps.size() + " ===");

        if (currentStep >= steps.size()) {
            LoggerManager.Logger("DEBUG", "所有步骤已完成");
            sequenceFuture.complete(null);
            return;
        }

        DialogStep step = steps.get(currentStep);
        LoggerManager.Logger("DEBUG", "当前步骤详情: ID=" + step.id +
                ", type=" + (step.isShowImage ? "显示图片" :
                step.isHideImage ? "隐藏图片" :
                step.isMapSelector ? "地图选择器" :
                step.message != null ? "对话" :
                        step.inputPrompt != null ? "输入" :
                                step.choiceStep != null ? "选择" : "未知") +
                ", skipUI=" + step.skipUI + ", result=" + step.result);

        if (step.skipUI && step.result != null) {
            LoggerManager.Logger("DEBUG", "步骤已有结果，跳过UI: " + step.id);

            if (step.completionCallback != null) {
                try {
                    step.completionCallback.accept(step.result);
                } catch (Exception e) {
                    LoggerManager.Logger("ERROR", "步骤自身回调执行异常: " + e.getMessage());
                }
            }

            Consumer<Object> registeredCallback = stepCallbacks.get(step.id);
            if (registeredCallback != null) {
                try {
                    registeredCallback.accept(step.result);
                } catch (Exception e) {
                    LoggerManager.Logger("ERROR", "注册的回调执行异常: " + e.getMessage());
                }
            }

            currentStep++;
            executeNextStep();
            return;
        }

        if (currentStep >= steps.size()) {
            LoggerManager.Logger("DEBUG", "所有步骤已完成");
            sequenceFuture.complete(null);
            return;
        }

        step = steps.get(currentStep);
        LoggerManager.Logger("DEBUG", "executeNextStep 准备执行步骤: " + currentStep + ", ID: " + step.id);

        if (step.result != null && !step.skipUI) {
            LoggerManager.Logger("DEBUG", "步骤已有结果但未跳过UI: " + step.id);

            if (step.choiceStep != null) {
                int idx = (Integer) step.result;
                Consumer<Object> callback = stepCallbacks.get(step.id);
                if (callback != null) {
                    callback.accept(idx);
                }
                step.skipUI = true;
                currentStep++;
                executeNextStep();
                return;
            } else if (step.inputPrompt != null && !step.isDateInput) {
                String txt = (String) step.result;
                Consumer<Object> callback = stepCallbacks.get(step.id);
                if (callback != null) {
                    callback.accept(txt);
                }
                step.skipUI = true;
                currentStep++;
                executeNextStep();
                return;
            } else if (step.isDateInput) {
                String date = (String) step.result;
                Consumer<Object> callback = stepCallbacks.get(step.id);
                if (callback != null) {
                    callback.accept(date);
                }
                step.skipUI = true;
                currentStep++;
                executeNextStep();
                return;
            } else if (step.isMapSelector) {
                int btnId = (Integer) step.result;
                Consumer<Object> callback = stepCallbacks.get(step.id);
                if (callback != null) {
                    callback.accept(btnId);
                }
                step.skipUI = true;
                currentStep++;
                executeNextStep();
                return;
            }
        }

        CompletableFuture<?> stepFuture = createStepFuture(step);

        final String stepId = step.id;
        final int currentStepIndex = currentStep;

        stepFuture.whenComplete((result, ex) -> {
            LoggerManager.Logger("DEBUG", "步骤完成，ID: " + stepId + ", 结果: " + result);

            if (ex != null) {
                LoggerManager.Logger("ERROR", "步骤执行异常: " + ex.getMessage());
                sequenceFuture.completeExceptionally(ex);
                return;
            }

            if (currentStepIndex >= steps.size()) {
                LoggerManager.Logger("WARN", "步骤索引越界，可能已被修改");
                return;
            }

            DialogStep completedStep = steps.get(currentStepIndex);
            if (!completedStep.id.equals(stepId)) {
                LoggerManager.Logger("WARN", "步骤ID不匹配，预期: " + stepId + ", 实际: " + completedStep.id);
            }

            completedStep.result = result;
            completedStep.skipUI = true;

            if (completedStep.completionCallback != null) {
                try {
                    completedStep.completionCallback.accept(result);
                } catch (Exception e) {
                    LoggerManager.Logger("ERROR", "步骤自身回调执行异常: " + e.getMessage());
                }
            }

            Consumer<Object> registeredCallback = stepCallbacks.get(stepId);
            if (registeredCallback != null) {
                try {
                    registeredCallback.accept(result);
                } catch (Exception e) {
                    LoggerManager.Logger("ERROR", "注册的回调执行异常: " + e.getMessage());
                }
            }

            boolean advance = true;
            synchronized (this) {
                if (jumpRequested) {
                    jumpRequested = false;
                    advance = false;
                } else if (currentStep == currentStepIndex) {
                    currentStep++;
                } else {
                    advance = false;
                    LoggerManager.Logger("WARN", "currentStep已变化，预期: " + currentStepIndex + ", 实际: " + currentStep);
                }
            }

            if (advance) {
                Platform.runLater(() -> {
                    LoggerManager.Logger("DEBUG", "准备执行下一步");
                    executeNextStep();
                });
            }
        });
    }

    private CompletableFuture<?> createStepFuture(DialogStep step) {
        LoggerManager.Logger("DEBUG", "创建步骤Future，ID: " + step.id);

        if (step.isMapSelector) {
            LoggerManager.Logger("DEBUG", "创建地图选择器Future");
            return MapManager.getInstance()
                    .showMapSelector(step.am, step.ab)
                    .thenApply(id -> (Object) id);
        } else if (step.message != null) {
            return chatManager.showDialog(step.speaker, step.message, step.voicePath, step.standeeImagePath);
        } else if (step.inputPrompt != null) {
            if (step.isDateInput) {
                return chatManager.showDateInputDialog(step.inputPrompt);
            } else {
                return chatManager.showInputDialog(step.inputPrompt, step.simulatedText);
            }
        } else if (step.choiceStep != null) {
            return chatManager.showChoiceDialog(step.speaker, step.choiceStep.prompt, step.choiceStep.choices);
        } else if (step.isShowImage) {
            return chatManager.showStoryImage(step.imagePath);
        } else if (step.isHideImage) {
            return chatManager.hideStoryImage();
        } else if (step.isReturnStep) {
            chatManager.returnAnchor(step.returnAnchorName);
            return CompletableFuture.completedFuture(null);
        } else {
            LoggerManager.Logger("WARN", "未知步骤类型，返回已完成的Future");
            return CompletableFuture.completedFuture(null);
        }
    }

    public DialogSequence insertChain(Function<Object, List<DialogStep>> condition) {
        if (lastAddedStep == null) {
            throw new IllegalStateException("必须先有一步才能 insertChain");
        }

        final DialogStep anchorStep = lastAddedStep;
        LoggerManager.Logger("DEBUG", "insertChain绑定到锚点步骤，ID: " + anchorStep.id +
                ", 类型: " + (anchorStep.isMapSelector ? "地图选择器" : "其他"));

        stepCallbacks.put(anchorStep.id, result -> {
            LoggerManager.Logger("DEBUG", "insertChain回调触发，锚点步骤ID: " + anchorStep.id + ", 结果: " + result);

            List<DialogStep> toAdd = condition.apply(result);
            if (toAdd != null && !toAdd.isEmpty()) {
                int anchorIndex = steps.indexOf(anchorStep);
                if (anchorIndex == -1) {
                    LoggerManager.Logger("ERROR", "找不到锚点步骤: " + anchorStep.id);
                    return;
                }

                int insertPosition = anchorIndex + 1;
                LoggerManager.Logger("DEBUG", "在位置 " + insertPosition + " 插入 " + toAdd.size() + " 个步骤");

                for (DialogStep s : toAdd) s.insertedByChain = true;
                steps.addAll(insertPosition, toAdd);

                if (currentStep >= insertPosition) {
                    int oldStep = currentStep;
                    currentStep += toAdd.size();
                    LoggerManager.Logger("DEBUG", "调整currentStep: " + oldStep + " -> " + currentStep);
                }

                if (!toAdd.isEmpty()) {
                    lastAddedStep = toAdd.get(toAdd.size() - 1);
                }

                if (currentStep == anchorIndex + 1) {
                    LoggerManager.Logger("DEBUG", "锚点步骤刚完成，立即执行下一步");
                    Platform.runLater(this::executeNextStep);
                }
            } else {
                LoggerManager.Logger("DEBUG", "insertChain返回空列表，不插入步骤");
            }
        });

        return this;
    }

    public static DialogStep createReturnStep(String anchorName) {
        DialogStep step = new DialogStep(null, null, null, null, null);
        step.isReturnStep = true;
        step.returnAnchorName = anchorName;
        return step;
    }

    public DialogSequence setAnchor(String name) {
        chatManager.setAnchor(name, steps.size(), steps.size());
        return this;
    }

    public void returnAnchor(String name) {
        chatManager.returnAnchor(name);
    }

    public int getStepCount() {
        return steps.size();
    }

    public DialogStep getStep(int i) {
        return steps.get(i);
    }

    public Object getStepResult(int i) {
        return i >= 0 && i < steps.size() ? steps.get(i).result : null;
    }

    public static class DialogStep {
        public boolean isMapSelector = false;
        public boolean isShowImage;
        public boolean isHideImage;
        public String imagePath;
        List<String> am;
        List<Integer> ab;
        public String speaker, message, inputPrompt, voicePath;
        public String standeeImagePath;
        public ChoiceStep choiceStep;
        public Object result;
        public boolean skipUI;
        public boolean isDateInput;
        public boolean isReturnStep;
        public String returnAnchorName;
        public String simulatedText;
        public String id;
        public boolean insertedByChain;
        public Consumer<Object> completionCallback;

        DialogStep(String sp, String msg, String ip, ChoiceStep cs, String vp) {
            this(sp, msg, ip, cs, vp, false);
        }

        DialogStep(String sp, String msg, String ip, ChoiceStep cs, String vp, boolean date) {
            this(sp, msg, ip, cs, vp, date, false, null, null);
        }

        DialogStep(String sp, String msg, String ip, ChoiceStep cs, String vp,
                   boolean date, boolean mapSelector, List<String> allowedMaps,
                   List<Integer> allowedButtons) {
            speaker = sp;
            message = msg;
            inputPrompt = ip;
            choiceStep = cs;
            voicePath = vp;
            isDateInput = date;
            isMapSelector = mapSelector;
            am = allowedMaps;
            ab = allowedButtons;
            this.id = UUID.randomUUID().toString();
        }
    }

    public static class ChoiceStep {
        public String prompt;
        List<String> choices;

        ChoiceStep(String p, List<String> c) {
            prompt = p;
            choices = c;
        }
    }
}
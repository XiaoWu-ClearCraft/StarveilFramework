package com.xiaowu.game.starveil.game.quest;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.overlay.NotificationManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务管理器 - 负责管理游戏任务（单任务模式）
 */
public class QuestManager {
    private static QuestManager instance;
    private final Map<String, QuestDefinition> questDefinitions;
    private QuestState currentQuest;
    private final Gson gson;

    private QuestManager() {
        questDefinitions = new HashMap<>();
        gson = new GsonBuilder().create();
        loadQuestDefinitions();
    }

    public static QuestManager getInstance() {
        if (instance == null) {
            instance = new QuestManager();
        }
        return instance;
    }

    /**
     * 从 JSON 文件加载任务定义
     */
    private void loadQuestDefinitions() {
        try (InputStream is = ResourceResolver.getResourceAsStream("starveil:data/config/quests.json")) {
            if (is == null) {
                LoggerManager.Logger("WARNING", "任务定义文件不存在: starveil:data/config/quests.json");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            Type type = new TypeToken<Map<String, QuestDefinition>>(){}.getType();
            Map<String, QuestDefinition> loaded = gson.fromJson(reader, type);

            if (loaded != null) {
                questDefinitions.putAll(loaded);
                LoggerManager.Logger("INFO", "加载了 " + questDefinitions.size() + " 个任务定义");
            }
        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "加载任务定义失败: " + e.getMessage());
        }
    }

    /**
     * 接受任务（通过ID）
     * @param questId 任务ID
     */
    public void acceptQuest(String questId) {
        if (currentQuest != null) {
            LoggerManager.Logger("WARNING", "已有进行中的任务: " + currentQuest.id);
            return;
        }

        QuestDefinition definition = questDefinitions.get(questId);
        if (definition == null) {
            LoggerManager.Logger("WARNING", "未找到任务定义: " + questId);
            return;
        }

        currentQuest = new QuestState(questId, definition);
        LoggerManager.Logger("INFO", "接受任务: " + definition.name);

        // 显示任务接受通知
        NotificationManager.getInstance().showNotification(
            "新的任务!",
            definition.name,
            definition.iconPath,
            5
        );
    }

    /**
     * 完成当前任务
     */
    public void completeQuest() {
        if (currentQuest == null) {
            LoggerManager.Logger("WARNING", "没有进行中的任务");
            return;
        }

        String questName = currentQuest.definition.name;
        String iconPath = currentQuest.definition.iconPath;

        currentQuest = null;
        LoggerManager.Logger("INFO", "完成任务: " + questName);

        // 显示任务完成通知
        NotificationManager.getInstance().showNotification(
            "任务完成!",
            questName,
            iconPath,
            5
        );
    }

    /**
     * 设置任务进度
     * @param progress 当前进度
     */
    public void setQuestProgress(int progress) {
        if (currentQuest == null) {
            LoggerManager.Logger("WARNING", "没有进行中的任务");
            return;
        }

        currentQuest.currentProgress = progress;
        LoggerManager.Logger("DEBUG", "设置任务进度: " + currentQuest.definition.name + " -> " + progress + "/" + currentQuest.definition.maxProgress);

        // 检查是否完成
        if (currentQuest.currentProgress >= currentQuest.definition.maxProgress) {
            completeQuest();
        }
    }

    /**
     * 增加任务进度
     * @param amount 增加的数量
     */
    public void addQuestProgress(int amount) {
        if (currentQuest == null) {
            LoggerManager.Logger("WARNING", "没有进行中的任务");
            return;
        }

        currentQuest.currentProgress += amount;
        LoggerManager.Logger("DEBUG", "增加任务进度: " + currentQuest.definition.name + " -> " + currentQuest.currentProgress + "/" + currentQuest.definition.maxProgress);

        // 检查是否完成
        if (currentQuest.currentProgress >= currentQuest.definition.maxProgress) {
            completeQuest();
        }
    }

    /**
     * 更新任务详细内容
     * @param details 新的详细内容
     */
    public void updateQuestDetails(String details) {
        if (currentQuest == null) {
            LoggerManager.Logger("WARNING", "没有进行中的任务");
            return;
        }

        currentQuest.definition.details = details;
        LoggerManager.Logger("DEBUG", "更新任务详情: " + currentQuest.definition.name);
    }

    /**
     * 获取当前任务
     */
    public QuestState getCurrentQuest() {
        return currentQuest;
    }

    /**
     * 检查是否有进行中的任务
     */
    public boolean hasActiveQuest() {
        return currentQuest != null;
    }

    /**
     * 放弃当前任务
     */
    public void abandonQuest() {
        if (currentQuest == null) {
            LoggerManager.Logger("WARNING", "没有进行中的任务");
            return;
        }

        String questName = currentQuest.definition.name;
        currentQuest = null;
        LoggerManager.Logger("INFO", "放弃任务: " + questName);
    }

    /**
     * 清除当前任务（返回菜单/新开游戏时调用）
     */
    public void clearQuest() {
        currentQuest = null;
    }

    /**
     * 任务定义类（从JSON加载）
     */
    public static class QuestDefinition {
        public String name;
        public String description;
        public String details;
        public int maxProgress;
        public String iconPath;

        public QuestDefinition() {
            this.maxProgress = 0;
            this.iconPath = "starveil:textures/icons/app-icon.png";
        }
    }

    /**
     * 任务状态类（运行时）
     */
    public static class QuestState {
        public String id;
        public QuestDefinition definition;
        public int currentProgress;

        public QuestState(String id, QuestDefinition definition) {
            this.id = id;
            this.definition = definition;
            this.currentProgress = 0;
        }

        public double getProgressPercentage() {
            return definition.maxProgress > 0 ? (double) currentProgress / definition.maxProgress : 0;
        }

        public boolean isCompleted() {
            return currentProgress >= definition.maxProgress;
        }
    }
}
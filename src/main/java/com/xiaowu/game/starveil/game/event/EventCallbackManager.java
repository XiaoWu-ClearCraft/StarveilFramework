package com.xiaowu.game.starveil.game.event;

import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.story.StorySignals;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 全局事件回调管理器
 * 用于管理游戏中各种事件完成后的回调处理
 */
public class EventCallbackManager {
    private static EventCallbackManager instance;

    // 事件类型枚举
    public enum EventType {
        WORLD_LOADED,       // 世界加载完成（支持指定世界ID）
        TUTORIAL_COMPLETED  // 教程完成
    }

    // 带世界ID的回调（用于 WORLD_LOADED 事件）
    private static class WorldLoadedCallback {
        final Integer worldId;  // null 表示任何世界
        final Consumer<Integer> callback;

        WorldLoadedCallback(Integer worldId, Consumer<Integer> callback) {
            this.worldId = worldId;
            this.callback = callback;
        }
    }

    // 回调列表
    private final List<WorldLoadedCallback> worldLoadedCallbacks = new ArrayList<>();
    private final List<Consumer<Void>> tutorialCompletedCallbacks = new ArrayList<>();

    private EventCallbackManager() {
    }

    public static EventCallbackManager getInstance() {
        if (instance == null) {
            instance = new EventCallbackManager();
        }
        return instance;
    }

    /**
     * 注册世界加载完成回调（指定世界ID）
     * @param worldId 世界ID，null 表示任何世界（通配符）
     * @param callback 回调函数，参数为加载的世界ID
     */
    public void registerWorldLoadedCallback(Integer worldId, Consumer<Integer> callback) {
        if (callback != null) {
            worldLoadedCallbacks.add(new WorldLoadedCallback(worldId, callback));

            // 检查玩家当前是否已在目标世界（如果是null，则在任何世界中都立即执行）
            if (shouldExecuteImmediately(worldId)) {
                WorldMap worldMap = GameInstance.getWorldMap();
                if (worldMap != null) {
                    Integer currentWorldId = worldMap.getCurrentWorldId();
                    if (currentWorldId != null) {
                        try {
                            callback.accept(currentWorldId);
                            LoggerManager.Logger("DEBUG",
                                "立即执行世界加载回调 (worldId=" + worldId + ", current=" + currentWorldId + ")");
                        } catch (Exception e) {
                            LoggerManager.Logger("ERROR",
                                "执行世界加载回调失败: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断是否应该立即执行回调
     * @param targetWorldId 目标世界ID，null 表示通配符（匹配任何世界）
     * @return 是否应该立即执行
     */
    private boolean shouldExecuteImmediately(Integer targetWorldId) {
        WorldMap worldMap = GameInstance.getWorldMap();
        if (worldMap == null) {
            return false;
        }

        // 检查世界窗口是否有可见的地图元素
        javafx.scene.layout.Pane worldPane = worldMap.getWorld();
        if (worldPane == null || worldPane.getChildren().isEmpty()) {
            return false;
        }

        // 检查是否有背景图片或其他地图元素（非空气墙）
        boolean hasMapElements = false;
        for (javafx.scene.Node node : worldPane.getChildren()) {
            // 排除空气墙（透明且无描边的Rectangle）
            if (node instanceof javafx.scene.shape.Rectangle) {
                javafx.scene.shape.Rectangle rect = (javafx.scene.shape.Rectangle) node;
                if (rect.getFill() == javafx.scene.paint.Color.TRANSPARENT && rect.getStroke() == null) {
                    continue;
                }
            }
            // 排除坐标文本
            if (node instanceof javafx.scene.text.Text) {
                continue;
            }
            // 找到其他元素（背景图片、NPC、玩家等）
            hasMapElements = true;
            break;
        }

        if (!hasMapElements) {
            return false;
        }

        // null 表示通配符，匹配任何已加载且有元素的世界
        if (targetWorldId == null) {
            Integer currentWorldId = worldMap.getCurrentWorldId();
            return currentWorldId != null;
        }

        // 检查是否匹配特定的世界ID
        Integer currentWorldId = worldMap.getCurrentWorldId();
        return targetWorldId.equals(currentWorldId);
    }

    /**
     * 触发世界加载完成事件
     * @param worldId 加载的世界ID
     */
    public void triggerWorldLoaded(Integer worldId) {
        for (WorldLoadedCallback wc : worldLoadedCallbacks) {
            if (wc.worldId == null || wc.worldId.equals(worldId)) {
                try {
                    wc.callback.accept(worldId);
                } catch (Exception e) {
                    LoggerManager.Logger("ERROR",
                        "执行世界加载回调失败 (worldId=" + worldId + "): " + e.getMessage());
                }
            }
        }
        // 同时以信号形式广播，供剧情脚本 await
        StorySignals.signal(StorySignals.WORLD_LOADED);
        StorySignals.signal(StorySignals.WORLD_LOADED + ":" + worldId);
    }

    /**
     * 注册教程完成回调
     * @param callback 回调函数
     */
    public void registerTutorialCompletedCallback(Consumer<Void> callback) {
        if (callback != null) {
            tutorialCompletedCallbacks.add(callback);
        }
    }

    /**
     * 触发教程完成事件
     */
    public void triggerTutorialCompleted() {
        // 使用迭代器遍历，以便在执行后移除回调
        java.util.Iterator<Consumer<Void>> iterator = tutorialCompletedCallbacks.iterator();
        while (iterator.hasNext()) {
            Consumer<Void> callback = iterator.next();

            try {
                callback.accept(null);
            } catch (Exception e) {
                LoggerManager.Logger("ERROR",
                    "执行教程完成回调失败: " + e.getMessage());
            }

            // 执行完后自动移除这个回调
            iterator.remove();
        }

        // 同时以信号形式广播，剧情脚本可以直接 s.awaitTutorial() 等待
        StorySignals.signal(StorySignals.TUTORIAL_COMPLETED);
    }

    // ==================== 剧情信号（供章节脚本使用） ====================

    /**
     * 触发一个自定义剧情信号，唤醒所有 {@code StoryScript.await(name)} 中的脚本。
     *
     * <p>章节可以在任意回调里（地图事件、UI 按钮、玩家死亡……）调用它。
     */
    public void signal(String name) {
        StorySignals.signal(name);
    }

    /** 清除某个信号的「已触发」状态。 */
    public void resetSignal(String name) {
        StorySignals.reset(name);
    }

    /** 新开游戏 / 读档时清空所有剧情信号。 */
    public void resetAllSignals() {
        StorySignals.resetAll();
    }

    /**
     * 移除世界加载完成回调
     * @param callback 回调函数
     */
    public void unregisterWorldLoadedCallback(Consumer<Integer> callback) {
        worldLoadedCallbacks.removeIf(wc -> wc.callback == callback);
    }

    /**
     * 移除教程完成回调
     * @param callback 回调函数
     */
    public void unregisterTutorialCompletedCallback(Consumer<Void> callback) {
        tutorialCompletedCallbacks.remove(callback);
    }

    /**
     * 清空所有世界加载完成回调
     */
    public void clearWorldLoadedCallbacks() {
        worldLoadedCallbacks.clear();
    }

    /**
     * 清空所有教程完成回调
     */
    public void clearTutorialCompletedCallbacks() {
        tutorialCompletedCallbacks.clear();
    }

    /**
     * 清空所有回调
     */
    public void clearAllCallbacks() {
        worldLoadedCallbacks.clear();
        tutorialCompletedCallbacks.clear();
    }

    /**
     * 获取世界加载完成回调数量
     * @param worldId 世界ID，null 表示获取所有
     * @return 回调数量
     */
    public int getWorldLoadedCallbackCount(Integer worldId) {
        if (worldId == null) {
            return worldLoadedCallbacks.size();
        }
        return (int) worldLoadedCallbacks.stream()
            .filter(wc -> wc.worldId == null || wc.worldId.equals(worldId))
            .count();
    }

    /**
     * 获取教程完成回调数量
     * @return 回调数量
     */
    public int getTutorialCompletedCallbackCount() {
        return tutorialCompletedCallbacks.size();
    }
}
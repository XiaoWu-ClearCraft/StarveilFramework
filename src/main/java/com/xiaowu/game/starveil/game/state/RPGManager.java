package com.xiaowu.game.starveil.game.state;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.infrastructure.audio.BGMManager;
import com.xiaowu.game.starveil.infrastructure.persistence.SaveManager;
import com.xiaowu.game.starveil.game.EventTest;
import com.xiaowu.game.starveil.game.story.StoryScripts;
import com.xiaowu.game.starveil.game.story.TutorialManager;
import com.xiaowu.game.starveil.game.quest.QuestManager;
import com.xiaowu.game.starveil.ui.overlay.PopupManager;
import com.xiaowu.game.starveil.config.GameConstants;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.Objects;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class RPGManager extends Application {

    @Override
    public void start(Stage stage) {

        // 清除旧实例的死亡UI
        GameInstance.clearOldInstanceDeathUI();

        // 清除待执行的教程（开新存档时不应用旧的教程）
        TutorialManager.getInstance().reset();

        // 清除上一局残留的剧情（正在播放/排队的脚本、剧情信号、对话界面）
        StoryScripts.resetRuntime();

        // 清除上一次游戏残留的任务目标
        QuestManager.getInstance().clearQuest();

        GameManager gameManager = GameManager.getInstance();
        // 进游戏前按用户保存的设置同步比例/全屏（GameManager 在 Menu 阶段已初始化）
        gameManager.applyWindowSettings();
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root, 1200, 675);
        // 不重复初始化 GameManager，仅将新场景挂载到已有的 rootContainer（含通知/弹窗/效果层）
        gameManager.updateScene(scene);
        stage.setScene(scene);
        stage.setTitle(GameConstants.gameWindowTitle());
        // 窗口图标由内容提供；未提供时保持系统默认图标。
        // 原实现用 Objects.requireNonNull 包着，缺图直接 NPE。
        String iconPath = com.xiaowu.game.starveil.infrastructure.ContentConfig.appIcon();
        if (iconPath != null && !iconPath.isEmpty()) {
            try (java.io.InputStream iconStream = ResourceResolver.getResourceAsStream(iconPath)) {
                if (iconStream != null) {
                    stage.getIcons().add(new Image(iconStream));
                }
            } catch (Exception ignored) {
                // 图标加载失败不该拦住启动
            }
        }
        stage.show();

        // 创建游戏实例
        GameInstance gameInstance = new GameInstance(root, scene);
        gameInstance.initialize();
        // 播放BGM
        BGMManager.getInstance().playRandomBGM();

        new EventTest().setupEventListeners();
    }

    /**
     * 从存档启动游戏
     * @param slotIndex 存档槽位索引
     */
    public void startGameFromSave(int slotIndex) {
        try {
            // 清除旧实例的死亡UI
            GameInstance.clearOldInstanceDeathUI();

            // 读档前先掐掉上一局残留的剧情
            StoryScripts.resetRuntime();

            // 获取当前舞台
            Stage stage = GameManager.getInstance().getPrimaryStage();
            if (stage == null) {
                throw new IllegalStateException("无法获取舞台");
            }

            // 创建新的游戏实例
            BorderPane root = new BorderPane();
            Scene scene = new Scene(root, 1200, 675);

            // 进游戏前按用户保存的设置同步比例/全屏
            GameManager.getInstance().applyWindowSettings();
            // 不重复初始化 GameManager，仅将新场景挂载到已有的 rootContainer（含通知/弹窗/效果层）
            GameManager.getInstance().updateScene(scene);
            stage.setScene(scene);
            stage.setTitle(GameConstants.gameWindowTitle());
            stage.show();

            // 创建游戏实例
            GameInstance gameInstance = new GameInstance(root, scene);
            gameInstance.initialize();

            // 从存档加载数据
            SaveManager.getInstance().loadGameAndStart(slotIndex, gameInstance);

            // 播放BGM
            BGMManager.getInstance().playRandomBGM();

            // 设置事件监听器
            new EventTest().setupEventListeners();

            // 显示加载成功通知
            PopupManager.getInstance().showPopup(
                "加载成功",
                "已从槽位 " + (slotIndex + 1) + " 加载存档"
            );

        } catch (Exception e) {
            Logger("ERROR", "从存档启动游戏失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
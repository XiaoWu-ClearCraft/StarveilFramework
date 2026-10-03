package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.ui.core.GameUI;
import com.xiaowu.game.starveil.ui.overlay.NotificationManager;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.concurrent.CompletableFuture;

/**
 * 游戏世界 / UI / 输入 API。
 */
public class StarveilGame {

    private static final StarveilGame INSTANCE = new StarveilGame();

    private StarveilGame() {
    }

    public static StarveilGame getInstance() {
        return INSTANCE;
    }

    private GameInstance getGame() {
        return GameInstance.getCurrentInstance();
    }

    // ==================== 世界 ====================

    public WorldMap getWorld() {
        return GameInstance.getWorldMap();
    }

    public Pane getWorldPane() {
        WorldMap w = getWorld();
        return w != null ? w.getWorld() : null;
    }

    /**
     * 切换地图（返回异步任务，可观察完成状态）。
     */
    public CompletableFuture<Void> switchMap(String mapFile) {
        WorldMap w = getWorld();
        return w != null ? w.switchMap(mapFile) : CompletableFuture.completedFuture(null);
    }

    /**
     * 全部 NPC 实体 id（供插件按组件查询）。
     */
    public int[] getNpcEntities() {
        WorldMap w = getWorld();
        return w != null ? w.getNpcEntityIds() : new int[0];
    }

    public boolean isWorldPaused() {
        WorldMap w = getWorld();
        return w != null && w.isPaused();
    }

    public void setWorldPaused(boolean paused) {
        WorldMap w = getWorld();
        if (w != null) w.setPaused(paused);
    }

    // ==================== UI ====================

    /**
     * 在游戏 UI 中央显示一条消息（支持已存在的 {color} 标签）。
     */
    public void sendMessage(String text) {
        GameUI.getInstance().addMessage(text);
    }

    /**
     * 显示右上角通知。
     *
     * @param title 标题（发送者）
     * @param message 内容
     * @param iconPath 图标资源路径（可 null）
     * @param seconds 显示时长（秒）
     */
    public void showNotification(String title, String message, String iconPath, double seconds) {
        NotificationManager.getInstance().showNotification(title, message, iconPath, seconds);
    }

    /**
     * 向游戏 UI 容器添加自定义节点。
     */
    public void addCustomUI(Node node) {
        GameUI.getInstance().addCustomUI(node);
    }

    // ==================== 输入 ====================

    /**
     * 注册按键按下回调（按功能名，如 "MOVE_UP"、"MAGIC_ATTACK"）。
     */
    public void onKeyPressed(String function, InputHandler.KeyEventHandler handler) {
        InputHandler ih = GameInstance.getInputHandlerStatic();
        if (ih != null) ih.onKeyPressed(function, handler);
    }

    /**
     * 注册按键释放回调。
     */
    public void onKeyReleased(String function, InputHandler.KeyEventHandler handler) {
        InputHandler ih = GameInstance.getInputHandlerStatic();
        if (ih != null) ih.onKeyReleased(function, handler);
    }

    public InputHandler getInputHandler() {
        return GameInstance.getInputHandlerStatic();
    }
}
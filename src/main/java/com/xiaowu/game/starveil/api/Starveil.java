package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.input.InputHandler;

/**
 * 游戏核心 API — 面向插件的统一访问入口（OOP 门面）。
 *
 * <p>插件无需感知具体实现类，统一通过此包访问游戏数据与能力。
 * 主要能力分区：
 * <ul>
 *   <li>{@link StarveilData} — 全局配置（DataManager）与法阵/魔力开关</li>
 *   <li>{@link StarveilPlayer} — 玩家实体（生命/体力/魔力/位置/背包）</li>
 *   <li>{@link StarveilResources} — 标准资源加载（命名空间 starveil:xxx）</li>
 *   <li>{@link StarveilGame} — 游戏实例、世界地图、输入、UI 消息与提示</li>
 *   <li>{@link StarveilMagic} — 法阵（魔力）系统开关与会话魔力值</li>
 * </ul>
 */
public final class Starveil {

    /** 命名空间写法前缀（资源引用使用） */
    public static final String NAMESPACE = "starveil";

    private static volatile boolean initialized = false;

    private Starveil() {
    }

    /**
     * 获取当前游戏实例（若未进入游戏返回 null）。
     */
    public static GameInstance getGameInstance() {
        return GameInstance.getCurrentInstance();
    }

    /**
     * 获取输入处理器。
     */
    public static InputHandler getInputHandler() {
        return GameInstance.getInputHandlerStatic();
    }

    /**
     * API 层已就绪（启动后即置位，可用作插件初始化判断）。
     */
    public static boolean isReady() {
        return initialized;
    }

    public static void markReady() {
        initialized = true;
    }

    /**
     * 数据访问门面。
     */
    public static StarveilData data() {
        return StarveilData.getInstance();
    }

    /**
     * 玩家门面。
     */
    public static StarveilPlayer player() {
        return StarveilPlayer.getInstance();
    }

    /**
     * 资源门面。
     */
    public static StarveilResources resources() {
        return StarveilResources.getInstance();
    }

    /**
     * 游戏门面。
     */
    public static StarveilGame game() {
        return StarveilGame.getInstance();
    }

    /**
     * 法阵（魔力）门面。
     */
    public static StarveilMagic magic() {
        return StarveilMagic.getInstance();
    }
}
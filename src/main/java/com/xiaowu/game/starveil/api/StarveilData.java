package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;

import java.util.Map;

/**
 * 全局配置 API — 封装 DataManager 读写。
 *
 * <p>法阵（魔力）系统开关 {@code magic_circle.enabled} 也在此暴露：
 * 关闭时魔力值恒为 0、GameUI 不显示魔力条、Q 键法阵无效。
 */
public class StarveilData {

    private static final StarveilData INSTANCE = new StarveilData();

    private StarveilData() {
    }

    public static StarveilData getInstance() {
        return INSTANCE;
    }

    public String get(String key) {
        return DataManager.getString(key);
    }

    public String get(String key, String defaultValue) {
        return DataManager.getString(key, defaultValue);
    }

    public boolean getBoolean(String key) {
        return DataManager.getBoolean(key);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return DataManager.getBoolean(key, defaultValue);
    }

    public int getInt(String key) {
        return DataManager.getInt(key);
    }

    public int getInt(String key, int defaultValue) {
        return DataManager.getInt(key, defaultValue);
    }

    public double getDouble(String key) {
        return DataManager.getDouble(key);
    }

    public double getDouble(String key, double defaultValue) {
        return DataManager.getDouble(key, defaultValue);
    }

    public void set(String key, String value) {
        DataManager.set(key, value);
    }

    public void setBoolean(String key, boolean value) {
        DataManager.setBoolean(key, value);
    }

    public void setInt(String key, int value) {
        DataManager.setInt(key, value);
    }

    public void setDouble(String key, double value) {
        DataManager.setDouble(key, value);
    }

    public void remove(String key) {
        DataManager.remove(key);
    }

    public boolean contains(String key) {
        return DataManager.contains(key);
    }

    /**
     * 获取全部配置（只读副本）。
     */
    public Map<String, String> getAll() {
        return DataManager.getAll();
    }

    /**
     * 法阵（魔力）系统是否启用。
     */
    public boolean isMagicEnabled() {
        return DataManager.getBoolean(GameConstants.MAGIC_CIRCLE_ENABLED_KEY, false);
    }

    /**
     * 设置法阵（魔力）系统开关。
     */
    public void setMagicEnabled(boolean enabled) {
        com.xiaowu.game.starveil.game.MagicSystem.setEnabled(enabled);
    }
}
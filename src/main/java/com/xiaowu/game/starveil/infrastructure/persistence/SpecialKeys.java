package com.xiaowu.game.starveil.infrastructure.persistence;

import com.xiaowu.game.starveil.config.GameConstants;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 特殊键注册表。
 *
 * <p>「特殊键」是 {@link DataManager} 里会被 {@link SaveDataManager} 覆盖的那类键 ——
 * 它们既要有全局默认值（写在 data.dat），又要允许当前存档临时改写。
 *
 * <p><b>命名规范（强制）</b>：
 * <ul>
 *   <li>内置键一律带 {@code starveil:} 前缀，例如 {@code starveil:cant_exit}、
 *       {@code starveil:inertia}。</li>
 *   <li>第三方插件注册的特殊键<b>必须</b>带自己的命名空间前缀，形如
 *       {@code myplugin:some_key}。没有前缀的裸名会被拒绝。</li>
 *   <li>{@code starveil:} 是引擎保留命名空间，第三方不得占用。</li>
 * </ul>
 *
 * <p>强制前缀的收益是「无歧义」：看到 {@code a:b} 就知道有命名空间，
 * 看到裸名就一定是历史遗留或写错了 —— 不必再靠约定去猜。
 */
public final class SpecialKeys {

    /** 引擎保留命名空间。 */
    public static final String ENGINE_NAMESPACE = "starveil";

    public static final String SEPARATOR = ":";

    /** 内置特殊键。 */
    private static final Set<String> BUILT_IN = new LinkedHashSet<>(List.of(
            GameConstants.CANT_EXIT_KEY,
            GameConstants.INERTIA_KEY,
            GameConstants.CHAT_HISTORY_KEEP_CHAPTERS_KEY,
            GameConstants.MAGIC_CIRCLE_ENABLED_KEY,
            GameConstants.SETTING_OVERLAY_ENABLED,
            GameConstants.TUTORIAL_COMPLETED_KEY,
            GameConstants.TUTORIAL_PERSIST_KEY
    ));

    /** 所有已注册的特殊键（内置 + 第三方）。 */
    private static final Set<String> REGISTERED = new LinkedHashSet<>(BUILT_IN);

    /**
     * 历史键别名表 —— <b>刻意留空</b>。
     *
     * <p>框架仍在测试阶段，不做历史数据迁移：老存档与老配置直接删掉即可。
     * 留着迁移表反而会让「裸名曾经合法」这件事继续流传。
     */
    private static final Map<String, String> LEGACY_ALIASES = Map.of();

    private SpecialKeys() {
    }

    // ==================== 注册 ====================

    /**
     * 注册一个第三方特殊键。
     *
     * @return 规范化后的键
     * @throws IllegalArgumentException 键为空、没有命名空间前缀、
     *                                  占用了引擎命名空间、或已被注册
     */
    public static synchronized String register(String key) {
        String k = normalize(key);
        if (k == null) {
            throw new IllegalArgumentException("特殊键不能为空");
        }
        if (isBuiltIn(k)) {
            throw new IllegalArgumentException("内置特殊键不允许重复注册: " + k);
        }
        if (!REGISTERED.add(k)) {
            throw new IllegalArgumentException("特殊键已被注册: " + k);
        }
        return k;
    }

    /** 尝试注册，失败只返回 false。 */
    public static synchronized boolean tryRegister(String key) {
        try {
            register(key);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 校验并规范化：返回 null 表示格式不合法（调用方可据此给出自己的错误信息）。 */
    private static String normalize(String key) {
        if (key == null) return null;
        String k = key.trim();
        if (k.isEmpty()) return null;

        int idx = k.indexOf(SEPARATOR);
        // 必须形如 namespace:name，且两段都非空
        if (idx <= 0 || idx == k.length() - 1) {
            throw new IllegalArgumentException(
                    "特殊键必须带命名空间前缀，形如 myplugin:some_key，收到: " + key);
        }
        if (k.indexOf(SEPARATOR, idx + 1) >= 0) {
            throw new IllegalArgumentException("特殊键只能有一段命名空间: " + key);
        }

        String namespace = k.substring(0, idx);
        if (ENGINE_NAMESPACE.equals(namespace)) {
            throw new IllegalArgumentException(
                    "'" + ENGINE_NAMESPACE + ":' 是引擎保留命名空间，第三方请用自己的前缀: " + key);
        }
        return k;
    }

    // ==================== 查询 ====================

    public static synchronized boolean isSpecial(String key) {
        String k = key == null ? null : key.trim();
        return k != null && REGISTERED.contains(k);
    }

    public static synchronized boolean isBuiltIn(String key) {
        String k = key == null ? null : key.trim();
        return k != null && BUILT_IN.contains(k);
    }

    public static synchronized Set<String> registeredKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTERED));
    }

    /**
     * 规范化键：去空白。
     *
     * <p>框架测试阶段不做历史键迁移，所以这里只规范化，不查别名表。
     */
    public static String migrate(String key) {
        if (key == null) return null;
        return key.trim();
    }

    /** 清空第三方注册（仅测试使用；内置键保留）。 */
    static synchronized void resetForTest() {
        REGISTERED.clear();
        REGISTERED.addAll(BUILT_IN);
    }
}

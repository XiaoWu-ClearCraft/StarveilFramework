package com.xiaowu.game.starveil.infrastructure.persistence;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 特殊键 —— 「会被当前存档覆盖」的那类键。
 *
 * <p><b>这个类现在只是转发层</b>：真正的键定义在 {@link DataKeyRegistry}，
 * 而「特殊」这件事在注册时就写明了 —— 声明时带 {@link DataKeyFlag#PER_SAVE}
 * （对应 {@link DataKeyScope#PER_SAVE}）的键就是特殊键。
 *
 * <p>保留本类是为了不破坏既有调用点与文档中对「特殊键」的称呼，
 * 但新代码应当直接读 {@code DataKey.scope()}，不必再来这里查集合成员关系。
 *
 * <p><b>命名规范（强制，由注册表执行）</b>：
 * <ul>
 *   <li>内置键一律带 {@code starveil:} 前缀，例如 {@code starveil:cant_exit}；</li>
 *   <li>第三方插件必须带自己的命名空间前缀，形如 {@code myplugin:some_key}；</li>
 *   <li>没有前缀的裸名会被拒绝。</li>
 * </ul>
 */
public final class SpecialKeys {

    /** 引擎保留命名空间。 */
    public static final String ENGINE_NAMESPACE = DataKeyRegistry.ENGINE_NAMESPACE;

    public static final String SEPARATOR = DataKeyRegistry.SEPARATOR;

    /**
     * 历史键别名表 —— <b>刻意留空</b>。
     *
     * <p>框架仍在测试阶段，不做历史数据迁移：老存档与老配置直接删掉即可。
     * 留着迁移表反而会让「裸名曾经合法」这件事继续流传。
     */
    private SpecialKeys() {
    }

    // ==================== 查询 ====================

    /** 该键是否为特殊键（即存档作用域）。 */
    public static boolean isSpecial(String key) {
        return DataKeyRegistry.isPerSave(key);
    }

    /** 该键是否由框架自带。 */
    public static boolean isBuiltIn(String key) {
        return DataKeyRegistry.isBuiltIn(key);
    }

    /** 所有特殊键。 */
    public static Set<String> specialKeys() {
        Set<String> result = new LinkedHashSet<>();
        for (String k : DataKeyRegistry.registeredKeys()) {
            if (DataKeyRegistry.isPerSave(k)) {
                result.add(k);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** 所有已注册的键。 */
    public static Set<String> registeredKeys() {
        return DataKeyRegistry.registeredKeys();
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
}

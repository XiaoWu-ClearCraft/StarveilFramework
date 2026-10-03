package com.xiaowu.game.starveil.infrastructure.persistence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 数据键注册表 —— 全框架<b>唯一</b>的键定义来源。
 *
 * <p>键必须先在注册表里声明才能读写。强制注册的收益是「键的归属一眼可辨」：
 * 每个键都带命名空间前缀（{@code starveil:} 为引擎保留，第三方用自己的），
 * 类型与默认值钉在注册处，拼错的键会当场报错而不是静默丢数据。
 *
 * <p>{@link DataKey#of} 与 {@code DataManager.define*} 都走
 * {@link #register(String, String, Object, DataKeyFlag...)}，注册结果是同一个
 * {@link DataKey} 实例 —— 内容重定义框架内置键时，虽然注册表里的定义被替换，
 * 但框架调用点早已持有旧句柄、句柄本身不可变，因此不会读到「半个新定义」。
 */
public final class DataKeyRegistry {

    /** 引擎保留命名空间。 */
    public static final String ENGINE_NAMESPACE = "starveil";

    public static final String SEPARATOR = ":";

    /** 已注册的键：qualified → DataKey。 */
    private static final Map<String, DataKey<?>> REGISTERED = new LinkedHashMap<>();

    /** 内置（框架自带）的键。 */
    private static final Set<String> BUILT_IN = new LinkedHashSet<>();

    private DataKeyRegistry() {
    }

    // ==================== 注册 ====================

    /**
     * 注册一个数据键。同名重复注册会<b>替换</b>旧定义（内容可以重定义框架内置键
     * 的默认值或类型）。
     *
     * @param namespace    命名空间；若 {@code name} 已含前缀则可为 null
     * @param name         键名；也可以直接给完整键名 {@code ns:key}
     * @param defaultValue 默认值，其 java 类型决定键类型
     * @param flags        {@link DataKeyFlag}，可省略
     * @throws IllegalArgumentException 命名空间/键名不合法，或与已有键<b>类型冲突</b>
     *                                  （用同一个键名声明了不同的值类型）
     */
    public static synchronized <T> DataKey<T> register(String namespace,
                                                       String name,
                                                       T defaultValue,
                                                       DataKeyFlag... flags) {
        String qualified = qualify(namespace, name);
        String ns = qualified.substring(0, qualified.indexOf(SEPARATOR));

        DataType type = dataTypeOf(defaultValue);
        if (type == null) {
            throw new IllegalArgumentException(
                    "键 '" + qualified + "' 的默认值为 null，无法推断类型；"
                            + "请给一个非 null 默认值（或用带类型的方法注册）");
        }

        boolean temporary = hasFlag(flags, DataKeyFlag.TEMPORARY);
        boolean readOnly = hasFlag(flags, DataKeyFlag.READ_ONLY);
        DataKeyScope scope = hasFlag(flags, DataKeyFlag.PER_SAVE)
                ? DataKeyScope.PER_SAVE : DataKeyScope.GLOBAL;

        // 类型冲突：同一个键名被声明成两种类型。
        // 这种情况几乎一定是「两处各自声明了同名键」，必须炸掉而不是让后者静默覆盖 ——
        // 否则读的那一侧会一直拿到解析失败的默认值，排查方向完全错。
        DataKey<?> existing = REGISTERED.get(qualified);
        if (existing != null && existing.type() != type) {
            throw new IllegalArgumentException(
                    "键 '" + qualified + "' 已被注册为 " + existing.type()
                            + "，不能再注册为 " + type
                            + "（同一键名只允许一种值类型）");
        }

        DataKey<T> key = new DataKey<>(ns, localName(qualified), qualified, type,
                defaultValue, readOnly, temporary, scope);

        boolean replaced = REGISTERED.put(qualified, key) != null;
        if (replaced) {
            Logger("DEBUG", "键定义已被替换: " + qualified + " (" + type
                    + (readOnly ? ", 只读" : "") + (temporary ? ", 临时" : "") + ")");
        } else {
            Logger("DEBUG", "已注册数据键: " + qualified + " (" + type + ", 默认 "
                    + defaultValue + (readOnly ? ", 只读" : "")
                    + (temporary ? ", 临时" : "") + ")");
        }
        // 只读/临时还额外记在旁表里，避免为了查一个布尔值去拆 DataKey
        // （旁表在 resetForTest 里会按 DataKey 自身的标记重建）
        if (readOnly) {
            READ_ONLY_KEYS.add(qualified);
        } else {
            READ_ONLY_KEYS.remove(qualified);
        }
        if (temporary) {
            TEMPORARY_KEYS.add(qualified);
        } else {
            TEMPORARY_KEYS.remove(qualified);
        }
        return key;
    }

    /**
     * 把「此刻已注册的全部键」标记为框架自带。
     *
     * <p>由 {@link FrameworkDataKeys} 的最后一个静态字段调用 —— 那一刻框架内置键
     * 刚好全部声明完毕，因此不需要逐个手写 {@code markBuiltIn}。
     */
    static synchronized boolean markBuiltInDuring() {
        BUILT_IN.addAll(REGISTERED.keySet());
        return true;
    }

    /** 当前全部定义的快照（`FrameworkDataKeys` 用它留一份内置键的原始定义）。 */
    static synchronized Map<String, DataKey<?>> snapshotRegistered() {
        return Map.copyOf(REGISTERED);
    }

    // ==================== 查询 ====================

    public static synchronized boolean isRegistered(String qualified) {
        return qualified != null && REGISTERED.containsKey(qualified.trim());
    }

    /** 取回定义；未注册返回 null。 */
    @SuppressWarnings("unchecked")
    public static synchronized <T> DataKey<T> lookup(String qualified) {
        if (qualified == null) {
            return null;
        }
        return (DataKey<T>) REGISTERED.get(qualified.trim());
    }

    /** 未注册时抛出异常，便于调用方把「键没声明」变成显式失败。 */
    public static synchronized DataKey<?> require(String qualified) {
        DataKey<?> key = lookup(qualified);
        if (key == null) {
            throw new IllegalArgumentException(
                    "数据键未注册: '" + qualified + "'（请先用 DataManager.define* 或 DataKey.of 声明）");
        }
        return key;
    }

    public static synchronized DataType typeOf(String qualified) {
        DataKey<?> key = lookup(qualified);
        return key == null ? null : key.type();
    }

    public static synchronized boolean isTemporary(String qualified) {
        return qualified != null && TEMPORARY_KEYS.contains(qualified.trim());
    }

    public static synchronized boolean isReadOnly(String qualified) {
        return qualified != null && READ_ONLY_KEYS.contains(qualified.trim());
    }

    public static synchronized boolean isPerSave(String qualified) {
        DataKey<?> key = lookup(qualified);
        return key != null && key.scope() == DataKeyScope.PER_SAVE;
    }

    /** 是否为框架自带键（测试与文档用）。 */
    public static synchronized boolean isBuiltIn(String qualified) {
        return qualified != null && BUILT_IN.contains(qualified.trim());
    }

    public static synchronized Set<String> registeredKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTERED.keySet()));
    }

    // ==================== 内部 ====================

    private static final Set<String> TEMPORARY_KEYS = new LinkedHashSet<>();
    private static final Set<String> READ_ONLY_KEYS = new LinkedHashSet<>();

    private static boolean hasFlag(DataKeyFlag[] flags, DataKeyFlag wanted) {
        if (flags == null) {
            return false;
        }
        for (DataKeyFlag f : flags) {
            if (f == wanted) {
                return true;
            }
        }
        return false;
    }

    /** 命名空间 + 键名 → {@code namespace:name}，并校验格式。 */
    private static String qualify(String namespace, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("数据键名不能为空");
        }
        String n = name.trim();

        // 直接给了完整键名
        if (n.indexOf(SEPARATOR) >= 0) {
            if (namespace != null && !namespace.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "键名 '" + n + "' 已含命名空间，不要再单独传 namespace='" + namespace + "'");
            }
            int idx = n.indexOf(SEPARATOR);
            if (idx <= 0 || idx == n.length() - 1) {
                throw new IllegalArgumentException(
                        "数据键必须形如 namespace:name，收到: '" + n + "'");
            }
            if (n.indexOf(SEPARATOR, idx + 1) >= 0) {
                throw new IllegalArgumentException("数据键只能有一段命名空间: '" + n + "'");
            }
            return n;
        }

        if (namespace == null || namespace.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "数据键必须带命名空间前缀，形如 starveil:some_key 或 myplugin:some_key，收到: '" + name
                            + "'（引擎保留命名空间为 '" + ENGINE_NAMESPACE + "'）");
        }
        String ns = namespace.trim();
        if (ns.indexOf(SEPARATOR) >= 0 || ns.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("命名空间不合法: '" + ns + "'");
        }
        return ns + SEPARATOR + n;
    }

    private static String localName(String qualified) {
        return qualified.substring(qualified.indexOf(SEPARATOR) + 1);
    }

    /** 由默认值的 java 类型推断 {@link DataType}。 */
    static DataType dataTypeOf(Object defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (defaultValue instanceof String) return DataType.STRING;
        if (defaultValue instanceof Boolean) return DataType.BOOLEAN;
        if (defaultValue instanceof Integer) return DataType.INT;
        if (defaultValue instanceof Long) return DataType.LONG;
        if (defaultValue instanceof Double) return DataType.DOUBLE;
        if (defaultValue instanceof Float) return DataType.FLOAT;
        throw new IllegalArgumentException(
                "不支持的数据键类型: " + defaultValue.getClass().getName()
                        + "（只支持 String/Boolean/Integer/Long/Double/Float）");
    }

    /**
     * 仅测试使用：清掉第三方注册，并把被测试改写的内置键定义恢复回来。
     *
     * <p>不试图「清空一切再重新声明内置键」：{@link FrameworkDataKeys} 的静态初始化
     * 一个 JVM 只会跑一次，靠反射去重置它既脆又没必要 —— 内置键在测试开始前就已经
     * 注册好了，测试要的只是「别让本用例的第三方键泄漏给下一个用例」。
     *
     * <p>内置键被测试重定义过（例如改了默认值）时，这里用
     * {@link FrameworkDataKeys#snapshotBuiltIns()} 里的原定义覆盖回去。
     */
    static synchronized void resetForTest() {
        Set<String> builtIn = new LinkedHashSet<>(BUILT_IN);
        REGISTERED.keySet().removeIf(k -> !builtIn.contains(k));
        TEMPORARY_KEYS.removeIf(k -> !builtIn.contains(k));
        READ_ONLY_KEYS.removeIf(k -> !builtIn.contains(k));
        for (Map.Entry<String, DataKey<?>> e : FrameworkDataKeys.snapshotBuiltIns().entrySet()) {
            REGISTERED.put(e.getKey(), e.getValue());
            DataKey<?> key = e.getValue();
            if (key.readOnly()) {
                READ_ONLY_KEYS.add(e.getKey());
            } else {
                READ_ONLY_KEYS.remove(e.getKey());
            }
            if (key.temporary()) {
                TEMPORARY_KEYS.add(e.getKey());
            } else {
                TEMPORARY_KEYS.remove(e.getKey());
            }
        }
    }
}

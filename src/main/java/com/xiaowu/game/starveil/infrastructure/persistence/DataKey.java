package com.xiaowu.game.starveil.infrastructure.persistence;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 一个<b>已注册的数据键</b>的句柄 —— 键名、类型、默认值、作用域和各项标记都在这里。
 *
 * <p><b>为什么要有这个类型</b>：以前键是裸字符串，调用方每处都要自己补默认值
 * （{@code DataManager.getInt("starveil:x", 50)}）。结果是同一个键在不同文件里
 * 可能带着不同的默认值 —— 一处写 50、一处写 30，读出来的值取决于先执行哪一处，
 * 这种 bug 极难定位。把默认值和类型钉在<b>注册处</b>，全工程就只有一份定义。
 *
 * <p>声明方式（内容侧写在 {@code content.init.init()} 里，框架侧见
 * {@link FrameworkDataKeys}）：
 * <pre>
 *   // 带默认值的字符串键：写读都用 "mymod:hero_name"，默认值 "无名"
 *   public static final DataKey&lt;String&gt; HERO =
 *           DataManager.defineStr("mymod", "hero_name", "无名");
 *
 *   // 整数键 + 只读：锁定在默认值，写入会被拒绝并告警
 *   public static final DataKey&lt;Integer&gt; CAP =
 *           DataManager.defineInt("mymod", "level_cap", 99, DataKeyFlag.READ_ONLY);
 *
 *   // 临时键：不落盘，只活到进程结束
 *   public static final DataKey&lt;Boolean&gt; PAUSED =
 *           DataManager.defineBool("mymod", "paused", false, DataKeyFlag.TEMPORARY);
 *
 *   // 存档作用域的键：允许当前存档覆盖全局默认值
 *   public static final DataKey&lt;Boolean&gt; CANT_EXIT =
 *           DataManager.defineBool("mymod", "cant_exit", false, DataKeyFlag.PER_SAVE);
 *
 *   // 使用
 *   HERO.set("霁雾");
 *   String name = HERO.get();
 *   CAP.set(120);            // → WARNING，值不生效
 *   CAP.getInt();            // → 99
 * </pre>
 *
 * <p>句柄是<b>不可变</b>的：即使后来有人用同一个键名重新注册（内容重定义框架内置键
 * 的默认值就是这种情况），已发出的句柄仍持有当初的定义。这样就不会出现
 * 「同一个键在两次读取之间换了默认值」这种半边生效的状态。
 *
 * @param namespace    命名空间（{@code starveil} 为引擎保留）
 * @param name         键名（不含命名空间）
 * @param qualified    实际读写用的完整键名，形如 {@code namespace:name}
 * @param type         值类型
 * @param defaultValue 默认值（类型与 {@code type} 一致的包装类型）
 * @param readOnly     只读：锁定在默认值，写入会被拒绝
 * @param temporary    临时：不落盘，只在当前进程内有效
 * @param scope        作用域
 */
public record DataKey<T>(String namespace,
                         String name,
                         String qualified,
                         DataType type,
                         Object defaultValue,
                         boolean readOnly,
                         boolean temporary,
                         DataKeyScope scope) {

    /**
     * 创建一个键并<b>立即注册</b>。
     *
     * <p>重复注册会替换注册表里的定义（内容可以重定义框架内置键的默认值），
     * 但<b>不改变已发出句柄</b>。同名键不允许换类型：那几乎一定是
     * 「两处各自声明了同名键」，必须报错而不是让后者静默覆盖。
     *
     * @param defaultValue 默认值；{@code null} 表示「没有默认值」，读取时按类型取零值。
     *                     非 null 时类型必须与 {@code valueType} 一致
     * @param valueType    值类型，{@code int.class} / {@code Integer.class} 都可以
     */
    public static <T> DataKey<T> of(String namespace,
                                    String name,
                                    T defaultValue,
                                    Class<?> valueType,
                                    DataKeyFlag... flags) {
        return DataKeyRegistry.register(namespace, name, defaultValue, valueType, flags);
    }

    // ==================== 读取 ====================

    /**
     * 读取当前值。未设置时返回默认值（注册时传了 {@code null} 就返回 {@code null}）。
     *
     * @throws ClassCastException 声明处的泛型写错时（例如把 int 键声明成
     *                            {@code DataKey<String>}）会在调用方爆掉，
     *                            这是想要的效果 —— 早点发现比默默拿到错值好
     */
    @SuppressWarnings("unchecked")
    public T get() {
        Object raw = DataManager.readRaw(this);
        return (T) (raw == null ? defaultValue : parse(raw));
    }

    /** 读取字符串值。类型不是 STRING 时告警并返回值的字符串形式。 */
    public String getString() {
        Object value = get();
        return value == null ? null : String.valueOf(value);
    }

    /** 读取整数值，等价于 {@code get()} + 拆箱（便于 {@code int n = KEY.getInt();}）。 */
    public int getInt() {
        Object value = get();
        if (value instanceof Number n) {
            return n.intValue();
        }
        warnType("getInt");
        return defaultValue instanceof Number n ? n.intValue() : 0;
    }

    public long getLong() {
        Object value = get();
        if (value instanceof Number n) {
            return n.longValue();
        }
        warnType("getLong");
        return defaultValue instanceof Number n ? n.longValue() : 0L;
    }

    public double getDouble() {
        Object value = get();
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        warnType("getDouble");
        return defaultValue instanceof Number n ? n.doubleValue() : 0.0;
    }

    public float getFloat() {
        Object value = get();
        if (value instanceof Number n) {
            return n.floatValue();
        }
        warnType("getFloat");
        return defaultValue instanceof Number n ? n.floatValue() : 0f;
    }

    /** 读取布尔值。声明类型不是 BOOLEAN 时告警并返回默认值。 */
    public boolean getBool() {
        Object value = get();
        if (value instanceof Boolean b) {
            return b;
        }
        warnType("getBool");
        return Boolean.TRUE.equals(defaultValue);
    }

    // ==================== 写入 ====================

    /**
     * 写入值。只读键会被拒绝（告警且不生效），类型不符同样被拒绝。
     *
     * <p>{@code set(7)} 这种「int 字面量写进 long/double 键」的用法会走
     * {@link Number} 重载 —— 数值拓宽是无损的，允许。
     */
    public void set(T value) {
        DataManager.writeRaw(this, value);
    }

    /**
     * 用数值写入。仅对数值类型的键有效，且只允许<b>无损拓宽</b>
     * （int → long/double，long → double 之外的降级会被告警拒绝）。
     */
    public void set(Number value) {
        if (value == null) {
            DataManager.writeRaw(this, null);
            return;
        }
        switch (type) {
            case INT -> DataManager.writeRaw(this, value.intValue());
            case LONG -> DataManager.writeRaw(this, value.longValue());
            case DOUBLE -> DataManager.writeRaw(this, value.doubleValue());
            case FLOAT -> DataManager.writeRaw(this, value.floatValue());
            default -> {
                warnType("set(Number)");
                DataManager.writeRaw(this, String.valueOf(value));
            }
        }
    }

    public void setInt(int value) {
        requireNumeric("setInt");
        DataManager.writeRaw(this, value);
    }

    public void setLong(long value) {
        requireNumeric("setLong");
        DataManager.writeRaw(this, value);
    }

    public void setDouble(double value) {
        requireNumeric("setDouble");
        DataManager.writeRaw(this, value);
    }

    public void setFloat(float value) {
        requireNumeric("setFloat");
        DataManager.writeRaw(this, value);
    }

    public void setBool(boolean value) {
        if (type != DataType.BOOLEAN) {
            warnType("setBool");
        }
        DataManager.writeRaw(this, value);
    }

    /** 删除此键在存储中的值（回到默认值）。只读键拒绝。 */
    public void remove() {
        DataManager.removeQualified(this);
    }

    /** 是否已显式设过值（存储 / 临时 / 内存覆盖任一有值即为真）。 */
    public boolean isSet() {
        return DataManager.isSet(this);
    }

    // ==================== 元信息 ====================

    /** 键是否属于某个作用域。 */
    public boolean is(DataKeyScope other) {
        return scope == other;
    }

    /** 类型判断的便捷写法。 */
    public boolean is(DataType other) {
        return type == other;
    }

    // ==================== 内部 ====================

    private void requireNumeric(String method) {
        if (!(type == DataType.INT || type == DataType.LONG
                || type == DataType.DOUBLE || type == DataType.FLOAT)) {
            warnType(method);
        }
    }

    private void warnType(String method) {
        Logger("WARNING", "键 '" + qualified + "' 注册类型为 " + type
                + "，但被当作 " + method + " 使用 —— 返回值不具意义，请检查调用处或注册类型");
    }

    /**
     * 判断一个字符串能否按注册类型解析。用于写入时校验。
     *
     * <p>{@link #parse} 在解析失败时会回退到默认值（读取路径要尽量给出可用的值），
     * 因此写入校验不能依赖它 —— 否则把 {@code "abc"} 写进 int 键会被判为
     * 「解析成功（回退成了默认值）」，脏数据就落进存档了。
     */
    boolean canParse(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        try {
            return switch (type) {
                case STRING -> true;
                case BOOLEAN -> "true".equalsIgnoreCase(t) || "false".equalsIgnoreCase(t);
                case INT -> {
                    Integer.parseInt(t);
                    yield true;
                }
                case LONG -> {
                    Long.parseLong(t);
                    yield true;
                }
                case DOUBLE -> {
                    Double.parseDouble(t);
                    yield true;
                }
                case FLOAT -> {
                    Float.parseFloat(t);
                    yield true;
                }
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 把存储层读出的字符串按注册类型解析。解析失败告警并返回默认值。 */
    Object parse(Object raw) {
        if (raw == null) {
            return defaultValue;
        }
        String text;
        if (raw instanceof String s) {
            text = s;
        } else {
            // 临时值 / 内存覆盖可能是原始类型
            if (type.matches(raw)) {
                return raw;
            }
            text = String.valueOf(raw);
        }
        try {
            return switch (type) {
                case STRING -> text;
                case BOOLEAN -> parseBoolean(text);
                case INT -> Integer.parseInt(text.trim());
                case LONG -> Long.parseLong(text.trim());
                case DOUBLE -> Double.parseDouble(text.trim());
                case FLOAT -> Float.parseFloat(text.trim());
            };
        } catch (NumberFormatException e) {
            Logger("WARNING", "键 '" + qualified + "' 的值无法解析为 " + type
                    + ": '" + text + "'，已回退到默认值 " + defaultValue);
            return defaultValue;
        }
    }

    private Boolean parseBoolean(String text) {
        String t = text.trim();
        if ("true".equalsIgnoreCase(t)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(t)) {
            return Boolean.FALSE;
        }
        Logger("WARNING", "键 '" + qualified + "' 的值不是合法布尔值: '" + text
                + "'（只接受 true/false），已回退到默认值 " + defaultValue);
        return defaultValue instanceof Boolean b ? b : Boolean.FALSE;
    }
}

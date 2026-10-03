package com.xiaowu.game.starveil.infrastructure.persistence;

/**
 * 数据键的值类型。
 *
 * <p>在注册时就必须写明，因此 {@link DataManager} 与 {@link SaveDataManager}
 * 不需要再为每种类型各准备一套 get/set —— 类型是键自身属性的一部分，
 * 不是调用点的选择。
 *
 * <p>类型写入的是「归一化后的 java 类型」，与存储层无关：所有值一律以
 * 字符串落盘，{@link #valueClass} 描述的是<b>读写时对外呈现的类型</b>。
 */
public enum DataType {

    STRING(String.class),
    BOOLEAN(Boolean.class),
    INT(Integer.class),
    LONG(Long.class),
    DOUBLE(Double.class),
    FLOAT(Float.class);

    private final Class<?> valueClass;

    DataType(Class<?> valueClass) {
        this.valueClass = valueClass;
    }

    /** 该类型对应的包装类型。 */
    public Class<?> valueClass() {
        return valueClass;
    }

    /**
     * 由 java 类型（基本类型或包装类型均可）取对应的值类型。
     *
     * <p>{@code int.class} 与 {@code Integer.class} 都映射到 {@link #INT} ——
     * 声明时写哪个是风格问题，不该让其中一个失败。
     *
     * @return 对应类型；不受支持时返回 {@code null}
     */
    public static DataType of(Class<?> javaType) {
        if (javaType == null) {
            return null;
        }
        if (javaType == String.class) return STRING;
        if (javaType == boolean.class || javaType == Boolean.class) return BOOLEAN;
        if (javaType == int.class || javaType == Integer.class) return INT;
        if (javaType == long.class || javaType == Long.class) return LONG;
        if (javaType == double.class || javaType == Double.class) return DOUBLE;
        if (javaType == float.class || javaType == Float.class) return FLOAT;
        return null;
    }

    /** 由默认值的 java 类型推断值类型；{@code null} 无法推断。 */
    public static DataType of(Object defaultValue) {
        return defaultValue == null ? null : of(defaultValue.getClass());
    }

    /** 该类型的「没有默认值」表示：数值 0 / false / 空串 / null。 */
    public Object zeroValue() {
        return switch (this) {
            case STRING -> "";
            case BOOLEAN -> Boolean.FALSE;
            case INT -> 0;
            case LONG -> 0L;
            case DOUBLE -> 0.0;
            case FLOAT -> 0f;
        };
    }

    /** 判断一个 java 值是否属于此类型（自动装箱）。 */
    public boolean matches(Object value) {
        return value != null && valueClass.isInstance(value);
    }

    /** 判断一个已声明的类型是否就是此类型。 */
    public boolean matches(DataType other) {
        return this == other;
    }
}

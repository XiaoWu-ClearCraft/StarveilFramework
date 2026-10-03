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

    /** 判断一个 java 值是否属于此类型（自动装箱）。 */
    public boolean matches(Object value) {
        return value != null && valueClass.isInstance(value);
    }

    /** 判断一个已声明的类型是否就是此类型。 */
    public boolean matches(DataType other) {
        return this == other;
    }
}

package com.xiaowu.game.starveil.infrastructure.persistence;

/**
 * 数据键的作用域。
 *
 * <p>这是「特殊键」这个概念在注册制下的正式表达。以前「特殊」是一个集合成员关系
 * （{@code SpecialKeys.isSpecial(key)}），现在它是键在注册时就声明好的属性 ——
 * 读代码时不必跳去另一个文件查这个键算不算特殊。
 */
public enum DataKeyScope {

    /**
     * 全局键：值存在 {@code data.dat}，跨存档共享。
     *
     * <p>适合画面设置、音量、按键绑定这类「属于玩家而不是属于某个存档」的配置。
     */
    GLOBAL,

    /**
     * 存档键：允许被当前存档覆盖 —— 存档里有值时优先用存档的，
     * 存档里没有时才回落到 {@code data.dat} 里的全局默认值。
     *
     * <p>适合「全局有个基准，但剧情可以临时改写」的开关，
     * 例如 {@code starveil:cant_exit}（默认不禁，某段剧情里禁）。
     */
    PER_SAVE
}

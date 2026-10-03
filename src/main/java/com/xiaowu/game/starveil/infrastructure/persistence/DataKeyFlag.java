package com.xiaowu.game.starveil.infrastructure.persistence;

/**
 * 注册数据键时的附加标记。不传即普通键（全局作用域、可写、落盘）。
 */
public enum DataKeyFlag {

    /**
     * 临时键：<b>不落盘</b>，只在当前进程内有效，进程结束即消失。
     *
     * <p>替掉「写进配置、用完再主动清理」那套做法。后者有两个毛病：
     * 崩溃或被强杀时清理不掉，标记就永久残留在配置里（例如「异常关闭」标记）；
     * 而且清理逻辑散落在启动流程各处，容易漏。
     */
    TEMPORARY,

    /**
     * 只读键：锁定在默认值。写入会被拒绝并记录 WARNING。
     *
     * <p>适合「框架告知内容、但内容不该改」的信息，例如框架版本号。
     */
    READ_ONLY,

    /**
     * 存档作用域（等价于 {@link DataKeyScope#PER_SAVE}）。
     *
     * <p>单独给一个标记是因为 {@code DataManager.define*} 的调用点写
     * {@code Flag.PER_SAVE} 比再开一个参数位可读。
     */
    PER_SAVE
}

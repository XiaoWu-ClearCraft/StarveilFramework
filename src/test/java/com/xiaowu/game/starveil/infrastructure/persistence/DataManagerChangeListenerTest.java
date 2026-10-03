package com.xiaowu.game.starveil.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 配置变更通知测试。
 *
 * <p>通知存在的意义：调试窗口据此<b>按需</b>重读解密文件，
 * 而不是每 500ms 无脑读一遍磁盘。
 *
 * <p>这里用 {@code reload()} 触发通知：它在测试环境（未 initialize）下不会写盘 ——
 * {@code dataFile} 为 null 会走异常分支并重建空表，随后照常发通知。
 * 刻意不调用 {@code set*}，那会真的改写配置文件。
 */
class DataManagerChangeListenerTest {

    @AfterEach
    void clearOverrides() {
        DataManager.clearAllMemoryOverrides();
    }

    @Test
    void listenerFiresOnReload() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;
        DataManager.addChangeListener(listener);
        try {
            DataManager.reload();
            assertEquals(1, calls.get(), "重载应当通知监听者，否则界面会一直显示旧内容");

            DataManager.reload();
            assertEquals(2, calls.get());
        } finally {
            DataManager.removeChangeListener(listener);
        }
    }

    @Test
    void removedListenerStopsReceiving() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;
        DataManager.addChangeListener(listener);
        DataManager.reload();
        assertEquals(1, calls.get());

        DataManager.removeChangeListener(listener);
        DataManager.reload();
        assertEquals(1, calls.get(), "移除后不应再收到通知（插件卸载 / 窗口关闭场景）");
    }

    @Test
    void oneBadListenerDoesNotBlockOthers() {
        AtomicInteger calls = new AtomicInteger();
        Runnable bad = () -> {
            throw new IllegalStateException("boom");
        };
        Runnable good = calls::incrementAndGet;

        DataManager.addChangeListener(bad);
        DataManager.addChangeListener(good);
        try {
            DataManager.reload();
            assertEquals(1, calls.get(),
                    "一个监听者抛异常不应影响其它监听者，否则界面会漏刷新");
        } finally {
            DataManager.removeChangeListener(bad);
            DataManager.removeChangeListener(good);
        }
    }

    @Test
    void nullListenerIsIgnored() {
        DataManager.addChangeListener(null);
        DataManager.removeChangeListener(null);
        DataManager.reload();
    }
}

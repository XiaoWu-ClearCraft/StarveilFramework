package com.xiaowu.game.starveil.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 框架版本兼容判定测试。 */
class PluginCompatibilityTest {

    private static final String CURRENT = "1.0.0";

    @Test
    void unspecifiedVersionIsAlwaysCompatible() {
        assertTrue(PluginCompatibility.isCompatible(null, CURRENT), "没声明就是不挑版本");
        assertTrue(PluginCompatibility.isCompatible("", CURRENT));
        assertTrue(PluginCompatibility.isCompatible("   ", CURRENT));
        assertTrue(PluginCompatibility.isCompatible("*", CURRENT));
    }

    @Test
    void exactMatchIsCompatible() {
        assertTrue(PluginCompatibility.isCompatible("1.0.0", "1.0.0"));
    }

    @Test
    void sameMajorIsCompatible() {
        assertTrue(PluginCompatibility.isCompatible("1.9.3", "1.0.0"),
                "同一主版本内应放行，否则每次发版都会作废所有插件");
        assertTrue(PluginCompatibility.isCompatible("1.0.0", "1.9.3"));
        assertTrue(PluginCompatibility.isCompatible("2.5", "2.0.1"));
    }

    @Test
    void differentMajorIsIncompatible() {
        assertFalse(PluginCompatibility.isCompatible("2.0.0", "1.0.0"),
                "跨大版本必须拦下：注入点语义可能已经变了");
        assertFalse(PluginCompatibility.isCompatible("1.0.0", "2.0.0"));
    }

    @Test
    void toleratesVPrefixAndWhitespace() {
        assertTrue(PluginCompatibility.isCompatible("  v1.0.0  ", CURRENT));
        assertTrue(PluginCompatibility.isCompatible("V1.2", "1.0.0"));
    }

    @Test
    void majorOfExtractsFirstSegment() {
        assertEquals("1", PluginCompatibility.majorOf("1.2.3"));
        assertEquals("2", PluginCompatibility.majorOf("v2.0"));
        assertEquals("7", PluginCompatibility.majorOf("7"));
        assertEquals("", PluginCompatibility.majorOf(null));
    }

    @Test
    void nonNumericVersionsOnlyMatchThemselves() {
        assertTrue(PluginCompatibility.isCompatible("beta", "beta"));
        assertFalse(PluginCompatibility.isCompatible("beta", "alpha"),
                "无法解析的版本号不应被意外放行");
    }

    @Test
    void missingCurrentVersionDoesNotBlockStartup() {
        assertTrue(PluginCompatibility.isCompatible("9.9.9", null),
                "框架版本取不到时不应当成不兼容而拦下插件");
    }

    @Test
    void mismatchDescriptionNamesThePlugin() {
        String msg = PluginCompatibility.describeMismatch("某插件", "2.0.0", "1.0.0");
        assertTrue(msg.contains("某插件"));
        assertTrue(msg.contains("2.0.0"));
        assertTrue(msg.contains("1.0.0"));
    }
}

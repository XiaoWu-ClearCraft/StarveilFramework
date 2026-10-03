package com.xiaowu.game.starveil.plugin;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件清单解析测试。
 *
 * <p>重点覆盖新增的两件事：加载时机与框架版本。
 * 时机解析错误会让插件在错误的阶段加载（注入静默失效），
 * 所以「无法识别就回退 LAUNCHER」这条必须钉住。
 */
class PluginManifestTest {

    private static PluginManifest parse(String yml) throws Exception {
        return PluginManifest.parse(new BufferedReader(new StringReader(yml)));
    }

    @Test
    void parsesBasicFields() throws Exception {
        PluginManifest m = parse("""
                # 注释
                id: my-plugin
                name: "我的插件"
                version: 1.2.3
                main: com.example.Main
                icon: icon.png
                """);
        assertEquals("my-plugin", m.id);
        assertEquals("我的插件", m.name, "值两端的引号应被去掉");
        assertEquals("1.2.3", m.version);
        assertEquals("com.example.Main", m.main);
        assertEquals("icon.png", m.icon);
        assertTrue(m.isValid());
    }

    @Test
    void defaultsToLauncherTiming() throws Exception {
        PluginManifest m = parse("id: a\nmain: x\n");
        assertEquals(PluginLoadTiming.LAUNCHER, m.loadTiming, "缺省时机必须是 LAUNCHER");
        assertNull(m.frameworkVersion, "没写框架版本就是 null（不挑版本）");
    }

    @Test
    void parsesInitTiming() throws Exception {
        assertEquals(PluginLoadTiming.INIT, parse("id: a\nmain: x\nload: INIT\n").loadTiming);
        assertEquals(PluginLoadTiming.INIT, parse("id: a\nmain: x\nload-at: init\n").loadTiming);
        assertEquals(PluginLoadTiming.INIT, parse("id: a\nmain: x\ntiming: INIT\n").loadTiming);
    }

    @Test
    void parsesExplicitLauncherTiming() throws Exception {
        assertEquals(PluginLoadTiming.LAUNCHER,
                parse("id: a\nmain: x\nload: LAUNCHER\n").loadTiming);
    }

    @Test
    void unknownTimingFallsBackToLauncher() throws Exception {
        assertEquals(PluginLoadTiming.LAUNCHER,
                parse("id: a\nmain: x\nload: 未来时机\n").loadTiming,
                "无法识别时必须回退 LAUNCHER：错当成 INIT 会去注入 init 并可能致命退出");
    }

    @Test
    void parsesFrameworkVersionAliases() throws Exception {
        assertEquals("1.0.0", parse("id: a\nmain: x\nframework-version: 1.0.0\n").frameworkVersion);
        assertEquals("1.0.0", parse("id: a\nmain: x\napi-version: 1.0.0\n").frameworkVersion);
        assertEquals("1.0.0", parse("id: a\nmain: x\nframework: 1.0.0\n").frameworkVersion);
    }

    @Test
    void pluginVersionAndFrameworkVersionAreIndependent() throws Exception {
        PluginManifest m = parse("""
                id: a
                version: 9.9.9
                framework-version: 1.0.0
                main: x
                """);
        assertEquals("9.9.9", m.version, "插件自身版本");
        assertEquals("1.0.0", m.frameworkVersion, "适配的框架版本，两者互不影响");
    }

    @Test
    void missingRequiredFieldsIsInvalid() throws Exception {
        assertFalse(parse("name: 没有 id\nmain: x\n").isValid());
        assertFalse(parse("id: a\n").isValid());
    }

    @Test
    void toleratesBlankLinesAndComments() throws Exception {
        PluginManifest m = parse("\n\n# c\nid: a\n\nmain: x\n\n");
        assertEquals("a", m.id);
        assertEquals("x", m.main);
    }
}

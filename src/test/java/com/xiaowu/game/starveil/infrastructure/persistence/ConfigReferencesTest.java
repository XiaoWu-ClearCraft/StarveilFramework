package com.xiaowu.game.starveil.infrastructure.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置同级 {@code @} 引用测试。
 *
 * <p>配置文件里最容易踩的两件事：链式引用要跟到底，循环引用不能把栈打爆。
 */
class ConfigReferencesTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    // ==================== 基本 ====================

    @Test
    void plainValueIsReturnedAsIs() {
        JsonObject o = json("{ \"LEFT\": \"a.png\" }");
        assertEquals("a.png", ConfigReferences.resolve(o, "LEFT"));
    }

    @Test
    void resolvesSingleReference() {
        JsonObject o = json("""
                {
                  "LEFT": "a.png",
                  "LEFT:LENGTHWAYS": "@LEFT"
                }
                """);
        assertEquals("a.png", ConfigReferences.resolve(o, "LEFT:LENGTHWAYS"));
        assertEquals("a.png", ConfigReferences.resolve(o, "LEFT"));
    }

    @Test
    void resolvesChainToTheEnd() {
        JsonObject o = json("""
                {
                  "A": "final.png",
                  "B": "@A",
                  "C": "@B",
                  "D": "@C"
                }
                """);
        assertEquals("final.png", ConfigReferences.resolve(o, "D"),
                "链式引用必须一路跟到底，而不是只跳一层");
        assertEquals("final.png", ConfigReferences.resolve(o, "B"));
    }

    @Test
    void referenceWithWhitespaceIsTolerated() {
        JsonObject o = json("""
                { "LEFT": "a.png", "X": "@  LEFT  " }
                """);
        assertEquals("a.png", ConfigReferences.resolve(o, "X"));
    }

    // ==================== 循环 ====================

    @Test
    void directSelfReferenceIsDetected() {
        JsonObject o = json("{ \"A\": \"@A\" }");
        assertNull(ConfigReferences.resolve(o, "A"), "自引用必须被检出，不能无限递归");
    }

    @Test
    void mutualCycleIsDetected() {
        JsonObject o = json("{ \"A\": \"@B\", \"B\": \"@A\" }");
        assertNull(ConfigReferences.resolve(o, "A"));
        assertNull(ConfigReferences.resolve(o, "B"));
    }

    @Test
    void longerCycleIsDetected() {
        JsonObject o = json("{ \"A\": \"@B\", \"B\": \"@C\", \"C\": \"@A\" }");
        assertNull(ConfigReferences.resolve(o, "A"));
    }

    // ==================== 缺失 / 非法 ====================

    @Test
    void missingTargetReturnsNull() {
        JsonObject o = json("{ \"A\": \"@NOPE\" }");
        assertNull(ConfigReferences.resolve(o, "A"));
    }

    @Test
    void missingKeyReturnsNull() {
        JsonObject o = json("{ \"A\": \"a.png\" }");
        assertNull(ConfigReferences.resolve(o, "NOPE"));
    }

    @Test
    void emptyReferenceTargetReturnsNull() {
        JsonObject o = json("{ \"A\": \"@\" }");
        assertNull(ConfigReferences.resolve(o, "A"));
        assertNull(ConfigReferences.resolve(o, "@   "));
    }

    @Test
    void nonPrimitiveValueReturnsNull() {
        JsonObject o = json("{ \"A\": {\"LEFT\": \"a.png\"} }");
        assertNull(ConfigReferences.resolve(o, "A"),
                "对象值不是引用目标，调用方应走对象分支");
    }

    @Test
    void nullArgumentsAreSafe() {
        JsonObject o = json("{ \"A\": \"a.png\" }");
        assertNull(ConfigReferences.resolve(null, "A"));
        assertNull(ConfigReferences.resolve(o, null));
    }

    // ==================== resolveValue ====================

    @Test
    void resolveValueHandlesDirectReference() {
        JsonObject o = json("{ \"LEFT\": \"a.png\", \"X\": \"@LEFT\" }");
        assertEquals("a.png", ConfigReferences.resolveValue(o, o.get("X")));
    }

    @Test
    void resolveValuePassesThroughPlainStrings() {
        JsonObject o = json("{ \"X\": \"a.png\" }");
        assertEquals("a.png", ConfigReferences.resolveValue(o, o.get("X")));
    }

    @Test
    void resolveValueHandlesNonStringAndNull() {
        JsonObject o = json("{ \"X\": 42 }");
        assertEquals("42", ConfigReferences.resolveValue(o, o.get("X")));
        assertNull(ConfigReferences.resolveValue(o, null));
    }

    @Test
    void isReferenceDistinguishesPrefixFromBareAt() {
        assertTrue(ConfigReferences.isReference("@LEFT"));
        assertFalse(ConfigReferences.isReference("@"), "空目标不算引用");
        assertFalse(ConfigReferences.isReference("LEFT"));
        assertFalse(ConfigReferences.isReference(null));
    }
}

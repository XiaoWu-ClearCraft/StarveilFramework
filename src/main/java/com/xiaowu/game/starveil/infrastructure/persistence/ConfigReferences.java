package com.xiaowu.game.starveil.infrastructure.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 配置文件的同级引用。
 *
 * <p>同一个 JSON 对象里，值可以写成 {@code "@某个同级键"} 来复用另一个键的值：
 * <pre>
 * {
 *   "LEFT": "starveil:textures/p.png",
 *   "LEFT:LENGTHWAYS": "@LEFT",           // 与 LEFT 完全相同
 *   "LEFT:LENGTHWAYS:WALK": "@LEFT:WALK"  // 复用行走贴图
 * }
 * </pre>
 *
 * <p>规则：
 * <ul>
 *   <li>只在<b>同级</b>（同一个 JSON 对象）里查找，不跨层；</li>
 *   <li>支持链式引用 {@code A→B→C}，一路跟到底；</li>
 *   <li>循环引用会被检测出来并报错返回 null，而不是无限递归把栈打爆；</li>
 *   <li>引用目标不存在时返回 null（调用方按「没有配置」处理）。</li>
 * </ul>
 */
public final class ConfigReferences {

    /** 引用前缀。 */
    public static final String PREFIX = "@";

    private ConfigReferences() {
    }

    /**
     * 按 key 取值，并跟随 {@code @} 引用。
     *
     * @return 最终值；不是引用则原样返回；引用缺失或成环返回 null
     */
    public static String resolve(JsonObject scope, String key) {
        if (scope == null || key == null) {
            return null;
        }
        Set<String> visited = new LinkedHashSet<>();
        String current = key.trim();

        while (true) {
            if (!visited.add(current)) {
                Logger("ERROR", "@引用成环: " + String.join(" → ", visited)
                        + " → " + current);
                return null;
            }
            JsonElement el = scope.get(current);
            if (el == null || !el.isJsonPrimitive()) {
                return null;
            }
            String raw = el.getAsString();
            if (raw == null) {
                return null;
            }
            if (!raw.startsWith(PREFIX)) {
                return raw;
            }
            String target = raw.substring(PREFIX.length()).trim();
            if (target.isEmpty()) {
                Logger("WARNING", "@引用没有目标: " + current);
                return null;
            }
            current = target;
        }
    }

    /**
     * 直接给一个值元素，若它是 {@code "@X"} 则跟随引用。
     *
     * <p>用于「值本身」就是引用的场景（而不是通过 key 取值）。
     */
    public static String resolveValue(JsonObject scope, JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String raw = element.getAsString();
        if (raw == null) {
            return null;
        }
        if (!raw.startsWith(PREFIX)) {
            return raw;
        }
        String target = raw.substring(PREFIX.length()).trim();
        if (target.isEmpty()) {
            Logger("WARNING", "@引用没有目标");
            return null;
        }
        return resolve(scope, target);
    }

    /** 值是否是引用写法。 */
    public static boolean isReference(String raw) {
        return raw != null && raw.startsWith(PREFIX) && raw.length() > PREFIX.length();
    }
}

package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 命名空间属性组件 —— 类似 Minecraft 实体上的数据标签（NBT/Data Components）。
 *
 * <p>以形如 {@code "starveil:entity_type"} 的带命名空间字符串作为键，值为任意简单对象
 * （String / Number / Boolean 等）。调试（F3）模式下渲染在实体头顶。
 */
public final class Attributes implements Component {

    // ==================== 常用命名空间键 ====================

    /** 实体类型，如 starveil:player / starveil:npc。 */
    public static final String ENTITY_TYPE = "starveil:entity_type";
    public static final String ENTITY_TYPE_PLAYER = "starveil:player";
    public static final String ENTITY_TYPE_NPC = "starveil:npc";

    /** 实体唯一 id（与存档 NPC id 一致）。 */
    public static final String ENTITY_ID = "starveil:entity_id";

    /** 显示名。 */
    public static final String DISPLAY_NAME = "starveil:display_name";

    /** NPC 行为算法。 */
    public static final String NPC_ALGORITHM = "starveil:algorithm";

    /** 玩家选中的角色皮肤 id。 */
    public static final String PLAYER_CHARACTER = "starveil:selected_character";

    /** 物品/物品名（当实体持有物品语义时使用，例如背包/掉落物）。 */
    public static final String ITEM_NAME = "starveil:item_name";

    private final TreeMap<String, Object> tags = new TreeMap<>();

    public boolean has(String key) {
        return tags.containsKey(key);
    }

    public Attributes set(String key, Object value) {
        if (value == null) {
            tags.remove(key);
        } else {
            tags.put(key, value);
        }
        return this;
    }

    public Attributes remove(String key) {
        tags.remove(key);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) tags.get(key);
    }

    public String getString(String key, String def) {
        Object v = tags.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    public int getInt(String key, int def) {
        Object v = tags.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public double getDouble(String key, double def) {
        Object v = tags.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public boolean getBoolean(String key, boolean def) {
        Object v = tags.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return Boolean.parseBoolean(String.valueOf(v).trim());
        }
        return def;
    }

    /** 所有键（按字典序）。 */
    public Set<String> keys() {
        return tags.keySet();
    }

    public Map<String, Object> asMap() {
        return new TreeMap<>(tags);
    }
}

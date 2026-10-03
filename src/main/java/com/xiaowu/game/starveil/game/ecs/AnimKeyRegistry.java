package com.xiaowu.game.starveil.game.ecs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 动画键注册表 —— 变体名登记 + 键解析回退。
 *
 * <p><b>注册规则</b>：变体名不得使用内置保留名（{@link AnimState} 的名字）。
 * 这不是洁癖 —— {@code LEFT:WALK} 是二段键，若允许一个叫 WALK 的变体存在，
 * 这个键就同时是「LEFT 的行走」和「WALK 变体的 LEFT 基础姿态」，语法立刻失去
 * 无歧义性。因此内置名由引擎独占，第三方必须另起名字（如 {@code LENGTHWAYS}）。
 *
 * <p><b>解析回退</b>：见 {@link #resolve}。核心思想是「变体比效果更具体」——
 * 若某个变体只定义了基础姿态 {@code LEFT:LENGTHWAYS}，那么
 * {@code LEFT:LENGTHWAYS:WALK} 应当落到它，而不是跳到默认模型集的
 * {@code LEFT:WALK}。
 */
public final class AnimKeyRegistry {

    /**
     * 引擎自带的变体名 —— 由玩法模式声明（如 {@code GRAVITY → LENGTHWAYS}）。
     *
     * <p>第三方不得占用：这些名字的语义由引擎模式定义，
     * 插件抢注会让「同一个键在装/不装插件时指向不同贴图」。
     *
     * <p>与 {@code GameplayMode} 的同步由 {@code GameplayModeTest} 守卫，
     * 新增模式却忘了登记会直接测挂。
     */
    private static final Set<String> BUILT_IN_VARIANTS =
            new LinkedHashSet<>(List.of("LENGTHWAYS"));

    /** 变体名 → 注册来源（便于报错时说明是谁注册的）。 */
    private static final Set<String> REGISTERED_VARIANTS =
            new LinkedHashSet<>(BUILT_IN_VARIANTS);

    private AnimKeyRegistry() {
    }

    // ==================== 变体注册 ====================

    /**
     * 注册一个变体名。
     *
     * @throws IllegalArgumentException 名字为空、是内置保留名、或已被注册
     */
    public static synchronized void registerVariant(String name) {
        String v = AnimKey.canonicalVariant(name);
        if (v == null) {
            throw new IllegalArgumentException("变体名不能为空");
        }
        if (AnimState.isBuiltIn(v)) {
            throw new IllegalArgumentException(
                    "不允许注册内置保留名 '" + v + "'（内置效果名: "
                            + String.join("/", AnimState.builtInNames())
                            + "）。变体请另起名字，例如 LENGTHWAYS。");
        }
        if (v.indexOf(':') >= 0) {
            throw new IllegalArgumentException("变体名不能包含 ':' —— " + v);
        }
        if (AnimKey.isAngleSegment(v)) {
            throw new IllegalArgumentException(
                    "变体名不能是纯数字 '" + v + "'（纯数字段会被解析成角度）");
        }
        if (BUILT_IN_VARIANTS.contains(v)) {
            throw new IllegalArgumentException(
                    "变体名 '" + v + "' 是引擎自带变体（由玩法模式声明），第三方不得占用");
        }
        if (!REGISTERED_VARIANTS.add(v)) {
            throw new IllegalArgumentException("变体名已被注册: " + v);
        }
    }

    /** 尝试注册，失败只返回 false（给「可选注册」场景用）。 */
    public static synchronized boolean tryRegisterVariant(String name) {
        try {
            registerVariant(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static synchronized boolean isVariantRegistered(String name) {
        String v = AnimKey.canonicalVariant(name);
        return v != null && REGISTERED_VARIANTS.contains(v);
    }

    public static synchronized Set<String> registeredVariants() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(REGISTERED_VARIANTS));
    }

    /** 是否是引擎自带变体（第三方不得占用）。 */
    public static synchronized boolean isBuiltInVariant(String name) {
        String v = AnimKey.canonicalVariant(name);
        return v != null && BUILT_IN_VARIANTS.contains(v);
    }

    /** 引擎自带的变体名。 */
    public static synchronized Set<String> builtInVariants() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(BUILT_IN_VARIANTS));
    }

    /** 清空第三方注册，保留引擎自带变体（仅测试与存档重载场景使用）。 */
    public static synchronized void clearRegistry() {
        REGISTERED_VARIANTS.clear();
        REGISTERED_VARIANTS.addAll(BUILT_IN_VARIANTS);
    }

    // ==================== 解析回退 ====================

    /**
     * 按优先级从「已定义的键」中挑出最合适的一个。
     *
     * <p>候选顺序 = 变体（具体 → 普通） × 效果（具体 → 基础姿态）。
     * 变体整体优先于效果回退，保证某个变体只定义基础姿态时不会被默认模型集抢走。
     *
     * <p>注意 SKID <b>不会</b>回退到 WALK：急停时用行走贴图是错的，
     * 没定义就退回基础姿态。
     *
     * @param defined 判断某个键是否已有贴图
     * @return 命中的键；都没有则 null
     */
    public static AnimKey resolve(Predicate<AnimKey> defined, Facing facing,
                                  String variant, AnimState effect) {
        return resolve(defined, facing, variant, effect, null);
    }

    /**
     * 带角度维度的解析。
     *
     * <p>候选顺序 = 变体 × 效果 × 角度。角度是<b>最内层</b>偏好：
     * 同一个变体同一效果下，先找角度专用贴图，找不到再用无角度贴图
     * （由调用方按重力角度旋转）。变体仍然整体优先于角度 ——
     * 某个变体只定义了基础姿态时，绝不因为「另一个模型集有 90° 专用图」而被抢走。
     *
     * @param angle 重力方向角度；null 或 0 表示不做角度专用查找
     */
    public static AnimKey resolve(Predicate<AnimKey> defined, Facing facing,
                                  String variant, AnimState effect, Integer angle) {
        if (defined == null || facing == null) {
            return null;
        }
        for (String v : variantChain(variant)) {
            for (AnimState e : effectChain(effect)) {
                for (Integer a : angleChain(angle)) {
                    AnimKey candidate = AnimKey.of(facing, v, e, a);
                    if (defined.test(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** 角度回退顺序：角度专用 → 无角度。 */
    public static List<Integer> angleChain(Integer angle) {
        List<Integer> out = new ArrayList<>(2);
        Integer a = AnimKey.normalizeAngle(angle);
        if (a != null) {
            out.add(a);
        }
        out.add(null);
        return out;
    }

    /** 变体回退顺序：具体变体 → 普通模型集。名称统一取规范形式（大写）。 */
    public static List<String> variantChain(String variant) {
        List<String> out = new ArrayList<>(2);
        String v = AnimKey.canonicalVariant(variant);
        if (v != null) {
            out.add(v);
        }
        out.add(null);
        return out;
    }

    /** 效果回退顺序。 */
    public static List<AnimState> effectChain(AnimState effect) {
        List<AnimState> out = new ArrayList<>(3);
        if (effect == null) {
            out.add(null);
            return out;
        }
        out.add(effect);
        switch (effect) {
            case WALK -> {
                out.add(AnimState.IDLE);
                out.add(null);
            }
            case SKID -> {
                // 急停不复用行走贴图，没定义就退回基础姿态
                out.add(null);
            }
            case IDLE -> out.add(null);
        }
        return out;
    }
}

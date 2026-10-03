package com.xiaowu.game.starveil.game.ecs;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 动画键 —— 形如 {@code <方向>[:<变体>][:<效果>][:<角度>]}。
 *
 * <p>各段含义：
 * <ul>
 *   <li><b>方向</b>（必填）—— {@link Facing} 之一。</li>
 *   <li><b>变体</b>（可选）—— 任意字符串，例如 {@code LENGTHWAYS}。省略表示
 *       「普通（NORMAL）模型集」。由玩法模式统一决定。</li>
 *   <li><b>效果</b>（可选）—— {@link AnimState} 之一（IDLE / WALK / SKID）。
 *       省略表示该朝向的<b>基础姿态</b>。</li>
 *   <li><b>角度</b>（可选）—— 整数度数，用于重力方向倾斜时的专用贴图。
 *       省略（或为 0）表示「没有专用贴图，按重力角度自动旋转基础贴图」。</li>
 * </ul>
 *
 * <p>实例：
 * <pre>
 *   LEFT                        普通模式的 LEFT 基础姿态
 *   LEFT:WALK                   普通模式的 LEFT 行走
 *   LEFT:LENGTHWAYS             重力模式的 LEFT 基础姿态
 *   LEFT:LENGTHWAYS:90          重力方向 90° 时的 LEFT 专用贴图
 *   LEFT:LENGTHWAYS:WALK        重力模式的 LEFT 行走
 *   LEFT:LENGTHWAYS:WALK:90     重力方向 90° 时的 LEFT 行走专用贴图
 * </pre>
 *
 * <p><b>为什么纯数字段一定是角度</b>：{@code LEFT:90} 里 90 既可能是变体也可能是角度，
 * 必须靠一条硬规则消歧。取「纯数字 = 角度」后，
 * {@code LEFT:LENGTHWAYS:90}（变体 + 角度）与 {@code LEFT:LENGTHWAYS:WALK}
 * （变体 + 效果）就能各归其位。代价是变体名不能是纯数字 ——
 * 这与「变体名不能占用内置效果名」是同一类约束，注册时会一并拒绝。
 */
public final class AnimKey {

    private static final char SEP = ':';
    /** 方向 + 变体 + 效果 + 角度。 */
    private static final int MAX_PARTS = 4;

    /** 纯数字段（允许负号，便于从尾部剥离角度）。 */
    private static final Pattern ANGLE_PATTERN = Pattern.compile("-?\\d+");

    private final Facing facing;
    /** null = 普通（NORMAL）模型集。 */
    private final String variant;
    /** null = 该朝向的基础姿态。 */
    private final AnimState effect;
    /** 归一化到 [0,360) 的角度；null = 无角度专用贴图（按需自动旋转）。 */
    private final Integer angle;

    private AnimKey(Facing facing, String variant, AnimState effect, Integer angle) {
        this.facing = facing;
        this.variant = variant;
        this.effect = effect;
        this.angle = angle;
    }

    public static AnimKey of(Facing facing, String variant, AnimState effect) {
        return of(facing, variant, effect, null);
    }

    public static AnimKey of(Facing facing, String variant, AnimState effect, Integer angle) {
        if (facing == null) {
            throw new IllegalArgumentException("动画键必须有方向");
        }
        return new AnimKey(facing, canonicalVariant(variant), effect, normalizeAngle(angle));
    }

    // ==================== 解析 / 格式化 ====================

    /**
     * 解析动画键文本。
     *
     * @throws IllegalArgumentException 段数超限、方向不合法、变体用了内置保留名、
     *                                  变体是纯数字、效果段不是内置效果名、或角度越界
     */
    public static AnimKey parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("动画键不能为 null");
        }
        String raw = text.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("动画键不能为空");
        }

        String[] parts = raw.split(String.valueOf(SEP), -1);
        if (parts.length > MAX_PARTS) {
            throw new IllegalArgumentException(
                    "动画键最多四段 <方向>[:<变体>][:<效果>][:<角度>]，收到: " + text);
        }

        Facing facing = parseFacing(parts[0]);
        if (facing == null) {
            throw new IllegalArgumentException(
                    "动画键必须以方向开头（LEFT/RIGHT/UP/DOWN），收到: " + text);
        }

        // 从尾部剥离角度段：纯数字即为角度
        int end = parts.length;
        Integer angle = null;
        if (end > 1 && isAngleSegment(parts[end - 1])) {
            angle = parseAngle(parts[end - 1], text);
            end--;
        }

        String variant = null;
        AnimState effect = null;
        int remaining = end - 1;  // 方向之后还剩几段

        if (remaining == 1) {
            variant = requireNonEmpty(parts[1], "第二段", text);
            AnimState asEffect = AnimState.fromName(variant);
            if (asEffect != null) {
                // LEFT:WALK —— 是内置效果名，按「效果」解释
                effect = asEffect;
                variant = null;
            }
        } else if (remaining == 2) {
            String second = requireNonEmpty(parts[1], "变体段", text);
            String third = requireNonEmpty(parts[2], "第三段", text);
            if (AnimState.isBuiltIn(second)) {
                throw new IllegalArgumentException(
                        "变体名不能使用内置保留名 " + second + "（会导致键语法歧义）: " + text);
            }
            AnimState asEffect = AnimState.fromName(third);
            if (asEffect == null) {
                throw new IllegalArgumentException(
                        "动画键第三段必须是内置效果名（IDLE/WALK/SKID），收到: " + text);
            }
            variant = second;
            effect = asEffect;
        } else if (remaining > 2) {
            // 4 段但末段不是纯数字：无法解释成「变体 + 效果 + 角度」，
            // 早期版本会静默丢掉多余段，等于悄悄忽略配置里的错字。
            throw new IllegalArgumentException(
                    "动画键第四段必须是角度（纯数字），或去掉多余段；收到: " + text);
        }

        return new AnimKey(facing, canonicalVariant(variant), effect, normalizeAngle(angle));
    }

    /** 是否是一个合法形态的动画键文本（不抛异常的版本）。 */
    public static boolean isValid(String text) {
        try {
            parse(text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 规范化文本：方向、变体、效果转大写；角度按归一化值输出。 */
    public String format() {
        StringBuilder sb = new StringBuilder(facing.name());
        if (variant != null) {
            sb.append(SEP).append(variant);
        }
        if (effect != null) {
            sb.append(SEP).append(effect.name());
        }
        if (angle != null) {
            sb.append(SEP).append(angle);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    // ==================== 访问器 ====================

    public Facing facing() {
        return facing;
    }

    /** 变体名；null 表示普通（NORMAL）模型集。 */
    public String variant() {
        return variant;
    }

    /** 效果；null 表示该朝向的基础姿态。 */
    public AnimState effect() {
        return effect;
    }

    /** 角度（[0,360)）；null 表示没有角度专用贴图。 */
    public Integer angle() {
        return angle;
    }

    public boolean hasAngle() {
        return angle != null;
    }

    public boolean isBasePose() {
        return effect == null;
    }

    // ==================== 工具 ====================

    /**
     * 是否是「角度段」—— 纯数字。
     *
     * <p>这条规则是键语法消歧的基石，因此公开出来供注册校验复用。
     */
    public static boolean isAngleSegment(String segment) {
        return segment != null && ANGLE_PATTERN.matcher(segment.trim()).matches();
    }

    /**
     * 变体名的规范形式：去空白 + 转大写。
     *
     * <p>大小写不敏感，否则配置里写 {@code left:lengthways:walk}
     * 就无法命中按 {@code LENGTHWAYS} 发起的查找。
     *
     * @return 规范名；传入 null/空白返回 null（表示普通模型集）
     */
    public static String canonicalVariant(String variant) {
        if (variant == null) return null;
        String v = variant.trim();
        return v.isEmpty() ? null : v.toUpperCase(java.util.Locale.ROOT);
    }

    /** 归一化角度到 [0,360)；0 与 null 等价（都表示「不做角度专用查找」）。 */
    public static Integer normalizeAngle(Integer angle) {
        if (angle == null) {
            return null;
        }
        int a = ((angle % 360) + 360) % 360;
        return a == 0 ? null : a;
    }

    private static Integer parseAngle(String segment, String text) {
        try {
            long value = Long.parseLong(segment.trim());
            if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("动画键角度超出范围: " + text);
            }
            return (int) value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("动画键角度无法解析: " + text);
        }
    }

    private static String requireNonEmpty(String segment, String what, String text) {
        String s = segment == null ? "" : segment.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("动画键的" + what + "为空: " + text);
        }
        return s;
    }

    private static Facing parseFacing(String text) {
        if (text == null) return null;
        String t = text.trim();
        for (Facing f : Facing.values()) {
            if (f.name().equalsIgnoreCase(t)) {
                return f;
            }
        }
        return null;
    }

    // ==================== 相等性 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnimKey other)) return false;
        return facing == other.facing
                && Objects.equals(variant, other.variant)
                && effect == other.effect
                && Objects.equals(angle, other.angle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(facing, variant, effect, angle);
    }
}

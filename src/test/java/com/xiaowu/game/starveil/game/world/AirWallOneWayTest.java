package com.xiaowu.game.starveil.game.world;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单向平台测试。
 *
 * <p>核心是「方向」这条判据：同一块平台，从上面落下来要接住、从下面跳上去要放过。
 * 光看「盒子与形状是否相交」永远区分不出这两种情形 —— 相交在两种情况下都成立。
 *
 * <p>另一个容易写错的地方是<b>快速下落</b>：一帧走十几个像素时，
 * 候选位置早就陷在平台内部了，判据必须带上「这一步是从哪开始的」。
 */
class AirWallOneWayTest {

    /** 一块 200×20 的单向平台，台面在 y=100。 */
    private static AirWall platform() {
        return AirWall.fromJson(JsonParser.parseString(
                "{ \"x\": 0, \"y\": 100, \"x_to\": 200, \"y_to\": 120, \"oneWay\": true }")
                .getAsJsonObject());
    }

    /** 同样几何、但没标 oneWay 的实心墙。 */
    private static AirWall solid() {
        return AirWall.fromJson(JsonParser.parseString(
                "{ \"x\": 0, \"y\": 100, \"x_to\": 200, \"y_to\": 120 }")
                .getAsJsonObject());
    }

    /** 玩家尺寸：20×20。 */
    private static final double W = 20;
    private static final double H = 20;

    // ==================== 解析 ====================

    @Test
    void oneWayBecomesADecorator() {
        AirWall wall = platform();
        assertNotNull(wall);
        assertTrue(wall instanceof AirWall.OneWay, "带 oneWay 的形状应当被包一层装饰器");
        assertTrue(wall.oneWay());
        // 形状本身的信息不能被装饰器弄丢
        assertEquals(4, wall.pointCount());
        assertEquals(100.0, wall.surfaceTop(), 1e-9);
    }

    @Test
    void solidWallIsNotOneWay() {
        AirWall wall = solid();
        assertNotNull(wall);
        assertFalse(wall.oneWay());
        assertFalse(wall instanceof AirWall.OneWay);
    }

    @Test
    void oneWayFalseIsJustASolidWall() {
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"x\": 0, \"y\": 100, \"x_to\": 200, \"y_to\": 120, \"oneWay\": false }")
                .getAsJsonObject());
        assertNotNull(wall);
        assertFalse(wall.oneWay());
    }

    /**
     * 单向语意只对<b>水平台面</b>成立，而台面高度取「最高顶点」。
     * 斜着标 oneWay 会得到一条悬空的判定线 —— 这是文档里明确不支持的写法，
     * 这里把它的实际行为钉住（不是崩溃，只是接不住人），免得以后误以为能用。
     */
    @Test
    void oneWayOnASlopeIsNotSupportedButDoesNotCrash() {
        // 从 (0,700) 斜到 (200,600) 的一条斜面
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"polygon\": [[0,700],[200,600],[200,700]], \"oneWay\": true }")
                .getAsJsonObject());
        assertNotNull(wall);
        assertTrue(wall.oneWay());
        // 台面被当成最高顶点 y=600，所以只有脚底在 600 以上才会被接住
        assertEquals(600.0, wall.surfaceTop(), 1e-9);
        // 脚底在最高顶点之上：仍然会挡（这个盒子确实压在斜面右上角上）
        assertTrue(wall.blocksFall(600, 180, 620, W, H),
                "脚底在最高顶点之上时仍然会挡");
        // 脚底已经低于最高顶点（例如走在斜面中段）：放行 —— 于是直接穿过斜面掉下去。
        // 这就是「斜面不要标 oneWay」的原因，这里把行为钉住而不是假装支持。
        assertFalse(wall.blocksFall(700, 180, 620, W, H),
                "脚底低于最高顶点就放行：斜面上标 oneWay 会直接穿下去");
    }

    @Test
    void nonBooleanOneWayIsIgnored() {
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"x\": 0, \"y\": 100, \"x_to\": 200, \"y_to\": 120, \"oneWay\": \"yes\" }")
                .getAsJsonObject());
        assertNotNull(wall);
        assertFalse(wall.oneWay(), "不是布尔值就当没写");
    }

    // ==================== 下落：从上面接住 ====================

    @Test
    void fallingFromAboveIsCaught() {
        AirWall wall = platform();
        // 站在台面上：脚底 y=100，本帧往下走 3px 就会陷进去
        assertTrue(wall.blocksFall(100, 10, 83, W, H), "从台面上方落下来要被接住");
    }

    /** 快速下落：候选位置整块陷进平台里，起点却还在台面上方 —— 也要被接住。 */
    @Test
    void fastFallIsStillCaught() {
        AirWall wall = platform();
        // 脚底原本在 90（台面上方 10px），这一帧直接掉到脚底 130（穿过整块平台）
        assertTrue(wall.blocksFall(90, 10, 110, W, H),
                "高速下落时判据必须看「起点」而不是只看候选位置，否则会直接穿过去");
    }

    @Test
    void fallingButNotReachingIsFree() {
        AirWall wall = platform();
        // 脚底从 40 掉到 60，还没碰到台面
        assertFalse(wall.blocksFall(40, 10, 40, W, H));
    }

    // ==================== 上升：从下面穿过 ====================

    @Test
    void risingFromBelowPassesThrough() {
        AirWall wall = platform();
        // 脚底原本在 140（台面下方 40px），上升中候选位置已经压到平台内部
        assertFalse(wall.blocksFall(140, 10, 110, W, H),
                "从下面往上穿时不能被挡住 —— 否则会卡在台面里");
    }

    @Test
    void boxAlreadyStuckInsideDoesNotBlock() {
        AirWall wall = platform();
        // 脚底在台面下方（正在穿），即便候选位置与平台相交也不该挡
        assertFalse(wall.blocksFall(120, 10, 105, W, H));
    }

    // ==================== 横向与实心墙 ====================

    @Test
    void sidewaysIgnoresOneWayButRespectsSolid() {
        AirWall wall = platform();
        // 平台内部的盒子：相交为真，但横向移动不该被单向平台拦住
        assertTrue(wall.intersects(10, 105, W, H));
        assertFalse(AirWall.anyBlocksSideways(List.of(wall), 10, 105, W, H),
                "单向平台的侧面是通的");

        AirWall solidWall = solid();
        assertTrue(AirWall.anyBlocksSideways(List.of(solidWall), 10, 105, W, H),
                "实心墙横向照挡");
    }

    @Test
    void solidWallFallsTheSameWayFromAnyDirection() {
        AirWall wall = solid();
        assertTrue(wall.blocksFall(100, 10, 85, W, H));
        assertTrue(wall.blocksFall(140, 10, 105, W, H),
                "实心墙不看方向：脚底在下面也照样挡");
        assertTrue(wall.blocksFall(Double.NEGATIVE_INFINITY, 10, 105, W, H));
    }

    // ==================== 批量判定 ====================

    @Test
    void sidebarBatch() {
        assertFalse(AirWall.anyBlocksSideways(null, 0, 0, W, H));
        assertFalse(AirWall.anyBlocksSideways(List.of(), 0, 0, W, H));
    }

    @Test
    void passOneWayWindowIgnoresOneWayWallsOnly() {
        AirWall wall = platform();
        // 从上面落下来本该被接住
        assertTrue(AirWall.anyBlocksFall(List.of(wall), 100, 10, 83, W, H, false));
        // 但处于「按向下键穿下去」的窗口内时，单向平台整体失效
        assertFalse(AirWall.anyBlocksFall(List.of(wall), 100, 10, 83, W, H, true),
                "穿越窗口内必须完全忽略单向平台");

        AirWall solidWall = solid();
        assertTrue(AirWall.anyBlocksFall(List.of(solidWall), 100, 10, 83, W, H, true),
                "穿越窗口不该让人穿实心墙");
    }

    @Test
    void oneWayAmongManyShapes() {
        AirWall wall = platform();
        AirWall other = solid();
        assertTrue(AirWall.anyBlocksFall(List.of(wall, other), 100, 10, 83, W, H, false));
        assertTrue(AirWall.anyBlocksFall(List.of(wall, other), 100, 10, 83, W, H, true),
                "同一组里还有实心墙，就仍然被挡住");
    }

    // ==================== 站在单向平台上 ====================

    @Test
    void standingOnOneWayIsDetected() {
        AirWall wall = platform();
        // 脚底正好贴在台面上（y=80 → 脚底 100）
        assertTrue(AirWall.anyOneWayBelow(List.of(wall), 10, 80, W, H, 2));
        // 离台面还有 20px
        assertFalse(AirWall.anyOneWayBelow(List.of(wall), 10, 60, W, H, 2));
        // 同样位置换成实心墙就不算「踩着单向平台」
        assertFalse(AirWall.anyOneWayBelow(List.of(solid()), 10, 80, W, H, 2));
    }

    @Test
    void oneWayBelowHandlesEmptyInput() {
        assertFalse(AirWall.anyOneWayBelow(null, 0, 0, W, H, 2));
        assertFalse(AirWall.anyOneWayBelow(List.of(), 0, 0, W, H, 2));
    }
}

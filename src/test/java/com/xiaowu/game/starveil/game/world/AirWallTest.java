package com.xiaowu.game.starveil.game.world;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 空气墙形状测试。
 *
 * <p>重点是「非矩形」这条：多边形与圆形的相交判定必须是<b>几何正确</b>的，
 * 而不是「用外接矩形近似」—— 后者会让斜坡顶端的空气墙莫名其妙挡人。
 *
 * <p>这些是纯数学断言，不需要 JavaFX 工具包（{@code buildNode} 才需要，不在这里测）。
 */
class AirWallTest {

    /** 一个 100×100 的正方形，左上角在 (0,0)。 */
    private static AirWall square() {
        return AirWall.fromJson(JsonParser.parseString(
                "{ \"polygon\": [[0,0],[100,0],[100,100],[0,100]] }").getAsJsonObject(), false);
    }

    /** 直角三角形：(0,0) (100,0) (0,100) —— 斜边从 (100,0) 到 (0,100)。 */
    private static AirWall triangle() {
        return AirWall.fromJson(JsonParser.parseString(
                "{ \"polygon\": [[0,0],[100,0],[0,100]] }").getAsJsonObject(), false);
    }

    // ==================== 解析 ====================

    @Test
    void rectangleShorthandBecomesAPolygon() {
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"x\": 0, \"y\": 860, \"x_to\": 450, \"y_to\": 884 }").getAsJsonObject(), false);
        assertNotNull(wall);
        assertTrue(wall instanceof AirWall.Poly, "老写法应当被解析成多边形");
        // 矩形的行为与以前一致
        assertTrue(wall.intersects(10, 862, 48, 64));
        assertFalse(wall.intersects(10, 700, 48, 64));
    }

    @Test
    void rectanglesStillNormaliseReversedCorners() {
        // 反向书写曾静默退化成 1px 宽的线（wu-home.json 的斜墙踩过这个坑）
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"x\": 599, \"y\": 810, \"x_to\": 834, \"y_to\": 736 }").getAsJsonObject(), false);
        assertNotNull(wall);
        assertTrue(wall.intersects(700, 780, 48, 64), "反向书写的矩形仍然要挡住");
    }

    @Test
    void malformedFallsBackToNull() {
        assertNull(AirWall.fromJson(JsonParser.parseString(
                "{ \"polygon\": [[0,0],[10,0]] }").getAsJsonObject(), false),
                "顶点不足 3 个要拒绝");
        assertNull(AirWall.fromJson(JsonParser.parseString(
                "{ \"polygon\": [[0,0],[10,\"x\"],[10,10]] }").getAsJsonObject(), false),
                "顶点不是 [x,y] 要拒绝");
        assertNull(AirWall.fromJson(JsonParser.parseString(
                "{ \"circle\": { \"x\": 1, \"y\": 2 } }").getAsJsonObject(), false),
                "圆缺 r 要拒绝");
        assertNull(AirWall.fromJson(JsonParser.parseString(
                "{ \"circle\": { \"x\": 1, \"y\": 2, \"r\": 0 } }").getAsJsonObject(), false),
                "半径必须为正");
    }

    @Test
    void nullForEmptyInput() {
        assertNull(AirWall.fromJson(null, false));
    }

    // ==================== 多边形相交 ====================

    @Test
    void boxFullyInsidePolygonIsBlocked() {
        AirWall wall = square();
        assertTrue(wall.intersects(20, 20, 48, 64), "整个盒在多边形内部应当算挡住");
    }

    @Test
    void boxFullyOutsidePolygonIsFree() {
        AirWall wall = square();
        assertFalse(wall.intersects(200, 200, 48, 64));
        assertFalse(wall.intersects(-100, -100, 48, 64), "紧挨左上角外侧不该被挡");
    }

    @Test
    void boxOverlappingEdgeIsBlocked() {
        AirWall wall = square();
        assertTrue(wall.intersects(80, 20, 48, 64), "盒压住右边界应当算挡住");
    }

    /**
     * 细长的斜坡整条穿过包围盒 —— 四个角都不在多边形内，边却穿过了。
     *
     * <p>这正是「只判顶点是否在形状内」会漏掉的情形，所以边相交那条判据不能省。
     */
    @Test
    void thinSlopeCrossingTheBoxIsBlocked() {
        // 一条从 (0,50) 到 (200,60) 的极扁三角形
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"polygon\": [[0,50],[200,60],[0,60]] }").getAsJsonObject(), false);
        // 盒在 x=100 附近、y 从 40 到 120：四个角都在三角形外，但斜边穿过它
        assertTrue(wall.intersects(100, 40, 10, 80),
                "斜边穿过包围盒时也必须算挡住（只判顶点会漏）");
    }

    /**
     * 斜坡顶端的「外接矩形区域」不该被挡。
     *
     * <p>这是改用真实几何而不是外接矩形的<b>核心收益</b>：三角形右上角那片空白
     * 在几何上不是障碍，角色应当能从那里跳过去。
     */
    @Test
    void cornerOutsideTheTriangleIsNotBlocked() {
        AirWall wall = triangle();
        // 斜边是 x + y = 100。盒放在 x=70..118, y=70..134，整体在斜边之外
        assertFalse(wall.intersects(70, 70, 48, 64),
                "三角形右下方（斜边之外）不该被挡 —— 用外接矩形近似就会误判成挡住");
        // 同一位置的左上半边确实在三角形里
        assertTrue(wall.intersects(10, 10, 20, 20));
    }

    @Test
    void concavePolygonUsesItsRealShape() {
        // 一个 U 形（凹多边形）：中间那道凹口不该挡人
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"polygon\": [[0,0],[100,0],[100,100],[70,100],[70,30],[30,30],[30,100],[0,100]] }")
                .getAsJsonObject(), false);
        assertNotNull(wall);
        assertTrue(wall.intersects(0, 0, 20, 20), "左侧实心部分要挡");
        assertTrue(wall.intersects(80, 0, 20, 20), "右侧实心部分要挡");
        assertFalse(wall.intersects(40, 60, 20, 20), "凹口内部不该被挡");
    }

    // ==================== 圆形 ====================

    @Test
    void circleUsesDistanceNotBoundingBox() {
        AirWall wall = AirWall.fromJson(JsonParser.parseString(
                "{ \"circle\": { \"x\": 100, \"y\": 100, \"r\": 50 } }").getAsJsonObject(), false);
        assertNotNull(wall);

        // 圆心正上方的盒：贴到圆的顶部才该挡
        assertTrue(wall.intersects(90, 60, 20, 20), "盒压到圆上要挡");
        assertFalse(wall.intersects(90, 0, 20, 20), "圆上方够远的盒不该挡");

        // 外接矩形的四个角（距离圆心约 70px > 50）不该被挡 —— 又一次是「真实形状」的价值
        assertFalse(wall.intersects(40, 40, 10, 10),
                "外接矩形左上角在圆外，不该被挡");
    }

    // ==================== 大小写一致的接口 ====================

    @Test
    void anyIntersectsNeedsOnlyOneHit() {
        var walls = java.util.List.of(square());
        assertTrue(AirWall.anyIntersects(walls, 20, 20, 10, 10));
        assertFalse(AirWall.anyIntersects(walls, 500, 500, 10, 10));
        assertFalse(AirWall.anyIntersects(java.util.List.of(), 0, 0, 10, 10));
        assertFalse(AirWall.anyIntersects(null, 0, 0, 10, 10));
    }
}

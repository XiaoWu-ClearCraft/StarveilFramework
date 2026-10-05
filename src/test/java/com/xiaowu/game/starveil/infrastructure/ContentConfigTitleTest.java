package com.xiaowu.game.starveil.infrastructure;

import com.xiaowu.game.starveil.config.GameConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 游戏名 / 窗口标题的配置。
 *
 * <p>框架是通用的：内容没声明之前，它不该表现出自己是某个具体的游戏，
 * 所以默认是 {@code My Game}；内容在 {@code init} 里声明之后，
 * 窗口标题、占位符、旁白说话人都跟着变。
 */
class ContentConfigTitleTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContentConfig.resetForTest();
    }

    @Test
    void defaultGameNameIsGeneric() {
        assertEquals("My Game", ContentConfig.gameName(),
                "框架不预设自己跑的是哪个游戏");
        assertEquals("My Game", GameConstants.gameTitle());
    }

    @Test
    void gameNameIsUsedInTitlesAndPlaceholders() {
        ContentConfig.setGameName("测试之诗");

        assertEquals("测试之诗", GameConstants.gameTitle(), "占位符 / 旁白说话人用游戏名");
        assertEquals("测试之诗 - 主菜单", GameConstants.mainMenuTitle(), "主菜单窗口标题自动拼后缀");
        assertEquals("测试之诗", GameConstants.gameWindowTitle(), "游戏窗口标题就是游戏名");
        assertFalse(ContentConfig.hasWindowTitle(), "没显式设过窗口标题");
    }

    @Test
    void explicitWindowTitleWinsAndIsNotSuffixed() {
        ContentConfig.setGameName("测试之诗");
        ContentConfig.setWindowTitle("我的标题");

        assertTrue(ContentConfig.hasWindowTitle());
        assertEquals("我的标题", ContentConfig.windowTitle());
        // 玩家自定义的标题不该被框架再加工
        assertEquals("我的标题", GameConstants.mainMenuTitle());
        assertEquals("我的标题", GameConstants.gameWindowTitle());
        // 但游戏名本身不受影响（占位符 / 旁白仍是游戏名）
        assertEquals("测试之诗", GameConstants.gameTitle());
    }

    @Test
    void blankValuesFallBackToDefaults() {
        ContentConfig.setGameName("  ");
        assertEquals("My Game", ContentConfig.gameName(), "空游戏名回退默认值");

        ContentConfig.setGameName("测试之诗");
        ContentConfig.setWindowTitle("   ");
        assertFalse(ContentConfig.hasWindowTitle(), "空窗口标题视为未设置");
        assertEquals("测试之诗 - 主菜单", GameConstants.mainMenuTitle());
    }

    @Test
    void namesAreTrimmed() {
        ContentConfig.setGameName("  测试之诗  ");
        assertEquals("测试之诗", ContentConfig.gameName());
    }
}

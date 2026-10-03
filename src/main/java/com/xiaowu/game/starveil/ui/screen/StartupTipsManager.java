package com.xiaowu.game.starveil.ui.screen;
import com.xiaowu.game.starveil.infrastructure.Fonts;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.input.InputHandler;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 启动提示管理器 - 在菜单显示前展示提示信息
 */
public class StartupTipsManager {
    
    private static StartupTipsManager instance;
    
    private Stage stage;
    private Scene originalScene;
    private InputHandler inputHandler;
    private StackPane root;
    private Parent originalRoot; // 保存原始场景根节点
    private List<TipData> tips;
    private int currentTipIndex = 0;
    private boolean isPlaying = false;
    private boolean isSkipping = false;
    private FadeTransition currentFadeTransition;
    private Runnable onCompleteCallback;
    
    // 提示数据类
    private static class TipData {
        String title;
        String text;
    }
    
    private StartupTipsManager() {
    }
    
    public static StartupTipsManager getInstance() {
        if (instance == null) {
            instance = new StartupTipsManager();
        }
        return instance;
    }
    
    /**
     * 显示启动提示
     * @param stage 舞台
     * @param scene 场景
     * @param inputHandler 输入处理器
     * @param onComplete 完成回调
     */
    public void showTips(Stage stage, Scene scene, InputHandler inputHandler, Runnable onComplete) {
        if (isPlaying) {
            LoggerManager.Logger("WARN", "Startup tips already playing");
            return;
        }
        
        this.stage = stage;
        this.originalScene = scene;
        this.inputHandler = inputHandler;
        this.onCompleteCallback = onComplete;
        this.isPlaying = true;
        this.isSkipping = false;
        
        // 加载提示数据
        if (!loadTipsData()) {
            // 如果加载失败，直接完成
            isPlaying = false;
            if (onComplete != null) {
                Platform.runLater(onComplete);
            }
            return;
        }
        
        if (tips.isEmpty()) {
            LoggerManager.Logger("WARN", "No tips loaded");
            isPlaying = false;
            if (onComplete != null) {
                Platform.runLater(onComplete);
            }
            return;
        }
        
        currentTipIndex = 0;
        
        // 保存原始场景根节点
        originalRoot = scene.getRoot();
        
        // 创建黑色背景覆盖层
        root = new StackPane();
        root.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        root.setOpacity(0.0);
        
        // 设置按键事件处理
        setupKeyHandlers();
        
        // 替换场景根节点
        scene.setRoot(root);
        
        // 淡入背景
        currentFadeTransition = new FadeTransition(Duration.seconds(0.5), root);
        currentFadeTransition.setFromValue(0.0);
        currentFadeTransition.setToValue(1.0);
        
        currentFadeTransition.setOnFinished(e -> {
            if (!isSkipping) {
                // 开始显示提示
                showNextTip();
            } else {
                // 用户在淡入期间跳过
                finishTips();
            }
        });
        
        currentFadeTransition.play();
        
        LoggerManager.Logger("INFO", "Startup tips started with " + tips.size() + " tips");
    }
    
    /**
     * 加载提示数据
     */
    private boolean loadTipsData() {
        try {
            String jsonPath = "starveil:data/config/startup-tips.json";
            InputStream is = ResourceResolver.getResourceAsStream(jsonPath);
            
            if (is == null) {
                LoggerManager.Logger("WARN", "Startup tips JSON file not found: " + jsonPath);
                return false;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, TipData>>(){}.getType();
            Map<String, TipData> tipsMap = gson.fromJson(sb.toString(), type);
            
            if (tipsMap != null && !tipsMap.isEmpty()) {
                tips = new ArrayList<>(tipsMap.values());
                LoggerManager.Logger("INFO", "Loaded " + tips.size() + " startup tips");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "Failed to load startup tips: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 显示下一个提示
     */
    private void showNextTip() {
        if (isSkipping || currentTipIndex >= tips.size()) {
            // 所有提示显示完成或用户跳过
            finishTips();
            return;
        }
        
        TipData tip = tips.get(currentTipIndex);
        LoggerManager.Logger("DEBUG", "Showing tip " + (currentTipIndex + 1) + "/" + tips.size() + ": " + tip.title);
        
        // 创建提示内容
        VBox tipContent = createTipContent(tip);
        
        root.getChildren().clear();
        root.getChildren().add(tipContent);
        
        // 淡入提示
        currentFadeTransition = new FadeTransition(Duration.seconds(0.8), tipContent);
        currentFadeTransition.setFromValue(0.0);
        currentFadeTransition.setToValue(1.0);
        
        currentFadeTransition.setOnFinished(e -> {
            if (isSkipping) {
                // 用户在淡入期间跳过
                finishTips();
                return;
            }
            
            // 显示一段时间后淡出
            currentFadeTransition = new FadeTransition(Duration.seconds(0.8), tipContent);
            currentFadeTransition.setFromValue(1.0);
            currentFadeTransition.setToValue(0.0);
            
            currentFadeTransition.setOnFinished(e2 -> {
                if (isSkipping) {
                    finishTips();
                } else {
                    currentTipIndex++;
                    showNextTip();
                }
            });
            
            currentFadeTransition.setDelay(Duration.seconds(3.0)); // 显示3秒
            currentFadeTransition.play();
        });
        
        currentFadeTransition.play();
    }
    
    /**
     * 创建提示内容
     */
    private VBox createTipContent(TipData tip) {
        VBox container = new VBox(20);
        container.setAlignment(Pos.CENTER);
        container.setOpacity(0.0);
        
        // 标题
        Label titleLabel = new Label(tip.title);
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 32));
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setAlignment(Pos.CENTER);
        
        // 横线 - 使用StackPane确保居中
        StackPane lineContainer = new StackPane();
        lineContainer.setAlignment(Pos.CENTER);
        Line separator = new Line();
        separator.setStroke(Color.WHITE);
        separator.setStrokeWidth(2);
        separator.setStartX(-150); // 居中,总长度300
        separator.setEndX(150);
        lineContainer.getChildren().add(separator);
        
        // 内容文本 - 使用TextFlow支持多色文本
        StackPane textContainer = new StackPane();
        textContainer.setAlignment(Pos.CENTER);
        textContainer.setMaxWidth(600);
        
        javafx.scene.text.TextFlow textFlow = new javafx.scene.text.TextFlow();
        textFlow.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        // 解析颜色标签并创建Text节点
        java.util.List<javafx.scene.text.Text> textNodes = parseColoredText(tip.text, 20);
        textFlow.getChildren().addAll(textNodes);
        
        textContainer.getChildren().add(textFlow);
        
        container.getChildren().addAll(titleLabel, lineContainer, textContainer);
        
        return container;
    }
    
    /**
     * 解析带颜色标签的文本
     * 支持格式: <#颜色代码>文本</颜色代码> 或 <颜色名称>文本</颜色名称>
     */
    private java.util.List<javafx.scene.text.Text> parseColoredText(String text, int fontSize) {
        java.util.List<javafx.scene.text.Text> result = new java.util.ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            result.add(createText("", "#FFFFFF", fontSize)); // 默认白色
            return result;
        }
        
        int i = 0;
        String currentColor = "#FFFFFF"; // 默认白色
        
        while (i < text.length()) {
            int nextTagStart = text.indexOf('<', i);
            
            if (nextTagStart == -1) {
                // 没有更多标签，添加剩余文本
                if (i < text.length()) {
                    result.add(createText(text.substring(i), currentColor, fontSize));
                }
                break;
            }
            
            // 添加标签前的普通文本
            if (nextTagStart > i) {
                result.add(createText(text.substring(i, nextTagStart), currentColor, fontSize));
            }
            
            // 查找标签结束
            int tagEnd = text.indexOf('>', nextTagStart);
            if (tagEnd == -1) {
                // 格式错误，添加剩余文本并退出
                result.add(createText(text.substring(nextTagStart), currentColor, fontSize));
                break;
            }
            
            String tagContent = text.substring(nextTagStart + 1, tagEnd);
            
            if (tagContent.startsWith("/")) {
                // 结束标签 - 恢复默认颜色
                currentColor = "#FFFFFF";
                i = tagEnd + 1;
                continue; // 继续处理后续文本
            } else {
                // 开始标签 - 解析颜色并提取标签后的文本
                String parsedColor = parseColorTag(tagContent);
                
                if (parsedColor != null) {
                    currentColor = parsedColor;
                }
                
                // 查找对应的结束标签
                String endTag = "</" + tagContent + ">";
                int endTagStart = text.indexOf(endTag, tagEnd);
                
                if (endTagStart == -1) {
                    // 没有结束标签，将剩余文本作为当前颜色
                    String remainingText = text.substring(tagEnd + 1);
                    result.add(createText(remainingText, currentColor, fontSize));
                    break;
                }
                
                // 提取标签内的文本
                String coloredText = text.substring(tagEnd + 1, endTagStart);
                result.add(createText(coloredText, currentColor, fontSize));
                
                // 恢复默认颜色
                currentColor = "#FFFFFF";
                
                // 移动到结束标签之后
                i = endTagStart + endTag.length();
            }
        }
        
        if (result.isEmpty()) {
            result.add(createText("", "#FFFFFF", fontSize));
        }
        
        return result;
    }
    
    /**
     * 解析颜色标签
     */
    private String parseColorTag(String tagContent) {
        // 检查是否是十六进制颜色 <#color>
        if (tagContent.startsWith("#")) {
            String colorCode = tagContent.substring(1);
            if (isValidColorCode(colorCode)) {
                return "#" + colorCode;
            }
        }
        
        // 检查是否是颜色名称
        return getColorHexFromName(tagContent.toLowerCase());
    }
    
    /**
     * 获取颜色名称对应的十六进制值
     */
    private String getColorHexFromName(String colorName) {
        return switch (colorName) {
            case "red" -> "#FF0000";
            case "green" -> "#00FF00";
            case "blue" -> "#0000FF";
            case "yellow" -> "#FFFF00";
            case "orange" -> "#FFA500";
            case "purple" -> "#800080";
            case "pink" -> "#FFC0CB";
            case "white" -> "#FFFFFF";
            case "black" -> "#000000";
            case "gray", "grey" -> "#808080";
            case "lightgray", "lightgrey" -> "#D3D3D3";
            case "darkgray", "darkgrey" -> "#A9A9A9";
            case "cyan" -> "#00FFFF";
            case "magenta" -> "#FF00FF";
            case "lime" -> "#00FF00";
            case "maroon" -> "#800000";
            case "navy" -> "#000080";
            case "olive" -> "#808000";
            case "teal" -> "#008080";
            case "silver" -> "#C0C0C0";
            case "gold" -> "#FFD700";
            case "indigo" -> "#4B0082";
            case "violet" -> "#EE82EE";
            case "rose" -> "#FF007F";
            case "azure" -> "#007FFF";
            case "beige" -> "#F5F5DC";
            case "brown" -> "#A52A2A";
            case "crimson" -> "#DC143C";
            case "coral" -> "#FF7F50";
            case "fuchsia" -> "#FF00FF";
            case "ivory" -> "#FFFFF0";
            case "khaki" -> "#F0E68C";
            case "lavender" -> "#E6E6FA";
            case "linen" -> "#FAF0E6";
            case "plum" -> "#DDA0DD";
            case "salmon" -> "#FA8072";
            case "tan" -> "#D2B48C";
            case "turquoise" -> "#40E0D0";
            case "wheat" -> "#F5DEB3";
            default -> null;
        };
    }
    
    /**
     * 验证颜色代码格式
     */
    private boolean isValidColorCode(String code) {
        return code.matches("^[0-9a-fA-F]{6}$") || code.matches("^[0-9a-fA-F]{3}$");
    }
    
    /**
     * 创建Text节点
     */
    private javafx.scene.text.Text createText(String text, String color, int fontSize) {
        javafx.scene.text.Text textNode = new javafx.scene.text.Text(text);
        textNode.setFill(Color.web(color));
        textNode.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", fontSize));
        return textNode;
    }
    
    private InputHandler.KeyEventHandler escKeyHandler;
    private InputHandler.KeyEventHandler spaceKeyHandler;

    /**
     * 设置按键事件处理
     */
    private void setupKeyHandlers() {
        if (inputHandler == null) return;
        escKeyHandler = e -> {
            if (isPlaying) {
                e.consume();
                skipTips();
            }
        };
        spaceKeyHandler = e -> {
            if (isPlaying) {
                e.consume();
                skipTips();
            }
        };
        inputHandler.onKeyPressed(javafx.scene.input.KeyCode.ESCAPE, escKeyHandler);
        inputHandler.onKeyPressed(javafx.scene.input.KeyCode.SPACE, spaceKeyHandler);
    }

    private void cleanupKeyHandlers() {
        if (inputHandler == null) return;
        inputHandler.removeKeyPressedHandler(javafx.scene.input.KeyCode.ESCAPE.ordinal(), escKeyHandler);
        inputHandler.removeKeyPressedHandler(javafx.scene.input.KeyCode.SPACE.ordinal(), spaceKeyHandler);
        escKeyHandler = null;
        spaceKeyHandler = null;
    }

    private void skipTips() {
        isSkipping = true;

        // 立即停止当前动画
        if (currentFadeTransition != null) {
            currentFadeTransition.stop();
        }

        // 立即退出，使用保存的原始根节点
        finishTips();
    }

    /**
     * 完成提示显示
     */
    private void finishTips() {
        if (!isPlaying) {
            return;
        }
        
        isPlaying = false;
        cleanupKeyHandlers();
        LoggerManager.Logger("INFO", "Startup tips finished");
        
        // 恢复原始场景根节点
        originalScene.setRoot(originalRoot);
        
        // 执行完成回调
        if (onCompleteCallback != null) {
            Platform.runLater(onCompleteCallback);
        }
    }
    
    /**
     * 检查是否正在播放
     */
    public boolean isPlaying() {
        return isPlaying;
    }
}
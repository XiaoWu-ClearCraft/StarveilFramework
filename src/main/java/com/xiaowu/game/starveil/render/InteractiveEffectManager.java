package com.xiaowu.game.starveil.render;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;

import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.config.GameConstants;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

 
public class InteractiveEffectManager {
    private static InteractiveEffectManager instance;

    private StackPane effectContainer;
    private Pane overlay;
    private GameManager gameManager;

     
    // 使用粉色系配色
    private final String PRIMARY_COLOR = ContentConfig.primaryColor();
    private final String SECONDARY_COLOR = ContentConfig.secondaryColor();
    private final String ACCENT_COLOR = "#FFB6C1";
    private final String DANGER_COLOR = "#FF6B6B";
    private final String SUCCESS_COLOR = ContentConfig.primaryColor();
    private final String WARNING_COLOR = "#FFA500";
    private final String TEXT_PRIMARY = "#FFE4E1";
    private final String TEXT_SECONDARY = ContentConfig.tertiaryColor();

     
    private volatile boolean isChallengeActive = false;
    private final List<EventHandler<KeyEvent>> activeKeyHandlers = new ArrayList<>();
    private final List<EventHandler<MouseEvent>> activeMouseHandlers = new ArrayList<>();
    private final List<Timeline> activeTimelines = new ArrayList<>();

    public InteractiveEffectManager() {
        initialize();
    }

    public static InteractiveEffectManager getInstance() {
        if (instance == null) {
            instance = new InteractiveEffectManager();
        }
        return instance;
    }

     
    public void setGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    private void initialize() {
        effectContainer = new StackPane();
        effectContainer.setAlignment(Pos.CENTER);
        effectContainer.setMouseTransparent(true);
        effectContainer.setPickOnBounds(false);
        effectContainer.setVisible(false);
        effectContainer.setStyle("-fx-background-color: transparent;");

         
        overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.1);");
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);

        effectContainer.getChildren().add(overlay);
    }

    public StackPane getEffectContainer() {
        return effectContainer;
    }

     
    public boolean isChallengeActive() {
        return isChallengeActive;
    }

     
    private Scene getCurrentScene() {
        if (gameManager != null) {
            Scene scene = gameManager.getCurrentScene();
            if (scene != null) {
                return scene;
            }

            Stage primaryStage = gameManager.getPrimaryStage();
            if (primaryStage != null) {
                scene = primaryStage.getScene();
                if (scene != null) {
                    return scene;
                }
            }
        }

        Scene scene = effectContainer.getScene();
        if (scene == null) {
            debugLog("无法获取当前场景");
        }
        return scene;
    }

     

    public CompletableFuture<Boolean> startProgressChallenge(double duration, double requiredProgress, boolean isPlotKill) {
        if (requiredProgress > 1.0) {
            debugLog("警告：requiredProgress=" + requiredProgress + " 超出合理范围(0.0-1.0)，已自动修正为1.0");
            requiredProgress = 1.0;
        } else if (requiredProgress <= 0) {
            debugLog("警告：requiredProgress=" + requiredProgress + " 过小，已自动修正为0.1");
            requiredProgress = 0.1;
        }

        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        if (isChallengeActive) {
            debugLog("已有挑战在进行中，拒绝新挑战");
            resultFuture.complete(false);
            return resultFuture;
        }

        Platform.runLater(() -> {
            if (effectContainer.getChildren().size() > 1) {
                debugLog("清理残留内容");
                effectContainer.getChildren().removeIf(node -> node != overlay);
            }
        });

        double finalRequiredProgress = requiredProgress;
        Platform.runLater(() -> {
            try {
                isChallengeActive = true;
                setupEffectEnvironment();

                showTutorialPanel("快速按下空格键填充进度条！\n\n在限定时间内达到目标进度即可成功。",
                        () -> startProgressChallengeAfterTutorial(duration, finalRequiredProgress, isPlotKill, resultFuture));
            } catch (Exception e) {
                safeCompleteFuture(resultFuture, isPlotKill, "进度条挑战初始化失败: " + e.getMessage());
            }
        });

        return resultFuture;
    }

    private void startProgressChallengeAfterTutorial(double duration, double requiredProgress, boolean isPlotKill,
                                                     CompletableFuture<Boolean> resultFuture) {
        VBox challengePanel = createProgressChallengePanel(duration, requiredProgress, isPlotKill, resultFuture);
        effectContainer.getChildren().add(challengePanel);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), challengePanel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private VBox createProgressChallengePanel(double duration, double requiredProgress, boolean isPlotKill,
                                              CompletableFuture<Boolean> resultFuture) {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));
        panel.setMaxSize(500, 300);
        panel.setStyle("-fx-background-color: transparent;");

        Label titleLabel = new Label(isPlotKill ? "紧急情况！" : "快速按下空格键！");
        titleLabel.setStyle("-fx-text-fill: " + (isPlotKill ? DANGER_COLOR : TEXT_PRIMARY) + "; " +
                "-fx-font-size: 20px; -fx-font-weight: bold;");

         
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefSize(300, 25);
        progressBar.setStyle("-fx-accent: " + (isPlotKill ? DANGER_COLOR : SUCCESS_COLOR) + "; " +
                "-fx-background-color: rgba(255, 255, 255, 0.2);");

         
        Label timerLabel = new Label(String.format("%.1f秒", duration));
        timerLabel.setStyle("-fx-text-fill: " + (isPlotKill ? DANGER_COLOR : WARNING_COLOR) + "; " +
                "-fx-font-size: 16px;");

         
        Label progressLabel = new Label("0%");
        progressLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; " +
                "-fx-font-size: 14px;");

        panel.getChildren().addAll(titleLabel, progressBar, progressLabel, timerLabel);

         
        DoubleProperty currentProgress = new SimpleDoubleProperty(0);
        Timeline decreaseTimeline = new Timeline();
        Timeline challengeTimeline = new Timeline();

        registerTimeline(decreaseTimeline);
        registerTimeline(challengeTimeline);

         
        AtomicBoolean challengeCompleted = new AtomicBoolean(false);

        final double decreaseRate = isPlotKill ? 0.05 : 0.02;
        final double increaseRate = isPlotKill ? 0.06 : 0.10;

         
        progressBar.progressProperty().bind(currentProgress);

         
        currentProgress.addListener((obs, oldVal, newVal) -> {
            double progress = newVal.doubleValue();
            double oldProgress = oldVal.doubleValue();

             
            int percentage = (int) (progress * 100);
            progressLabel.setText(percentage + "%");

             
            if (progress >= requiredProgress && !challengeCompleted.get()) {
                challengeCompleted.set(true);
                debugLog("进度达标 (" + percentage + "%)，挑战成功！");
                stopAllTimelines();
                completeChallenge(true, panel, resultFuture);
            }
        });

         
        KeyFrame decreaseFrame = new KeyFrame(Duration.millis(50), e -> {
            if (challengeCompleted.get()) return;

            double newProgress = currentProgress.get() - decreaseRate;
            currentProgress.set(Math.max(0, newProgress));
        });
        decreaseTimeline.getKeyFrames().add(decreaseFrame);
        decreaseTimeline.setCycleCount(Timeline.INDEFINITE);

         
        double[] remainingTime = {duration};
        KeyFrame timerFrame = new KeyFrame(Duration.millis(100), e -> {
            if (challengeCompleted.get()) return;

            remainingTime[0] -= 0.1;
            timerLabel.setText(String.format("%.1f秒", Math.max(0, remainingTime[0])));

            if (remainingTime[0] <= 0 && !challengeCompleted.get()) {
                challengeCompleted.set(true);
                debugLog("时间结束，进度: " + (int)(currentProgress.get() * 100) + "%");
                stopAllTimelines();
                 
                boolean success = currentProgress.get() >= requiredProgress;
                completeChallenge(success, panel, resultFuture);
            }
        });
        challengeTimeline.getKeyFrames().add(timerFrame);
        challengeTimeline.setCycleCount(Timeline.INDEFINITE);

         
        EventHandler<KeyEvent> keyHandler = event -> {
            if (event.getCode() == KeyCode.SPACE && !challengeCompleted.get()) {
                event.consume();
                double oldProgress = currentProgress.get();
                double newProgress = oldProgress + increaseRate;
                currentProgress.set(Math.min(1.0, newProgress));

                debugLog("空格键按下，进度: " + (int)(oldProgress * 100) + "% -> " + (int)(newProgress * 100) + "%");

                 
                playProgressBarFeedback(progressBar);
            }
        };

         
        Timeline endTimeline = new Timeline(
                new KeyFrame(Duration.seconds(duration), e -> {
                    if (!challengeCompleted.get()) {
                        challengeCompleted.set(true);
                        debugLog("结束时间线触发，进度: " + (int)(currentProgress.get() * 100) + "%");
                        stopAllTimelines();
                         
                        boolean success = currentProgress.get() >= requiredProgress;
                        completeChallenge(success, panel, resultFuture);
                    }
                })
        );
        registerTimeline(endTimeline);

         
        decreaseTimeline.play();
        challengeTimeline.play();
        endTimeline.play();
        registerKeyHandler(keyHandler);

        setupCleanup(resultFuture, keyHandler, decreaseTimeline, challengeTimeline, endTimeline);
        return panel;
    }

     
    private void playProgressBarFeedback(ProgressBar progressBar) {
        Timeline feedbackTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progressBar.scaleXProperty(), 1.0),
                        new KeyValue(progressBar.scaleYProperty(), 1.0)
                ),
                new KeyFrame(Duration.millis(50),
                        new KeyValue(progressBar.scaleXProperty(), 1.02),
                        new KeyValue(progressBar.scaleYProperty(), 1.02)
                ),
                new KeyFrame(Duration.millis(100),
                        new KeyValue(progressBar.scaleXProperty(), 1.0),
                        new KeyValue(progressBar.scaleYProperty(), 1.0)
                )
        );
        feedbackTimeline.play();
    }

     

    public CompletableFuture<Boolean> startCircleChallenge(double duration, boolean isPlotKill, double reactionTime) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        if (isChallengeActive) {
            debugLog("已有挑战在进行中，拒绝新挑战");
            resultFuture.complete(false);
            return resultFuture;
        }

        Platform.runLater(() -> {
            if (effectContainer.getChildren().size() > 1) {
                debugLog("清理残留内容");
                effectContainer.getChildren().removeIf(node -> node != overlay);
            }
        });

        Platform.runLater(() -> {
            try {
                isChallengeActive = true;
                setupEffectEnvironment();

                showTutorialPanel("按下屏幕上出现的对应字母键！\n\n在外圈缩小到内圈之前按下正确按键。",
                        () -> startCircleChallengeAfterTutorial(duration, isPlotKill, reactionTime, resultFuture));
            } catch (Exception e) {
                safeCompleteFuture(resultFuture, isPlotKill, "圆圈挑战初始化失败: " + e.getMessage());
            }
        });

        return resultFuture;
    }

    public CompletableFuture<Boolean> startCircleChallenge(double duration, boolean isPlotKill) {
        return startCircleChallenge(duration, isPlotKill, 2.0);
    }

    private void startCircleChallengeAfterTutorial(double duration, boolean isPlotKill, double reactionTime,
                                                   CompletableFuture<Boolean> resultFuture) {
        VBox challengePanel = createCircleChallengePanel(duration, isPlotKill, reactionTime, resultFuture);
        effectContainer.getChildren().add(challengePanel);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), challengePanel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private VBox createCircleChallengePanel(double duration, boolean isPlotKill, double reactionTime,
                                            CompletableFuture<Boolean> resultFuture) {
        VBox panel = new VBox(10);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(15));
        panel.setMaxSize(600, 400);
        panel.setStyle("-fx-background-color: transparent;");

        Label titleLabel = new Label(isPlotKill ? "危机！集中注意力！" : "按下对应的按键");
        titleLabel.setStyle("-fx-text-fill: " + (isPlotKill ? DANGER_COLOR : TEXT_PRIMARY) + "; " +
                "-fx-font-size: 18px; -fx-font-weight: bold;");

        HBox healthContainer = new HBox(5);
        healthContainer.setAlignment(Pos.CENTER);

        ProgressBar healthBar = new ProgressBar(1.0);
        healthBar.setPrefWidth(150);
        healthBar.setPrefHeight(12);
        healthBar.setStyle("-fx-accent: " + (isPlotKill ? DANGER_COLOR : SUCCESS_COLOR) + ";");

        healthContainer.getChildren().add(healthBar);

        Pane gameArea = new Pane();
        gameArea.setPrefSize(400, 250);
        gameArea.setStyle("-fx-background-color: rgba(51, 65, 85, 0.2);");

        Label timerLabel = new Label(String.format("%.1f秒", duration));
        timerLabel.setStyle("-fx-text-fill: " + (isPlotKill ? DANGER_COLOR : WARNING_COLOR) + "; " +
                "-fx-font-size: 16px;");

         
        Label circleCountLabel = new Label("当前圆圈: 0");
        circleCountLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 12px;");

        panel.getChildren().addAll(titleLabel, healthContainer, gameArea, timerLabel, circleCountLabel);

         
        DoubleProperty health = new SimpleDoubleProperty(1.0);
        Set<Character> activeKeys = Collections.synchronizedSet(new HashSet<>());
        List<CircleNode> activeCircles = Collections.synchronizedList(new ArrayList<>());
        Timeline challengeTimeline = new Timeline();

        registerTimeline(challengeTimeline);
        healthBar.progressProperty().bind(health);

         
        AtomicBoolean challengeCompleted = new AtomicBoolean(false);

         
        final double healthLoss = isPlotKill ? 0.2 : 0.15;
        final double healthGain = isPlotKill ? 0.12 : 0.18;
        final double minSpawnInterval = isPlotKill ? 0.2 : 0.3;
        final int maxConcurrentCircles = isPlotKill ? 5 : 4;

         
        EventHandler<KeyEvent> keyHandler = createCircleKeyHandler(activeKeys, activeCircles, health, healthGain, gameArea, circleCountLabel);
        registerKeyHandler(keyHandler);

         
        double[] timeCounter = {0};
        AtomicReference<Double> spawnInterval = new AtomicReference<>(isPlotKill ? 1.0 : 1.5);
        double[] lastSpawnTime = {0};

        KeyFrame gameFrame = new KeyFrame(Duration.millis(100), e -> {
            if (challengeCompleted.get()) return;

            timeCounter[0] += 0.1;
            double remainingTime = duration - timeCounter[0];
            timerLabel.setText(String.format("%.1f秒", Math.max(0, remainingTime)));

             
            if (isPlotKill) {
                double progress = timeCounter[0] / duration;
                spawnInterval.set(Math.max(minSpawnInterval, 1.0 - progress * 0.8));
            } else {
                double progress = timeCounter[0] / duration;
                spawnInterval.set(Math.max(minSpawnInterval, 1.5 - progress * 0.7));
            }

             
            int currentCircleCount = activeCircles.size();
            circleCountLabel.setText("当前圆圈: " + currentCircleCount);

            if (currentCircleCount < maxConcurrentCircles &&
                    timeCounter[0] - lastSpawnTime[0] >= spawnInterval.get()) {
                lastSpawnTime[0] = timeCounter[0];
                spawnNewCircle(gameArea, activeKeys, activeCircles, health, healthLoss, isPlotKill, reactionTime, circleCountLabel);
            }

             
            if (remainingTime <= 0 && !challengeCompleted.get()) {
                challengeCompleted.set(true);
                stopAllTimelines();
                 
                boolean success = health.get() > 0.2;
                debugLog("时间结束，血量: " + health.get() + ", 成功: " + success);
                completeChallenge(success, panel, resultFuture);
            } else if (health.get() <= 0 && !challengeCompleted.get()) {
                challengeCompleted.set(true);
                stopAllTimelines();
                debugLog("血量耗尽，挑战失败");
                completeChallenge(false, panel, resultFuture);
            }
        });

        challengeTimeline.getKeyFrames().add(gameFrame);
        challengeTimeline.setCycleCount(Timeline.INDEFINITE);

         
        Timeline endTimeline = new Timeline(
                new KeyFrame(Duration.seconds(duration), e -> {
                    if (!challengeCompleted.get()) {
                        challengeCompleted.set(true);
                        stopAllTimelines();
                         
                        boolean success = health.get() > 0.2;
                        debugLog("结束时间线触发，血量: " + health.get() + ", 成功: " + success);
                        completeChallenge(success, panel, resultFuture);
                    }
                })
        );
        registerTimeline(endTimeline);

        challengeTimeline.play();
        endTimeline.play();

        setupCleanup(resultFuture, keyHandler, challengeTimeline, endTimeline);
        return panel;
    }

     

     
    public CompletableFuture<Boolean> startMovingCircleChallenge(int requiredHits, double circleInterval, boolean isPlotKill) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        if (isChallengeActive) {
            debugLog("已有挑战在进行中，拒绝新挑战");
            resultFuture.complete(false);
            return resultFuture;
        }

        Platform.runLater(() -> {
            if (effectContainer.getChildren().size() > 1) {
                debugLog("清理残留内容");
                effectContainer.getChildren().removeIf(node -> node != overlay);
            }
        });

        Platform.runLater(() -> {
            try {
                isChallengeActive = true;
                setupEffectEnvironment();

                showTutorialPanel("在实心圆进入空心圆时按下空格/Enter/鼠标左右键！\n\n需要成功击中 " + requiredHits + " 次。",
                        () -> startMovingCircleChallengeAfterTutorial(requiredHits, circleInterval, isPlotKill, resultFuture));
            } catch (Exception e) {
                safeCompleteFuture(resultFuture, isPlotKill, "移动圆圈挑战初始化失败: " + e.getMessage());
            }
        });

        return resultFuture;
    }

    private void startMovingCircleChallengeAfterTutorial(int requiredHits, double circleInterval, boolean isPlotKill,
                                                         CompletableFuture<Boolean> resultFuture) {
        VBox challengePanel = createMovingCircleChallengePanel(requiredHits, circleInterval, isPlotKill, resultFuture);
        effectContainer.getChildren().add(challengePanel);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), challengePanel);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private VBox createMovingCircleChallengePanel(int requiredHits, double circleInterval, boolean isPlotKill,
                                                  CompletableFuture<Boolean> resultFuture) {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));
        panel.setMaxSize(600, 300);
        panel.setStyle("-fx-background-color: transparent;");

        Label titleLabel = new Label(isPlotKill ? "精准时机！" : "把握时机！");
        titleLabel.setStyle("-fx-text-fill: " + (isPlotKill ? DANGER_COLOR : TEXT_PRIMARY) + "; " +
                "-fx-font-size: 20px; -fx-font-weight: bold;");

         
        Label progressLabel = new Label("进度: 0/" + requiredHits);
        progressLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; " +
                "-fx-font-size: 16px;");

         
        Pane gameArea = new Pane();
        gameArea.setPrefSize(400, 100);
        gameArea.setStyle("-fx-background-color: rgba(51, 65, 85, 0.2); " +
                "-fx-border-color: " + (isPlotKill ? DANGER_COLOR : ACCENT_COLOR) + "; " +
                "-fx-border-width: 2;");

        panel.getChildren().addAll(titleLabel, progressLabel, gameArea);

         
        int[] successfulHits = {0};
        int[] totalCircles = {0};
        AtomicBoolean challengeCompleted = new AtomicBoolean(false);
        Timeline challengeTimeline = new Timeline();

        registerTimeline(challengeTimeline);

         
        double centerY = 50;  
        double lineWidth = 300;  
        double lineStartX = 50;  

         
        Line line = new Line(lineStartX, centerY, lineStartX + lineWidth, centerY);
        line.setStroke(Color.web(isPlotKill ? DANGER_COLOR : ACCENT_COLOR));
        line.setStrokeWidth(2);

         
        Circle targetCircle = new Circle(lineStartX + lineWidth - 30, centerY, 15);
        targetCircle.setFill(Color.TRANSPARENT);
        targetCircle.setStroke(Color.web(isPlotKill ? DANGER_COLOR : SUCCESS_COLOR));
        targetCircle.setStrokeWidth(2);

        gameArea.getChildren().addAll(line, targetCircle);

        EventHandler<KeyEvent> keyHandler = event -> {
            if (challengeCompleted.get()) {
                debugLog("挑战已结束，忽略按键事件");
                return;
            }

            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                event.consume();
                debugLog("按键检测: " + event.getCode() + " 时间: " + System.currentTimeMillis());

                 
                Platform.runLater(() -> {
                    if (challengeCompleted.get()) {
                        debugLog("挑战在延迟期间已结束，忽略检测");
                        return;
                    }

                    checkHit(gameArea, successfulHits, requiredHits, progressLabel,
                            challengeCompleted, isPlotKill, panel, resultFuture);
                });
            }
        };

        EventHandler<MouseEvent> mouseHandler = event -> {
            if (challengeCompleted.get()) {
                debugLog("挑战已结束，忽略鼠标事件");
                return;
            }

            if (event.getButton() == MouseButton.PRIMARY || event.getButton() == MouseButton.SECONDARY) {
                event.consume();
                debugLog("鼠标点击检测: " + event.getButton() + " 时间: " + System.currentTimeMillis());

                 
                Platform.runLater(() -> {
                    if (challengeCompleted.get()) {
                        debugLog("挑战在延迟期间已结束，忽略检测");
                        return;
                    }

                    checkHit(gameArea, successfulHits, requiredHits, progressLabel,
                            challengeCompleted, isPlotKill, panel, resultFuture);
                });
            }
        };

         
        double[] timeCounter = {0};
        KeyFrame spawnFrame = new KeyFrame(Duration.millis(100), e -> {
            if (challengeCompleted.get()) return;

            timeCounter[0] += 0.1;

             
            if (timeCounter[0] >= totalCircles[0] * circleInterval) {
                spawnMovingCircle(gameArea, lineStartX, centerY, lineWidth, targetCircle,
                        successfulHits, requiredHits, progressLabel, challengeCompleted,
                        isPlotKill, panel, resultFuture);
                totalCircles[0]++;
            }
        });

        challengeTimeline.getKeyFrames().add(spawnFrame);
        challengeTimeline.setCycleCount(Timeline.INDEFINITE);

         
        challengeTimeline.play();
        registerKeyHandler(keyHandler);
        registerMouseHandler(mouseHandler);

        setupCleanup(resultFuture, keyHandler, mouseHandler, challengeTimeline);
        return panel;
    }


     
    private void removeMovingCircle(Pane gameArea, Circle movingCircle) {
        if (movingCircle == null) return;

        String circleId = movingCircle.getUserData() != null ?
                movingCircle.getUserData().toString() : "unknown";
        debugLog("移除移动圆[" + circleId + "]");

         
        gameArea.getChildren().remove(movingCircle);

         
         
    }

     
    private void spawnMovingCircle(Pane gameArea, double startX, double centerY, double lineWidth,
                                   Circle targetCircle, int[] successfulHits, int requiredHits,
                                   Label progressLabel, AtomicBoolean challengeCompleted,
                                   boolean isPlotKill, VBox panel, CompletableFuture<Boolean> resultFuture) {

         
        Circle movingCircle = new Circle(startX, centerY, 8);
        movingCircle.setFill(Color.web(isPlotKill ? DANGER_COLOR : PRIMARY_COLOR));
        movingCircle.setStroke(Color.web(SECONDARY_COLOR));
        movingCircle.setStrokeWidth(1);

         
        String circleId = "circle_" + System.currentTimeMillis() + "_" + Math.random();
        movingCircle.setUserData(circleId);

        gameArea.getChildren().add(movingCircle);

        debugLog("生成新移动圆[" + circleId + "]: 起始位置=" + startX + ", 目标位置=" + (startX + lineWidth));

         
        double animationDuration = 2.0;
        double targetX = startX + lineWidth;

        Timeline moveTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(movingCircle.centerXProperty(), startX)
                ),
                new KeyFrame(Duration.seconds(animationDuration),
                        new KeyValue(movingCircle.centerXProperty(), targetX)
                )
        );

         
        Timeline hitCheckTimeline = new Timeline();
        hitCheckTimeline.setCycleCount(Timeline.INDEFINITE);

        KeyFrame hitCheckFrame = new KeyFrame(Duration.millis(16), e -> {
            if (challengeCompleted.get()) {
                moveTimeline.stop();
                hitCheckTimeline.stop();
                return;
            }

            double circleX = movingCircle.getCenterX();
            double targetCenterX = targetCircle.getCenterX();
            double targetRadius = targetCircle.getRadius();

             
            boolean inHitZone = Math.abs(circleX - targetCenterX) <= targetRadius + 8;

            if (inHitZone) {
                movingCircle.setFill(Color.web(WARNING_COLOR));
            } else {
                movingCircle.setFill(Color.web(isPlotKill ? DANGER_COLOR : PRIMARY_COLOR));
            }
        });

        hitCheckTimeline.getKeyFrames().add(hitCheckFrame);

         
        moveTimeline.setOnFinished(e -> {
             
            if (challengeCompleted.get()) {
                debugLog("移动圆[" + circleId + "]完成：挑战已结束，忽略");
                return;
            }

             
            if (!gameArea.getChildren().contains(movingCircle)) {
                debugLog("移动圆[" + circleId + "]完成：圆圈已被移除，忽略");
                return;
            }

            double finalX = movingCircle.getCenterX();
            double targetCenterX = targetCircle.getCenterX();
            double targetRadius = targetCircle.getRadius();
            double distance = Math.abs(finalX - targetCenterX);

            debugLog("移动圆[" + circleId + "]完成：最终位置=" + finalX +
                    ", 目标中心=" + targetCenterX + ", 距离=" + distance +
                    ", 允许距离=" + (targetRadius + 8));

            if (distance > targetRadius + 8) {
                 
                if (!challengeCompleted.get()) {
                    challengeCompleted.set(true);
                    debugLog("移动圆[" + circleId + "]未命中，挑战失败");
                    stopAllTimelines();
                    completeChallenge(false, panel, resultFuture);
                }
            } else {
                debugLog("移动圆[" + circleId + "]在目标区域内完成，可能是命中后未及时移除");
            }

             
            gameArea.getChildren().remove(movingCircle);
            hitCheckTimeline.stop();
        });

        moveTimeline.play();
        hitCheckTimeline.play();

        registerTimeline(moveTimeline);
        registerTimeline(hitCheckTimeline);
    }

     
    private void checkHit(Pane gameArea, int[] successfulHits, int requiredHits,
                          Label progressLabel, AtomicBoolean challengeCompleted,
                          boolean isPlotKill, VBox panel, CompletableFuture<Boolean> resultFuture) {

        boolean hitDetected = false;

        debugLog("开始命中检测，当前圆圈数量: " + gameArea.getChildren().size());

         
        Circle targetCircle = null;
        List<Circle> movingCircles = new ArrayList<>();

        for (Node node : gameArea.getChildren()) {
            if (node instanceof Circle circle) {
                if (circle.getFill() == Color.TRANSPARENT) {
                    targetCircle = circle;
                } else {
                    movingCircles.add(circle);
                }
            }
        }

        if (targetCircle == null || movingCircles.isEmpty()) {
            debugLog("未找到有效圆圈进行检测");
            if (!challengeCompleted.get()) {
                challengeCompleted.set(true);
                debugLog("技术错误：未找到有效圆圈");
                stopAllTimelines();
                completeChallenge(false, panel, resultFuture);
            }
            return;
        }

        double targetX = targetCircle.getCenterX();
        double targetRadius = targetCircle.getRadius();

        debugLog("目标圆位置: X=" + targetX + ", 半径=" + targetRadius);
        debugLog("检测 " + movingCircles.size() + " 个移动圆");

         
        movingCircles.sort((c1, c2) -> {
            double dist1 = Math.abs(c1.getCenterX() - targetX);
            double dist2 = Math.abs(c2.getCenterX() - targetX);
            return Double.compare(dist1, dist2);
        });

        debugLog("排序后的移动圆距离: " +
                movingCircles.stream()
                        .mapToDouble(c -> Math.abs(c.getCenterX() - targetX))
                        .boxed()
                        .toList());

         
        Circle closestCircle = movingCircles.get(0);
        double circleX = closestCircle.getCenterX();
        double distance = Math.abs(circleX - targetX);

        debugLog("检测最近移动圆: 位置=" + circleX + ", 距离目标=" + distance +
                ", 允许距离=" + (targetRadius + 8));

         
        if (distance <= targetRadius + 8) {
            hitDetected = true;

             
            successfulHits[0]++;
            progressLabel.setText("进度: " + successfulHits[0] + "/" + requiredHits);

             
            playHitEffect(gameArea, circleX, closestCircle.getCenterY());

             
            removeMovingCircle(gameArea, closestCircle);

            debugLog("命中成功！当前进度: " + successfulHits[0] + "/" + requiredHits +
                    ", 距离: " + distance);

             
            if (successfulHits[0] >= requiredHits && !challengeCompleted.get()) {
                challengeCompleted.set(true);
                debugLog("挑战完成！成功命中 " + successfulHits[0] + " 次");
                stopAllTimelines();
                completeChallenge(true, panel, resultFuture);
            }
        } else {
            debugLog("最近圆圈不在命中范围内，距离=" + distance);

             
            for (int i = 1; i < movingCircles.size(); i++) {
                Circle otherCircle = movingCircles.get(i);
                double otherDistance = Math.abs(otherCircle.getCenterX() - targetX);
                debugLog("检查其他圆圈[" + i + "]: 位置=" + otherCircle.getCenterX() +
                        ", 距离=" + otherDistance);

                if (otherDistance <= targetRadius + 8) {
                    debugLog("发现其他圆圈在命中范围内，但优先处理最近圆圈");
                    break;
                }
            }
        }

        if (!hitDetected) {
            debugLog("未命中任何有效圆圈！");
             
            if (!challengeCompleted.get()) {
                challengeCompleted.set(true);
                debugLog("未命中任何圆圈，挑战失败");
                stopAllTimelines();
                completeChallenge(false, panel, resultFuture);
            }
        }
    }

     
    private void playHitEffect(Pane gameArea, double x, double y) {
        Circle effect = new Circle(x, y, 15);
        effect.setFill(Color.TRANSPARENT);
        effect.setStroke(Color.web(SUCCESS_COLOR));
        effect.setStrokeWidth(2);

         
        effect.setUserData("hitEffect_" + System.currentTimeMillis());

        gameArea.getChildren().add(effect);

        debugLog("播放命中效果于位置: (" + x + ", " + y + ")");

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(effect.radiusProperty(), 15),
                        new KeyValue(effect.opacityProperty(), 1)
                ),
                new KeyFrame(Duration.seconds(0.3),
                        new KeyValue(effect.radiusProperty(), 40),
                        new KeyValue(effect.opacityProperty(), 0)
                )
        );
        timeline.setOnFinished(e -> {
            gameArea.getChildren().remove(effect);
            debugLog("命中效果移除完成");
        });
        timeline.play();
    }

     

    private void showTutorialPanel(String instruction, Runnable onStartChallenge) {
        VBox tutorialPanel = new VBox(20);
        tutorialPanel.setAlignment(Pos.CENTER);
        tutorialPanel.setPadding(new Insets(30));
        tutorialPanel.setMaxSize(400, 300);
        tutorialPanel.setStyle("-fx-background-color: rgba(30, 41, 59, 0.9); " +
                "-fx-background-radius: 15; -fx-border-color: " + ACCENT_COLOR + "; " +
                "-fx-border-width: 2; -fx-border-radius: 15;");

        Label instructionLabel = new Label(instruction);
        instructionLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-font-size: 16px; -fx-text-alignment: center;");
        instructionLabel.setWrapText(true);

        Label startLabel = new Label("按任意键开始挑战...");
        startLabel.setStyle("-fx-text-fill: " + WARNING_COLOR + "; -fx-font-size: 14px;");

        tutorialPanel.getChildren().addAll(instructionLabel, startLabel);
        effectContainer.getChildren().add(tutorialPanel);

         
        AtomicReference<EventHandler<KeyEvent>> startHandlerRef = new AtomicReference<>();

        EventHandler<KeyEvent> startHandler = event -> {
            effectContainer.getChildren().remove(tutorialPanel);
            EventHandler<KeyEvent> handler = startHandlerRef.get();
            if (handler != null) {
                unregisterKeyHandler(handler);
            }
            onStartChallenge.run();
        };

        startHandlerRef.set(startHandler);
        registerKeyHandler(startHandler);
    }

    private void setupEffectEnvironment() {
        effectContainer.setVisible(true);
        effectContainer.setMouseTransparent(false);
        effectContainer.toFront();
        overlay.setVisible(true);
    }

     
    private EventHandler<KeyEvent> createCircleKeyHandler(Set<Character> activeKeys, List<CircleNode> activeCircles,
                                                          DoubleProperty health, double healthGain, Pane gameArea,
                                                          Label circleCountLabel) {
        return event -> {
            char pressedChar = event.getCode().toString().charAt(0);
            if (activeKeys.contains(pressedChar)) {
                CircleNode circleToRemove = null;
                synchronized (activeCircles) {
                    for (CircleNode circle : activeCircles) {
                        if (circle.keyChar == pressedChar) {
                            circleToRemove = circle;
                            break;
                        }
                    }
                }

                if (circleToRemove != null) {
                    CircleNode finalCircle = circleToRemove;
                    Platform.runLater(() -> {
                         
                        if (finalCircle.shrinkTimeline != null) {
                            finalCircle.shrinkTimeline.stop();
                        }

                        double circleX = finalCircle.node.getLayoutX();
                        double circleY = finalCircle.node.getLayoutY();

                        gameArea.getChildren().remove(finalCircle.node);
                        activeCircles.remove(finalCircle);
                        activeKeys.remove(pressedChar);

                        double newHealth = health.get() + healthGain;
                        health.set(Math.min(1.0, newHealth));

                        playCircleSuccessEffect(gameArea, circleX, circleY);

                         
                        if (circleCountLabel != null) {
                            circleCountLabel.setText("当前圆圈: " + activeCircles.size());
                        }
                    });
                }
            }
        };
    }

     
    private void spawnNewCircle(Pane gameArea, Set<Character> activeKeys, List<CircleNode> activeCircles,
                                DoubleProperty health, double healthLoss, boolean isPlotKill,
                                double reactionTime, Label circleCountLabel) {
        char newKey;
        int attempts = 0;
        int maxAttempts = 50;

         
        do {
            newKey = (char) ('A' + ThreadLocalRandom.current().nextInt(26));
            attempts++;
            if (attempts >= maxAttempts) {
                debugLog("无法生成不重复的按键，跳过生成");
                return;
            }
        } while (activeKeys.contains(newKey));

        activeKeys.add(newKey);

         
        double x, y;
        boolean positionValid;
        int positionAttempts = 0;
        int maxPositionAttempts = 30;

        do {
            x = 40 + ThreadLocalRandom.current().nextInt(320);
            y = 40 + ThreadLocalRandom.current().nextInt(170);
            positionValid = true;
            positionAttempts++;

             
            for (CircleNode existingCircle : activeCircles) {
                double existingX = existingCircle.node.getLayoutX();
                double existingY = existingCircle.node.getLayoutY();
                double distance = Math.sqrt(Math.pow(x - existingX, 2) + Math.pow(y - existingY, 2));

                if (distance < 60) {
                    positionValid = false;
                    break;
                }
            }

            if (positionAttempts >= maxPositionAttempts) {
                break;
            }
        } while (!positionValid);

         
        StackPane circleContainer = new StackPane();
        circleContainer.setLayoutX(x);
        circleContainer.setLayoutY(y);

         
        Circle outerCircle = new Circle(0, 0, 25);
        outerCircle.setFill(Color.TRANSPARENT);
        outerCircle.setStroke(Color.web(isPlotKill ? DANGER_COLOR : WARNING_COLOR));
        outerCircle.setStrokeWidth(2);

        Circle innerCircle = new Circle(0, 0, 12);
        innerCircle.setFill(Color.web(isPlotKill ? DANGER_COLOR : PRIMARY_COLOR));
        innerCircle.setStroke(Color.web(SECONDARY_COLOR));
        innerCircle.setStrokeWidth(1);

        Label keyLabel = new Label(String.valueOf(newKey));
        keyLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-font-size: 12px; -fx-font-weight: bold;");

        circleContainer.getChildren().addAll(outerCircle, innerCircle, keyLabel);

         
        ScaleTransition scaleTransition = new ScaleTransition(Duration.seconds(reactionTime), outerCircle);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(12.0 / 25.0);
        scaleTransition.setToY(12.0 / 25.0);

         
        Timeline shrinkTimeline = new Timeline();
        char finalNewKey = newKey;
        double finalX = x;
        double finalY = y;
        KeyFrame shrinkFrame = new KeyFrame(Duration.seconds(reactionTime), e -> {
            if (circleContainer.getParent() != null && !shrinkTimeline.getStatus().equals(Animation.Status.STOPPED)) {
                Platform.runLater(() -> {
                    gameArea.getChildren().remove(circleContainer);
                    activeKeys.remove(finalNewKey);
                    activeCircles.removeIf(circle -> circle.keyChar == finalNewKey);

                    double newHealth = health.get() - healthLoss;
                    health.set(Math.max(0, newHealth));

                    playCircleFailEffect(gameArea, finalX, finalY);

                     
                    if (circleCountLabel != null) {
                        circleCountLabel.setText("当前圆圈: " + activeCircles.size());
                    }
                });
            }
        });
        shrinkTimeline.getKeyFrames().add(shrinkFrame);

        CircleNode circleData = new CircleNode(newKey, circleContainer, outerCircle, innerCircle, keyLabel, shrinkTimeline);
        activeCircles.add(circleData);

        Platform.runLater(() -> {
            gameArea.getChildren().add(circleContainer);
            scaleTransition.play();
            shrinkTimeline.play();

             
            if (circleCountLabel != null) {
                circleCountLabel.setText("当前圆圈: " + activeCircles.size());
            }
        });
    }

     
    private void playCircleSuccessEffect(Pane gameArea, double x, double y) {
        Circle effect = new Circle(x, y, 15);
        effect.setFill(Color.TRANSPARENT);
        effect.setStroke(Color.web(SUCCESS_COLOR));
        effect.setStrokeWidth(2);

        gameArea.getChildren().add(effect);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(effect.radiusProperty(), 15),
                        new KeyValue(effect.opacityProperty(), 1)
                ),
                new KeyFrame(Duration.seconds(0.3),
                        new KeyValue(effect.radiusProperty(), 40),
                        new KeyValue(effect.opacityProperty(), 0)
                )
        );
        timeline.setOnFinished(e -> gameArea.getChildren().remove(effect));
        timeline.play();
    }

     
    private void playCircleFailEffect(Pane gameArea, double x, double y) {
        Circle effect = new Circle(x, y, 15);
        effect.setFill(Color.TRANSPARENT);
        effect.setStroke(Color.web(DANGER_COLOR));
        effect.setStrokeWidth(2);

        gameArea.getChildren().add(effect);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(effect.radiusProperty(), 15),
                        new KeyValue(effect.opacityProperty(), 1)
                ),
                new KeyFrame(Duration.seconds(0.3),
                        new KeyValue(effect.radiusProperty(), 40),
                        new KeyValue(effect.opacityProperty(), 0)
                )
        );
        timeline.setOnFinished(e -> gameArea.getChildren().remove(effect));
        timeline.play();
    }

     
    private void completeChallenge(boolean success, Node challengePanel, CompletableFuture<Boolean> resultFuture) {
        if (resultFuture.isDone()) {
            debugLog("Future 已完成，跳过");
            return;
        }

        debugLog("开始完成挑战流程，结果: " + success);

         
        isChallengeActive = false;

        Platform.runLater(() -> {
            try {
                 
                stopAllTimelines();
                clearAllKeyHandlers();
                clearAllMouseHandlers();

                 
                Label resultLabel = new Label(success ? "成功！" : "失败！");
                resultLabel.setStyle("-fx-text-fill: " + (success ? SUCCESS_COLOR : DANGER_COLOR) + "; " +
                        "-fx-font-size: 24px; -fx-font-weight: bold;");

                StackPane resultPane = new StackPane(resultLabel);
                resultPane.setStyle("-fx-background-color: rgba(30, 41, 59, 0.9); " +
                        "-fx-background-radius: 10; -fx-padding: 20;");
                resultPane.setOpacity(0);

                effectContainer.getChildren().add(resultPane);

                 
                FadeTransition fadeOut = new FadeTransition(Duration.millis(400), challengePanel);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);

                FadeTransition resultFadeIn = new FadeTransition(Duration.millis(300), resultPane);
                resultFadeIn.setFromValue(0);
                resultFadeIn.setToValue(1);

                SequentialTransition sequence = new SequentialTransition(
                        new ParallelTransition(fadeOut, resultFadeIn),
                        new PauseTransition(Duration.millis(800))
                );

                sequence.setOnFinished(event -> {
                    debugLog("动画完成，开始清理");

                    Platform.runLater(() -> {
                        try {
                             
                            effectContainer.getChildren().remove(challengePanel);
                            effectContainer.getChildren().remove(resultPane);

                             
                            boolean hasOtherContent = effectContainer.getChildren().stream()
                                    .anyMatch(node -> node != overlay && node.isVisible());

                            if (!hasOtherContent) {
                                effectContainer.setVisible(false);
                                overlay.setVisible(false);
                                debugLog("容器已隐藏");
                            }

                             
                            if (!resultFuture.isDone()) {
                                resultFuture.complete(success);
                                debugLog("挑战流程完成");
                            }
                        } catch (Exception e) {
                            debugLog("清理过程中出错: " + e.getMessage());
                            if (!resultFuture.isDone()) {
                                resultFuture.complete(success);
                            }
                        }
                    });
                });

                sequence.play();

            } catch (Exception e) {
                debugLog("完成挑战过程中出错: " + e.getMessage());
                safeCompleteFuture(resultFuture, false, "挑战完成过程出错: " + e.getMessage());
            }
        });
    }

     

    private void setupCleanup(CompletableFuture<Boolean> resultFuture, EventHandler<KeyEvent> keyHandler, Timeline... timelines) {
        resultFuture.whenComplete((result, error) -> {
            Platform.runLater(() -> {
                if (keyHandler != null) {
                    unregisterKeyHandler(keyHandler);
                }
                for (Timeline timeline : timelines) {
                    if (timeline != null) {
                        stopTimeline(timeline);
                        unregisterTimeline(timeline);
                    }
                }
            });
        });
    }

    private void setupCleanup(CompletableFuture<Boolean> resultFuture, EventHandler<KeyEvent> keyHandler,
                              EventHandler<MouseEvent> mouseHandler, Timeline... timelines) {
        resultFuture.whenComplete((result, error) -> {
            Platform.runLater(() -> {
                if (keyHandler != null) {
                    unregisterKeyHandler(keyHandler);
                }
                if (mouseHandler != null) {
                    unregisterMouseHandler(mouseHandler);
                }
                for (Timeline timeline : timelines) {
                    if (timeline != null) {
                        stopTimeline(timeline);
                        unregisterTimeline(timeline);
                    }
                }
            });
        });
    }

    private void stopAllTimelines() {
        List<Timeline> timelinesToStop = new ArrayList<>(activeTimelines);
        activeTimelines.clear();

        debugLog("停止 " + timelinesToStop.size() + " 个时间线");

        for (Timeline timeline : timelinesToStop) {
            if (timeline != null) {
                try {
                    timeline.stop();
                    timeline.getKeyFrames().clear();
                } catch (Exception e) {
                    debugLog("停止时间线失败: " + e.getMessage());
                }
            }
        }
    }

    private void clearAllKeyHandlers() {
        Scene scene = getCurrentScene();
        if (scene != null) {
            for (EventHandler<KeyEvent> handler : activeKeyHandlers) {
                scene.removeEventHandler(KeyEvent.KEY_PRESSED, handler);
            }
        }
        activeKeyHandlers.clear();
        debugLog("清理所有按键处理器");
    }

    private void clearAllMouseHandlers() {
        Scene scene = getCurrentScene();
        if (scene != null) {
            for (EventHandler<MouseEvent> handler : activeMouseHandlers) {
                scene.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler);
            }
        }
        activeMouseHandlers.clear();
        debugLog("清理所有鼠标处理器");
    }

    private void registerKeyHandler(EventHandler<KeyEvent> handler) {
        Scene scene = getCurrentScene();
        if (scene != null) {
            scene.addEventHandler(KeyEvent.KEY_PRESSED, handler);
            activeKeyHandlers.add(handler);
        }
    }

    private void unregisterKeyHandler(EventHandler<KeyEvent> handler) {
        Scene scene = getCurrentScene();
        if (scene != null) {
            scene.removeEventHandler(KeyEvent.KEY_PRESSED, handler);
        }
        activeKeyHandlers.remove(handler);
    }

    private void registerMouseHandler(EventHandler<MouseEvent> handler) {
        Scene scene = getCurrentScene();
        if (scene != null) {
            scene.addEventHandler(MouseEvent.MOUSE_CLICKED, handler);
            activeMouseHandlers.add(handler);
        }
    }

    private void unregisterMouseHandler(EventHandler<MouseEvent> handler) {
        Scene scene = getCurrentScene();
        if (scene != null) {
            scene.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler);
        }
        activeMouseHandlers.remove(handler);
    }

    private void registerTimeline(Timeline timeline) {
        activeTimelines.add(timeline);
    }

    private void unregisterTimeline(Timeline timeline) {
        activeTimelines.remove(timeline);
    }

    private void stopTimeline(Timeline timeline) {
        if (timeline != null) {
            timeline.stop();
        }
    }

    private void safeCompleteFuture(CompletableFuture<Boolean> future, boolean result, String errorMessage) {
        if (!future.isDone()) {
            if (errorMessage != null) {
                debugLog(errorMessage);
            }
            future.complete(result);
            isChallengeActive = false;
            Platform.runLater(() -> {
                effectContainer.getChildren().clear();
                effectContainer.getChildren().add(overlay);
                effectContainer.setVisible(false);
                overlay.setVisible(false);
            });
        }
    }

    public void stopAllChallenges() {
        stopAllTimelines();
        clearAllKeyHandlers();
        clearAllMouseHandlers();
        isChallengeActive = false;

        Platform.runLater(() -> {
            effectContainer.getChildren().clear();
            effectContainer.getChildren().add(overlay);
            effectContainer.setVisible(false);
            overlay.setVisible(false);
        });

        debugLog("强制停止所有挑战");
    }

    private void debugLog(String message) {
        System.out.println("[InteractiveEffectManager][" + System.currentTimeMillis() + "] " + message);
    }

     

    private static class CircleNode {
        char keyChar;
        StackPane node;
        Circle outerCircle;
        Circle innerCircle;
        Label keyLabel;
        Timeline shrinkTimeline;

        CircleNode(char keyChar, StackPane node, Circle outerCircle, Circle innerCircle, Label keyLabel, Timeline shrinkTimeline) {
            this.keyChar = keyChar;
            this.node = node;
            this.outerCircle = outerCircle;
            this.innerCircle = innerCircle;
            this.keyLabel = keyLabel;
            this.shrinkTimeline = shrinkTimeline;
        }
    }
}
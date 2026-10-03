package com.xiaowu.game.starveil.game.world;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.game.world.MapData;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xiaowu.game.starveil.config.GameConstants;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class MapManager {

    private static MapManager instance;
    public static MapManager getInstance() {
        if (instance == null) instance = new MapManager();
        return instance;
    }

    // 数据存储
    private Map<String, MapData.MapInfo> fullMap = new HashMap<>();
    private StackPane uiRoot;
    private CompletableFuture<Integer> future;
    private Map<String, Image> thumbnailCache = new ConcurrentHashMap<>(); // 线程安全的缩略图缓存
    private Map<String, Image> fullImageCache = new ConcurrentHashMap<>(); // 完整图片缓存
    private Map<String, double[]> imageDimensions = new ConcurrentHashMap<>(); // 图片尺寸缓存
    private String currentMapId;

    // 样式常量 - 使用粉色系
    // 注意这几个是【实例】字段而非 static：主题色由内容在 content.init.init 里设定，
    // 而 static 字段在类加载时就求值 —— 启动顺序是「INIT 插件 → content init」，
    // 若某个 INIT 插件在 onLoad 里碰到本类，static 会把默认色永久冻住。
    private final String PRIMARY_COLOR = ContentConfig.primaryColor();
    private final String SECONDARY_COLOR = ContentConfig.secondaryColor();
    private static final String ACCENT_COLOR = "#FFB6C1";
    private static final String DARK_BG = "#2d1f2f";
    private static final String CARD_BG = "#3d2d3d";
    private static final String TEXT_PRIMARY = "#FFE4E1";
    private final String TEXT_SECONDARY = ContentConfig.tertiaryColor();
    private static final String TEXT_MUTED = "#FFB6C1";
    private final String BORDER_COLOR = ContentConfig.primaryColor();

    private MapManager() {
        loadMapJson();
        preloadImageDimensions();
    }

    private void loadMapJson() {
        try (InputStream in = ResourceResolver.getResourceAsStream("starveil:data/worlds/world-index.json");
             Reader reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
             Gson gson = new Gson();
             Type type = new TypeToken<Map<String, MapData.MapInfo>>() {}.getType();
             Map<String, MapData.MapInfo> raw = gson.fromJson(reader, type);
             fullMap.putAll(raw);
             Logger("INFO", "加载地图配置完成，共 " + raw.size() + " 个地图");
        } catch (Exception ex) {
            Logger("ERROR", "无法加载 starveil:data/worlds/world-index.json: " + ex.getMessage());
            throw new RuntimeException("无法加载地图配置文件", ex);
        }
    }

    /**
     * 预加载所有图片的尺寸信息
     */
    private void preloadImageDimensions() {
        Logger("INFO", "开始预加载图片尺寸...");
        int loadedCount = 0;
        int failedCount = 0;

        for (Map.Entry<String, MapData.MapInfo> entry : fullMap.entrySet()) {
            MapData.MapInfo mapInfo = entry.getValue();
            String imagePath = normalizeImagePath(mapInfo.map);

            if (imagePath == null) {
                Logger("WARN", "地图 '" + mapInfo.id + "' 图片路径为空");
                failedCount++;
                continue;
            }

            try (InputStream stream = ResourceResolver.getResourceAsStream(imagePath)) {
                if (stream == null) {
                    Logger("WARN", "无法加载图片获取尺寸: " + imagePath);
                    failedCount++;
                    continue;
                }

                // 只读取图片头部信息获取尺寸
                Image tempImage = new Image(new java.io.ByteArrayInputStream(stream.readAllBytes()), 0, 0, true, false);

                if (tempImage.isError()) {
                    Logger("WARN", "图片加载错误: " + imagePath);
                    failedCount++;
                    continue;
                }

                double width = tempImage.getWidth();
                double height = tempImage.getHeight();

                if (width <= 0 || height <= 0) {
                    Logger("WARN", "获取图片尺寸失败: " + imagePath);
                    failedCount++;
                } else {
                    imageDimensions.put(mapInfo.id, new double[]{width, height});
                    Logger("INFO", String.format("地图 '%s' 尺寸: %.0fx%.0f", mapInfo.id, width, height));
                    loadedCount++;
                }
            } catch (Exception e) {
                Logger("ERROR", "预加载图片尺寸异常: " + e.getMessage());
                failedCount++;
            }
        }

        Logger("INFO", String.format("图片尺寸预加载完成: %d 成功, %d 失败", loadedCount, failedCount));
    }

    /**
     * 获取图片尺寸信息
     */
    public Map<String, double[]> getImageDimensions() {
        return new HashMap<>(imageDimensions);
    }

    /**
     * 标准化图片路径（支持 starveil: 命名空间引用与旧式 /assets/ 路径）
     */
    private String normalizeImagePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        if (com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver.isNamespaceReference(path)) {
            return com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver.resolve(path);
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    // API方法
    public CompletableFuture<Integer> showMapSelector() {
        return showMapSelector(null, null);
    }

    public CompletableFuture<Integer> showMapSelector(List<String> allowedMaps,
                                                      List<Integer> allowedButtons) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                this.future = future;
                buildAndShow(allowedMaps, allowedButtons);
            } catch (Exception e) {
                Logger("ERROR", "构建地图选择器失败: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void buildAndShow(List<String> allowedMaps, List<Integer> allowedButtons) {
        GameManager gm = GameManager.getInstance();
        Scene scene = gm.getPrimaryStage().getScene();

        // 过滤显示的地图
        Map<String, MapData.MapInfo> showMap = new LinkedHashMap<>();
        fullMap.forEach((k, v) -> {
            if (allowedMaps == null || allowedMaps.contains(k)) {
                MapData.MapInfo copy = new MapData.MapInfo();
                copy.id = v.id;
                copy.map = v.map;
                copy.button = new ArrayList<>();
                v.button.forEach(b -> {
                    if (allowedButtons == null || allowedButtons.contains(b.id)) {
                        copy.button.add(b);
                    }
                });
                if (!copy.button.isEmpty()) showMap.put(k, copy);
            }
        });

        if (showMap.isEmpty()) {
            throw new IllegalArgumentException("没有可显示的地图或按钮");
        }

        Logger("INFO", "显示 " + showMap.size() + " 个地图");

        // 主容器
        HBox root = new HBox();
        root.setPrefSize(scene.getWidth(), scene.getHeight());

        // 左侧栏
        VBox left = new VBox();
        left.setPrefWidth(280);
        left.setStyle("-fx-background-color: " + DARK_BG + ";");

        // 标题
        Label title = new Label("选择地图");
        title.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                "-fx-font-size: 22px; " +
                "-fx-font-weight: bold; " +
                "-fx-padding: 20 20 15 20;");
        left.getChildren().add(title);

        // 地图列表
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background-color: transparent; " +
                "-fx-border-color: transparent; " +
                "-fx-padding: 0;");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox mapList = new VBox(8);
        mapList.setStyle("-fx-background-color: transparent;");
        mapList.setPadding(new Insets(0, 15, 15, 15));
        scrollPane.setContent(mapList);

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        left.getChildren().add(scrollPane);

        // 右侧地图显示区
        StackPane right = new StackPane();
        right.setStyle("-fx-background-color: #0f172a;");
        HBox.setHgrow(right, Priority.ALWAYS);

        StackPane imageContainer = new StackPane();
        imageContainer.setStyle("-fx-background-color: #0f172a;");
        right.getChildren().add(imageContainer);

        // 添加地图项
        for (Map.Entry<String, MapData.MapInfo> entry : showMap.entrySet()) {
            VBox mapItem = createMapListItem(entry.getKey(), entry.getValue(), () -> {
                currentMapId = entry.getValue().id;
                switchMap(entry.getValue(), imageContainer);
            });
            mapList.getChildren().add(mapItem);
        }

        // 默认显示第一个地图
        MapData.MapInfo first = showMap.values().iterator().next();
        currentMapId = first.id;
        switchMap(first, imageContainer);

        root.getChildren().addAll(left, right);
        uiRoot = new StackPane(root);
        gm.getContentWithOverlays().getChildren().add(uiRoot);
    }

    private VBox createMapListItem(String mapName, MapData.MapInfo mapInfo, Runnable onClick) {
        VBox container = new VBox(6);
        container.setAlignment(Pos.TOP_CENTER);
        container.setPadding(new Insets(10));
        container.setStyle("-fx-background-color: " +
                (mapInfo.id.equals(currentMapId) ? "rgba(99,102,241,0.15)" : "rgba(255,255,255,0.03)") + ";" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: " +
                (mapInfo.id.equals(currentMapId) ? PRIMARY_COLOR : "transparent") + ";" +
                "-fx-border-width: 2;" +
                "-fx-border-radius: 10;" +
                "-fx-cursor: hand;");
        container.setOnMouseClicked(e -> onClick.run());

        // 缩略图容器
        StackPane thumbnailContainer = new StackPane();
        thumbnailContainer.setPrefSize(240, 135); // 16:9 比例
        thumbnailContainer.setStyle("-fx-background-color: " + CARD_BG + ";" +
                "-fx-background-radius: 8;");

        // 加载缩略图
        ImageView thumbnail = loadThumbnail(mapInfo.map, 240, 135);
        if (thumbnail != null) {
            // 圆角裁剪
            Rectangle clip = new Rectangle(240, 135);
            clip.setArcWidth(8);
            clip.setArcHeight(8);
            thumbnail.setClip(clip);

            StackPane.setAlignment(thumbnail, Pos.CENTER);
            thumbnailContainer.getChildren().add(thumbnail);

            // 添加悬停覆盖层
            Pane hoverOverlay = new Pane();
            hoverOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.3);" +
                    "-fx-background-radius: 8;");
            hoverOverlay.setVisible(false);
            thumbnailContainer.getChildren().add(hoverOverlay);

            // 悬停效果
            thumbnailContainer.setOnMouseEntered(e -> {
                hoverOverlay.setVisible(true);
                thumbnailContainer.setStyle("-fx-background-color: #475569;" +
                        "-fx-background-radius: 8;");
            });
            thumbnailContainer.setOnMouseExited(e -> {
                hoverOverlay.setVisible(false);
                thumbnailContainer.setStyle("-fx-background-color: " + CARD_BG + ";" +
                        "-fx-background-radius: 8;");
            });
        } else {
            // 缩略图加载失败显示占位符
            Label placeholder = new Label("无预览");
            placeholder.setStyle("-fx-text-fill: " + TEXT_MUTED + ";" +
                    "-fx-font-size: 13px;");
            thumbnailContainer.getChildren().add(placeholder);
        }

        // 地图名称
        Label nameLabel = new Label(mapName);
        nameLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";" +
                "-fx-font-size: 15px;" +
                "-fx-font-weight: 600;");
        nameLabel.setMaxWidth(220);
        nameLabel.setWrapText(true);
        nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nameLabel.setOnMouseClicked(e -> onClick.run());

        // 按钮数量提示
        if (mapInfo.button != null && !mapInfo.button.isEmpty()) {
            Label buttonCount = new Label(mapInfo.button.size() + " 个可探索区域");
            buttonCount.setStyle("-fx-text-fill: " + TEXT_MUTED + ";" +
                    "-fx-font-size: 12px;" +
                    "-fx-font-style: italic;");
            container.getChildren().addAll(thumbnailContainer, nameLabel, buttonCount);
        } else {
            container.getChildren().addAll(thumbnailContainer, nameLabel);
        }

        return container;
    }

    private ImageView loadThumbnail(String imagePath, double targetWidth, double targetHeight) {
        try {
            String normalizedPath = normalizeImagePath(imagePath);
            if (normalizedPath == null) {
                return null;
            }

            // 从缓存获取
            String cacheKey = normalizedPath + "_thumb_" + targetWidth + "x" + targetHeight;
            Image thumbnail = thumbnailCache.get(cacheKey);

            if (thumbnail == null) {
                // 加载新缩略图
                try (InputStream stream = ResourceResolver.getResourceAsStream(normalizedPath)) {
                    if (stream == null) {
                        Logger("WARN", "无法加载缩略图: " + normalizedPath);
                        return null;
                    }

                    thumbnail = new Image(new java.io.ByteArrayInputStream(stream.readAllBytes()), targetWidth, targetHeight, true, true);
                }

                if (!thumbnail.isError()) {
                    thumbnailCache.put(cacheKey, thumbnail);
                } else {
                    Logger("WARN", "缩略图加载错误: " + normalizedPath);
                    return null;
                }
            }

            return new ImageView(thumbnail);
        } catch (Exception e) {
            Logger("ERROR", "加载缩略图异常: " + e.getMessage());
            return null;
        }
    }

    private void switchMap(MapData.MapInfo info, StackPane host) {
        host.getChildren().clear();

        Logger("INFO", "切换地图: " + info.id);

        String imagePath = info.map;
        String normalizedPath = normalizeImagePath(imagePath);
        if (normalizedPath == null) {
            Logger("ERROR", "地图图片路径为空: " + info.id);
            showErrorMap(host, "地图图片路径为空");
            return;
        }

        try {
            // 加载完整图片
            Image fullImage = loadFullImage(normalizedPath);
            if (fullImage == null) {
                Logger("ERROR", "无法加载地图图片: " + normalizedPath);
                showErrorMap(host, "无法加载地图");
                return;
            }

            // 获取图片原始尺寸
            double imageWidth = fullImage.getWidth();
            double imageHeight = fullImage.getHeight();

            Logger("INFO", String.format("地图 '%s' 尺寸: %.0fx%.0f", info.id, imageWidth, imageHeight));

            // 创建图片容器
            Pane imageContainer = new Pane();
            imageContainer.setPrefSize(imageWidth, imageHeight);

            // 创建图片
            ImageView imageView = new ImageView(fullImage);
            imageView.setFitWidth(imageWidth);
            imageView.setFitHeight(imageHeight);
            imageContainer.getChildren().add(imageView);

            // 添加按钮
            if (info.button != null) {
                for (MapData.MapInfo.Button btnData : info.button) {
                    Button button = createMapButton(btnData);

                    double buttonWidth = 120;
                    double buttonHeight = 40;

                    // 按钮中心点定位
                    double buttonX = btnData.x - buttonWidth / 2;
                    double buttonY = btnData.y - buttonHeight / 2;

                    button.setPrefSize(buttonWidth, buttonHeight);
                    button.setLayoutX(buttonX);
                    button.setLayoutY(buttonY);

                    imageContainer.getChildren().add(button);

                    Logger("INFO", String.format("按钮 '%s': 原始坐标(%.0f,%.0f) -> 显示坐标(%.0f,%.0f)",
                            btnData.title, btnData.x, btnData.y, buttonX, buttonY));
                }
            }

            // 使用Group包装，避免布局约束
            Group group = new Group(imageContainer);
            host.getChildren().add(group);

            // 使用ChangeListener而不是绑定
            ChangeListener<Number> resizeListener = (obs, oldVal, newVal) -> {
                double centerX = (host.getWidth() - imageWidth) / 2;
                double centerY = (host.getHeight() - imageHeight) / 2;

                imageContainer.setLayoutX(centerX);
                imageContainer.setLayoutY(centerY);
            };

            host.widthProperty().addListener(resizeListener);
            host.heightProperty().addListener(resizeListener);

            // 初始位置
            resizeListener.changed(null, 0, 0);

        } catch (Exception e) {
            Logger("ERROR", "切换地图异常: " + e.getMessage());
            showErrorMap(host, "加载地图失败: " + e.getMessage());
        }
    }

    private Image loadFullImage(String imagePath) {
        try {
            // 从缓存获取
            Image image = fullImageCache.get(imagePath);
            if (image == null) {
                // 加载新图片
                try (InputStream stream = ResourceResolver.getResourceAsStream(imagePath)) {
                    if (stream == null) {
                        Logger("ERROR", "图片不存在: " + imagePath);
                        return null;
                    }

                    image = new Image(new java.io.ByteArrayInputStream(stream.readAllBytes()));
                }

                if (image.isError()) {
                    Logger("ERROR", "图片加载错误: " + imagePath);
                    return null;
                }

                fullImageCache.put(imagePath, image);
                imageDimensions.put(getMapIdFromPath(imagePath), new double[]{image.getWidth(), image.getHeight()});
            }
            return image;
        } catch (Exception e) {
            Logger("ERROR", "加载完整图片异常: " + e.getMessage());
            return null;
        }
    }

    private String getMapIdFromPath(String imagePath) {
        for (Map.Entry<String, MapData.MapInfo> entry : fullMap.entrySet()) {
            String normalizedPath = normalizeImagePath(entry.getValue().map);
            if (imagePath.equals(normalizedPath)) {
                return entry.getKey();
            }
        }
        return "unknown";
    }

    private void showErrorMap(StackPane host, String errorMessage) {
        VBox errorBox = new VBox(20);
        errorBox.setAlignment(Pos.CENTER);
        errorBox.setStyle("-fx-background-color: " + DARK_BG + ";" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 30;" +
                "-fx-border-color: #ef4444;" +
                "-fx-border-width: 2;" +
                "-fx-border-radius: 12;");

        // 错误图标
        Label errorIcon = new Label("⚠");
        errorIcon.setStyle("-fx-text-fill: #ef4444;" +
                "-fx-font-size: 48px;");

        // 错误信息
        Label errorLabel = new Label(errorMessage);
        errorLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";" +
                "-fx-font-size: 16px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-alignment: center;");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);

        // 重试按钮
        Button retryButton = new Button("重试");
        retryButton.setStyle("-fx-background-color: " + PRIMARY_COLOR + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: 600;" +
                "-fx-padding: 10 25;" +
                "-fx-background-radius: 6;" +
                "-fx-cursor: hand;");

        retryButton.setOnMouseEntered(e -> retryButton.setStyle(
                "-fx-background-color: " + SECONDARY_COLOR + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: 600;" +
                        "-fx-padding: 10 25;" +
                        "-fx-background-radius: 6;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(99,102,241,0.3), 8, 0.3, 0, 2);"
        ));

        retryButton.setOnMouseExited(e -> retryButton.setStyle(
                "-fx-background-color: " + PRIMARY_COLOR + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: 600;" +
                        "-fx-padding: 10 25;" +
                        "-fx-background-radius: 6;" +
                        "-fx-cursor: hand;"
        ));

        retryButton.setOnAction(e -> {
            // 清除缓存并重试
            thumbnailCache.clear();
            fullImageCache.clear();
            loadMapJson();
            preloadImageDimensions();
        });

        errorBox.getChildren().addAll(errorIcon, errorLabel, retryButton);
        host.getChildren().add(errorBox);
    }

    private Button createMapButton(MapData.MapInfo.Button data) {
        Button button = new Button(data.title);
        button.setStyle("-fx-background-color: rgba(30, 41, 59, 0.9);" +
                "-fx-text-fill: #e2e8f0;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: 600;" +
                "-fx-padding: 8 12;" +
                "-fx-background-radius: 8;" +
                "-fx-border-color: " + BORDER_COLOR + ";" +
                "-fx-border-width: 1.5;" +
                "-fx-border-radius: 8;" +
                "-fx-cursor: hand;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0.2, 0, 2);");

        // 悬停效果
        button.setOnMouseEntered(e -> button.setStyle(
                "-fx-background-color: rgba(51, 65, 85, 0.95);" +
                        "-fx-text-fill: #f1f5f9;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: 600;" +
                        "-fx-padding: 8 12;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: #94a3b8;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 8;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(6, 182, 212, 0.3), 8, 0.3, 0, 2);"
        ));

        button.setOnMouseExited(e -> button.setStyle(
                "-fx-background-color: rgba(30, 41, 59, 0.9);" +
                        "-fx-text-fill: #e2e8f0;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: 600;" +
                        "-fx-padding: 8 12;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: " + BORDER_COLOR + ";" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 8;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0.2, 0, 2);"
        ));

        // 点击事件
        button.setOnAction(e -> {
            Logger("INFO", "选择按钮: " + data.id + " - " + data.title);
            if (future != null && !future.isDone()) {
                future.complete(data.id);
            }
            close();
        });

        return button;
    }

    private void close() {
        Platform.runLater(() -> {
            try {
                GameManager gm = GameManager.getInstance();
                if (gm != null && uiRoot != null) {
                    gm.getContentWithOverlays().getChildren().remove(uiRoot);
                    uiRoot = null;
                    currentMapId = null;
                }
            } catch (Exception e) {
                Logger("ERROR", "关闭地图选择器异常: " + e.getMessage());
            }
        });
    }

    // 清理缓存
    public void clearCache() {
        thumbnailCache.clear();
        fullImageCache.clear();
        imageDimensions.clear();
        Logger("INFO", "地图管理器缓存已清理");
    }

    // 获取地图信息
    public Map<String, MapData.MapInfo> getFullMap() {
        return new HashMap<>(fullMap);
    }

    // 获取当前选中的地图ID
    public String getCurrentMapId() {
        return currentMapId;
    }

    /**
     * 获取指定地图的按钮ID列表
     * @param mapId 地图ID，如果为null则返回所有地图的按钮ID
     * @return 按钮ID列表
     */
    public List<Integer> getButtonIds(String mapId) {
        List<Integer> buttonIds = new ArrayList<>();

        if (mapId != null) {
            // 返回指定地图的按钮ID
            MapData.MapInfo mapInfo = fullMap.get(mapId);
            if (mapInfo != null && mapInfo.button != null) {
                for (MapData.MapInfo.Button button : mapInfo.button) {
                    buttonIds.add(button.id);
                }
            }
        } else {
            // 返回所有地图的按钮ID
            for (MapData.MapInfo mapInfo : fullMap.values()) {
                if (mapInfo.button != null) {
                    for (MapData.MapInfo.Button button : mapInfo.button) {
                        buttonIds.add(button.id);
                    }
                }
            }
        }

        return buttonIds;
    }

    /**
     * 获取指定地图的按钮ID列表（去重版本）
     * @param mapId 地图ID，如果为null则返回所有地图的按钮ID
     * @return 去重后的按钮ID列表
     */
    public List<Integer> getDistinctButtonIds(String mapId) {
        Set<Integer> distinctIds = new LinkedHashSet<>(getButtonIds(mapId));
        return new ArrayList<>(distinctIds);
    }

    /**
     * 获取指定地图的按钮ID列表（排序版本）
     * @param mapId 地图ID，如果为null则返回所有地图的按钮ID
     * @param ascending 是否升序排序
     * @return 排序后的按钮ID列表
     */
    public List<Integer> getSortedButtonIds(String mapId, boolean ascending) {
        List<Integer> buttonIds = getButtonIds(mapId);
        if (ascending) {
            buttonIds.sort(Integer::compareTo);
        } else {
            buttonIds.sort(Collections.reverseOrder());
        }
        return buttonIds;
    }

    /**
     * 检查指定按钮ID是否存在
     * @param buttonId 要检查的按钮ID
     * @param mapId 地图ID，如果为null则在所有地图中检查
     * @return 是否存在
     */
    public boolean containsButtonId(int buttonId, String mapId) {
        if (mapId != null) {
            MapData.MapInfo mapInfo = fullMap.get(mapId);
            if (mapInfo != null && mapInfo.button != null) {
                for (MapData.MapInfo.Button button : mapInfo.button) {
                    if (button.id == buttonId) {
                        return true;
                    }
                }
            }
        } else {
            for (MapData.MapInfo mapInfo : fullMap.values()) {
                if (mapInfo.button != null) {
                    for (MapData.MapInfo.Button button : mapInfo.button) {
                        if (button.id == buttonId) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 获取指定地图的按钮数量
     * @param mapId 地图ID，如果为null则返回所有地图的按钮总数
     * @return 按钮数量
     */
    public int getButtonCount(String mapId) {
        return getButtonIds(mapId).size();
    }

    /**
     * 获取所有地图ID的列表
     * @return 地图ID列表
     */
    public List<String> getMapIds() {
        return new ArrayList<>(fullMap.keySet());
    }

    /**
     * 获取排序后的地图ID列表
     * @param ascending 是否升序排序
     * @return 排序后的地图ID列表
     */
    public List<String> getSortedMapIds(boolean ascending) {
        List<String> mapIds = getMapIds();
        if (ascending) {
            Collections.sort(mapIds);
        } else {
            Collections.sort(mapIds, Collections.reverseOrder());
        }
        return mapIds;
    }

    /**
     * 获取包含指定关键词的地图ID列表
     * @param keyword 关键词（不区分大小写）
     * @return 匹配的地图ID列表
     */
    public List<String> getMapIdsByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getMapIds();
        }

        String lowerKeyword = keyword.toLowerCase();
        List<String> matchedIds = new ArrayList<>();

        for (String mapId : fullMap.keySet()) {
            if (mapId.toLowerCase().contains(lowerKeyword)) {
                matchedIds.add(mapId);
            }
        }

        return matchedIds;
    }

    /**
     * 检查指定地图ID是否存在
     * @param mapId 要检查的地图ID
     * @return 是否存在
     */
    public boolean containsMapId(String mapId) {
        return fullMap.containsKey(mapId);
    }

    /**
     * 获取地图数量
     * @return 地图总数
     */
    public int getMapCount() {
        return fullMap.size();
    }
}
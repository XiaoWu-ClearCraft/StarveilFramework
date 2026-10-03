package com.xiaowu.game.starveil.ui.inventory;
import com.xiaowu.game.starveil.infrastructure.Fonts;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.game.item.Inventory;
import com.xiaowu.game.starveil.game.item.Item;
import com.xiaowu.game.starveil.game.item.ItemRegistry;
import com.xiaowu.game.starveil.game.world.WorldMap;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Path;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.VLineTo;
import javafx.scene.shape.ClosePath;

/**
 * 背包UI - B键打开的叠加层UI
 * 左侧手上格子 + 右侧3x3网格 + 底部物品详情
 */
public class BackpackUI {
    private static final int CELL_SIZE = 64;
    private static final int GAP = 8;

    private final Inventory inventory;
    private Pane root;
    private VBox detailsPanel;
    private Label detailsName;
    private Label detailsDesc;

    // 保存格子引用，避免遍历节点树
    private StackPane handCell;
    private StackPane[] backpackCells = new StackPane[9];

    private Runnable onCloseCallback;
    private java.util.function.Consumer<String> useItemCallback;
    private WorldMap worldMap;

    // 垃圾桶区域
    private Pane trashZoneLeft;
    private Pane trashZoneRight;
    private Rectangle trashBgLeft;
    private Rectangle trashBgRight;
    private Path trashIconLeft;
    private Path trashIconRight;
    private String draggingSlotId; // 当前拖拽的槽位ID

    public BackpackUI(Inventory inventory) {
        this.inventory = inventory;
        createUI();
    }

    public void setWorldMap(WorldMap worldMap) {
        this.worldMap = worldMap;
    }

    private void createUI() {
        root = new Pane();
        root.setMouseTransparent(false);
        root.setFocusTraversable(true);

        // 半透明背景
        Rectangle bg = new Rectangle();
        bg.setFill(Color.rgb(0, 0, 0, 0.6));
        bg.widthProperty().bind(root.widthProperty());
        bg.heightProperty().bind(root.heightProperty());
        bg.setMouseTransparent(true);
        root.getChildren().add(bg);

        // 垃圾桶区域宽度
        final int TRASH_ZONE_WIDTH = 80;

        // 左侧垃圾桶区域
        trashZoneLeft = new Pane();
        trashZoneLeft.setPrefWidth(TRASH_ZONE_WIDTH);
        trashZoneLeft.prefHeightProperty().bind(root.heightProperty());
        trashZoneLeft.setMouseTransparent(false);
        trashZoneLeft.toFront();

        trashBgLeft = new Rectangle();
        trashBgLeft.widthProperty().bind(trashZoneLeft.prefWidthProperty());
        trashBgLeft.heightProperty().bind(trashZoneLeft.prefHeightProperty());
        trashBgLeft.setMouseTransparent(true);
        trashZoneLeft.getChildren().add(trashBgLeft);

        // 左侧垃圾桶图标
        trashIconLeft = createTrashIcon(28);
        trashIconLeft.setFill(Color.rgb(150, 150, 150));
        trashIconLeft.setMouseTransparent(true);
        trashZoneLeft.getChildren().add(trashIconLeft);

        setupTrashZone(trashZoneLeft, trashBgLeft, trashIconLeft, true);
        root.getChildren().add(trashZoneLeft);

        // 右侧垃圾桶区域
        trashZoneRight = new Pane();
        trashZoneRight.setPrefWidth(TRASH_ZONE_WIDTH);
        trashZoneRight.prefHeightProperty().bind(root.heightProperty());
        trashZoneRight.setMouseTransparent(false);
        trashZoneRight.toFront();

        trashBgRight = new Rectangle();
        trashBgRight.widthProperty().bind(trashZoneRight.prefWidthProperty());
        trashBgRight.heightProperty().bind(trashZoneRight.prefHeightProperty());
        trashBgRight.setMouseTransparent(true);
        trashZoneRight.getChildren().add(trashBgRight);

        // 右侧垃圾桶图标
        trashIconRight = createTrashIcon(28);
        trashIconRight.setFill(Color.rgb(150, 150, 150));
        trashIconRight.setMouseTransparent(true);
        trashZoneRight.getChildren().add(trashIconRight);

        setupTrashZone(trashZoneRight, trashBgRight, trashIconRight, false);
        root.getChildren().add(trashZoneRight);

        // 主容器
        VBox mainBox = new VBox(16);
        mainBox.setAlignment(Pos.CENTER);
        mainBox.setPadding(new Insets(30));
        mainBox.toFront();

        // 标题
        Label title = new Label("背包");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 28px;");
        title.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 28));

        // 内容区域：手上格子 + 3x3网格
        HBox contentBox = new HBox(24);
        contentBox.setAlignment(Pos.CENTER);

        // 左侧：手上格子
        VBox handBox = new VBox(8);
        handBox.setAlignment(Pos.CENTER);
        Label handLabel = new Label("手上");
        handLabel.setTextFill(Color.rgb(200, 200, 200));
        handLabel.setStyle("-fx-font-size: 14px;");
        handLabel.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 14));
        handCell = createCell(null);
        updateHandCell(handCell);
        handBox.getChildren().addAll(handLabel, handCell);

        // 右侧：3x3背包网格
        GridPane backpackGrid = new GridPane();
        backpackGrid.setHgap(GAP);
        backpackGrid.setVgap(GAP);
        backpackGrid.setAlignment(Pos.CENTER);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                backpackCells[index] = createCell(index);
                updateBackpackCell(backpackCells[index], index);
                backpackGrid.add(backpackCells[index], col, row);
            }
        }

        contentBox.getChildren().addAll(handBox, backpackGrid);

        // 底部：物品详情
        detailsPanel = new VBox(4);
        detailsPanel.setAlignment(Pos.CENTER);
        detailsPanel.setPadding(new Insets(10));
        detailsPanel.setMinWidth(400);
        detailsPanel.setMaxWidth(500);

        detailsName = new Label();
        detailsName.setTextFill(Color.rgb(255, 200, 200));
        detailsName.setStyle("-fx-font-size: 16px;");
        detailsName.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 16));
        detailsName.setAlignment(Pos.CENTER);

        detailsDesc = new Label();
        detailsDesc.setTextFill(Color.rgb(180, 180, 180));
        detailsDesc.setStyle("-fx-font-size: 13px;");
        detailsDesc.setFont(Fonts.safeFont("starveil:fonts/zhengjing.ttf", 13));
        detailsDesc.setWrapText(true);
        detailsDesc.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        detailsDesc.setAlignment(Pos.CENTER);

        detailsPanel.getChildren().addAll(detailsName, detailsDesc);

        mainBox.getChildren().addAll(title, contentBox, detailsPanel);
        root.getChildren().add(mainBox);

        // 居中
        mainBox.layoutXProperty().bind(root.widthProperty().subtract(mainBox.widthProperty()).divide(2));
        mainBox.layoutYProperty().bind(root.heightProperty().subtract(mainBox.heightProperty()).divide(2));

        // 垃圾桶区域定位 - 固定在窗口边缘
        trashZoneLeft.layoutXProperty().bind(root.layoutXProperty());
        trashZoneLeft.layoutYProperty().bind(root.layoutYProperty());
        trashZoneRight.layoutXProperty().bind(root.widthProperty().subtract(TRASH_ZONE_WIDTH));
        trashZoneRight.layoutYProperty().bind(root.layoutYProperty());

        // 点击背景关闭
        bg.setOnMouseClicked(e -> {
            if (onCloseCallback != null) onCloseCallback.run();
        });
    }

    private StackPane createCell(Integer index) {
        StackPane cell = new StackPane();
        cell.setPrefSize(CELL_SIZE, CELL_SIZE);
        cell.setMinSize(CELL_SIZE, CELL_SIZE);
        cell.setMaxSize(CELL_SIZE, CELL_SIZE);

        // 背景
        Rectangle cellBg = new Rectangle(CELL_SIZE, CELL_SIZE);
        cellBg.setFill(Color.rgb(40, 40, 40, 0.85));
        cellBg.setStroke(Color.rgb(100, 100, 100));
        cellBg.setStrokeWidth(1.5);
        cell.setMouseTransparent(false);

        String slotId = (index != null) ? "backpack:" + index : "hand";

        // ---- 拖拽检测 ----
        cell.setOnDragDetected(e -> {
            String itemId = (index != null) ? inventory.getBackpackSlot(index) : inventory.getHandSlot();
            if (itemId == null) { e.consume(); return; } // 空格子不能拖
            Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(slotId);
            db.setContent(content);
            // 拖拽视图：半透明截图
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            WritableImage snapshot = cell.snapshot(sp, null);
            db.setDragView(snapshot, snapshot.getWidth() / 2, snapshot.getHeight() / 2);
            e.consume();
        });

        // ---- 拖拽经过 ----
        cell.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                String src = e.getDragboard().getString();
                if (!src.equals(slotId)) { // 不能拖到自己
                    e.acceptTransferModes(TransferMode.MOVE);
                }
            }
            e.consume();
        });

        // ---- 拖拽放下 ----
        cell.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                String src = db.getString();
                performSlotSwap(src, slotId);
                refreshAllCells();
                updateDetails(null);
                e.setDropCompleted(true);
            }
            e.consume();
        });

        // ---- 拖拽完成 ----
        cell.setOnDragDone(e -> e.consume());

        // ---- 点击 / 双击 / 右键 ----
        ContextMenu contextMenu = new ContextMenu();
        MenuItem useMenuItem = new MenuItem("使用物品");
        MenuItem removeMenuItem = new MenuItem("移除物品");
        contextMenu.getItems().addAll(useMenuItem, removeMenuItem);

        if (index != null) {
            final int idx = index;
            cell.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    // 右键弹出菜单
                    String itemId = inventory.getBackpackSlot(idx);
                    Item item = itemId != null ? ItemRegistry.getInstance().getItem(itemId) : null;
                    useMenuItem.setVisible(item != null && item.getEvent("onUse") != null);
                    removeMenuItem.setVisible(itemId != null);
                    contextMenu.show(cell, e.getScreenX(), e.getScreenY());
                    return;
                }
                if (e.getClickCount() == 2) {
                    // 双击使用物品
                    String itemId = inventory.getBackpackSlot(idx);
                    if (itemId != null && useItemCallback != null) {
                        useItemCallback.accept(itemId);
                        refreshAllCells();
                        updateDetails(null);
                    }
                } else {
                    // 单击交换到手
                    inventory.swapWithHand(idx);
                    refreshAllCells();
                    updateDetails(null);
                }
            });
            cell.setOnMouseEntered(e -> {
                String itemId = inventory.getBackpackSlot(idx);
                updateDetails(itemId);
            });
            cell.setOnMouseExited(e -> updateDetails(null));
        } else {
            cell.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    String itemId = inventory.getHandSlot();
                    Item item = itemId != null ? ItemRegistry.getInstance().getItem(itemId) : null;
                    useMenuItem.setVisible(item != null && item.getEvent("onUse") != null);
                    removeMenuItem.setVisible(itemId != null);
                    contextMenu.show(cell, e.getScreenX(), e.getScreenY());
                    return;
                }
                if (e.getClickCount() == 2) {
                    // 双击使用手上物品
                    String itemId = inventory.getHandSlot();
                    if (itemId != null && useItemCallback != null) {
                        useItemCallback.accept(itemId);
                        refreshAllCells();
                        updateDetails(null);
                    }
                } else {
                    // 单击放到第一个空格
                    inventory.moveHandToFirstEmpty();
                    refreshAllCells();
                    updateDetails(null);
                }
            });
            cell.setOnMouseEntered(e -> {
                String itemId = inventory.getHandSlot();
                updateDetails(itemId);
            });
            cell.setOnMouseExited(e -> updateDetails(null));
        }

        // 右键菜单：使用物品
        useMenuItem.setOnAction(e -> {
            String itemId = (index != null) ? inventory.getBackpackSlot(index) : inventory.getHandSlot();
            if (itemId != null && useItemCallback != null) {
                useItemCallback.accept(itemId);
                refreshAllCells();
                updateDetails(null);
            }
            contextMenu.hide();
        });

        // 右键菜单：移除物品
        removeMenuItem.setOnAction(e -> {
            if (index != null) {
                inventory.setBackpackSlot(index, null);
            } else {
                inventory.setHandSlot(null);
            }
            refreshAllCells();
            updateDetails(null);
            contextMenu.hide();
        });

        cell.getChildren().add(cellBg);
        return cell;
    }

    /**
     * 执行槽位交换
     * @param source "backpack:N" 或 "hand"
     * @param target "backpack:N" 或 "hand"
     */
    private void performSlotSwap(String source, String target) {
        if (source.equals(target)) return;

        String srcType = source.split(":")[0];
        String tgtType = target.split(":")[0];
        int srcIdx = srcType.equals("backpack") ? Integer.parseInt(source.split(":")[1]) : -1;
        int tgtIdx = tgtType.equals("backpack") ? Integer.parseInt(target.split(":")[1]) : -1;

        if (srcType.equals("backpack") && tgtType.equals("backpack")) {
            // 背包格 → 背包格
            inventory.swapBackpackSlots(srcIdx, tgtIdx);
        } else if (srcType.equals("backpack") && tgtType.equals("hand")) {
            // 背包格 → 手上
            inventory.swapWithHand(srcIdx);
        } else if (srcType.equals("hand") && tgtType.equals("backpack")) {
            // 手上 → 背包格
            inventory.swapWithHand(tgtIdx);
        }
    }

    private void updateHandCell(StackPane cell) {
        cell.getChildren().removeIf(node -> node instanceof ImageView || node instanceof Label);
        String itemId = inventory.getHandSlot();
        if (itemId != null) {
            Item item = ItemRegistry.getInstance().getItem(itemId);
            if (item != null && item.getIconPath() != null) {
                try {
                    javafx.scene.Node iconNode = com.xiaowu.game.starveil.render.TextureNodeFactory.load(
                            item.getIconPath(), CELL_SIZE - 8, CELL_SIZE - 8);
                    if (iconNode != null) {
                        cell.getChildren().add(iconNode);
                    } else {
                        Label fallback = new Label("?");
                        fallback.setTextFill(Color.WHITE);
                        cell.getChildren().add(fallback);
                    }
                } catch (Exception e) {
                    Label fallback = new Label("?");
                    fallback.setTextFill(Color.WHITE);
                    cell.getChildren().add(fallback);
                }
            }
        }
    }

    private void updateBackpackCell(StackPane cell, int index) {
        cell.getChildren().removeIf(node -> node instanceof ImageView || node instanceof Label);
        String itemId = inventory.getBackpackSlot(index);
        if (itemId != null) {
            Item item = ItemRegistry.getInstance().getItem(itemId);
            if (item != null && item.getIconPath() != null) {
                try {
                    javafx.scene.Node iconNode = com.xiaowu.game.starveil.render.TextureNodeFactory.load(
                            item.getIconPath(), CELL_SIZE - 8, CELL_SIZE - 8);
                    if (iconNode != null) {
                        cell.getChildren().add(iconNode);
                    } else {
                        Label fallback = new Label("?");
                        fallback.setTextFill(Color.WHITE);
                        cell.getChildren().add(fallback);
                    }
                } catch (Exception e) {
                    Label fallback = new Label("?");
                    fallback.setTextFill(Color.WHITE);
                    cell.getChildren().add(fallback);
                }
            }
        }
    }

    public void refreshAllCells() {
        if (handCell != null) updateHandCell(handCell);
        for (int i = 0; i < backpackCells.length; i++) {
            if (backpackCells[i] != null) updateBackpackCell(backpackCells[i], i);
        }
    }

    private void updateDetails(String itemId) {
        if (itemId == null) {
            detailsName.setText("");
            detailsDesc.setText("");
            return;
        }
        Item item = ItemRegistry.getInstance().getItem(itemId);
        if (item != null) {
            detailsName.setText(item.getName());
            detailsDesc.setText(item.getDescription() != null ? item.getDescription() : "");
        } else {
            detailsName.setText(itemId);
            detailsDesc.setText("");
        }
    }

    public Pane getRoot() {
        return root;
    }

    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    public void setUseItemCallback(java.util.function.Consumer<String> callback) {
        this.useItemCallback = callback;
    }

    /**
     * 创建垃圾桶图标（JavaFX Path）
     */
    private Path createTrashIcon(double size) {
        double w = size;
        double h = size;
        double bodyTop = h * 0.2;
        double bodyBottom = h * 0.95;
        double bodyLeft = w * 0.15;
        double bodyRight = w * 0.85;
        double lidY = h * 0.15;
        double handleY = h * 0.05;
        double handleLeft = w * 0.35;
        double handleRight = w * 0.65;

        Path path = new Path();
        // 桶身
        path.getElements().addAll(
            new MoveTo(bodyLeft, bodyTop),
            new LineTo(bodyRight, bodyTop),
            new LineTo(bodyRight - w * 0.05, bodyBottom),
            new LineTo(bodyLeft + w * 0.05, bodyBottom),
            new ClosePath()
        );
        // 盖子
        path.getElements().addAll(
            new MoveTo(bodyLeft - w * 0.05, lidY),
            new LineTo(bodyRight + w * 0.05, lidY),
            new LineTo(bodyRight + w * 0.05, lidY + h * 0.04),
            new LineTo(bodyLeft - w * 0.05, lidY + h * 0.04),
            new ClosePath()
        );
        // 把手
        path.getElements().addAll(
            new MoveTo(handleLeft, lidY),
            new LineTo(handleLeft, handleY),
            new LineTo(handleRight, handleY),
            new LineTo(handleRight, lidY)
        );
        return path;
    }

    /**
     * 设置垃圾桶区域的渐变和拖拽处理
     */
    private void setupTrashZone(Pane zone, Rectangle bg, Path icon, boolean isLeft) {
        // 默认渐变：外侧白灰 -> 中间透明
        updateTrashGradient(bg, isLeft, false);

        // 图标垂直居中
        icon.layoutXProperty().bind(
            zone.widthProperty().subtract(icon.getBoundsInLocal().getWidth()).divide(2)
        );
        icon.layoutYProperty().bind(
            zone.heightProperty().subtract(icon.getBoundsInLocal().getHeight()).divide(2)
        );

        // 拖拽经过
        zone.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                String src = e.getDragboard().getString();
                if (src.startsWith("backpack:") || src.equals("hand")) {
                    updateTrashGradient(bg, isLeft, true); // 变红
                    e.acceptTransferModes(TransferMode.MOVE);
                }
            }
            e.consume();
        });

        // 拖拽离开
        zone.setOnDragExited(e -> {
            updateTrashGradient(bg, isLeft, false); // 恢复白灰
        });

        // 拖拽放下 - 丢弃物品
        zone.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                String slotId = db.getString();
                discardItem(slotId);
                e.setDropCompleted(true);
            }
            updateTrashGradient(bg, isLeft, false);
            e.consume();
        });
    }

    /**
     * 更新垃圾桶区域渐变
     */
    private void updateTrashGradient(Rectangle bg, boolean isLeft, boolean isActive) {
        double zoneWidth = bg.getWidth() > 0 ? bg.getWidth() : 80;
        double zoneHeight = bg.getHeight() > 0 ? bg.getHeight() : 600;

        if (isActive) {
            // 红色渐变
            if (isLeft) {
                // 左侧：从左(边缘)到右(透明)，使用绝对坐标
                Stop[] stops = new Stop[] {
                    new Stop(0.0, Color.rgb(200, 50, 50, 0.5)),
                    new Stop(0.6, Color.rgb(180, 40, 40, 0.25)),
                    new Stop(1.0, Color.TRANSPARENT)
                };
                bg.setFill(new LinearGradient(0, 0, zoneWidth, 0, false, CycleMethod.NO_CYCLE, stops));
            } else {
                // 右侧：从左(透明)到右(边缘)，使用绝对坐标
                Stop[] stops = new Stop[] {
                    new Stop(0.0, Color.TRANSPARENT),
                    new Stop(0.4, Color.rgb(180, 40, 40, 0.25)),
                    new Stop(1.0, Color.rgb(200, 50, 50, 0.5))
                };
                bg.setFill(new LinearGradient(0, 0, zoneWidth, 0, false, CycleMethod.NO_CYCLE, stops));
            }
        } else {
            // 白灰色渐变
            if (isLeft) {
                Stop[] stops = new Stop[] {
                    new Stop(0.0, Color.rgb(200, 200, 200, 0.3)),
                    new Stop(0.6, Color.rgb(150, 150, 150, 0.15)),
                    new Stop(1.0, Color.TRANSPARENT)
                };
                bg.setFill(new LinearGradient(0, 0, zoneWidth, 0, false, CycleMethod.NO_CYCLE, stops));
            } else {
                Stop[] stops = new Stop[] {
                    new Stop(0.0, Color.TRANSPARENT),
                    new Stop(0.4, Color.rgb(150, 150, 150, 0.15)),
                    new Stop(1.0, Color.rgb(200, 200, 200, 0.3))
                };
                bg.setFill(new LinearGradient(0, 0, zoneWidth, 0, false, CycleMethod.NO_CYCLE, stops));
            }
        }
    }

    /**
     * 丢弃物品：从背包/手上移除并丢到地图上
     */
    private void discardItem(String slotId) {
        if (worldMap == null) return;

        String itemId = null;
        if (slotId.equals("hand")) {
            itemId = inventory.getHandSlot();
            if (itemId != null) {
                inventory.setHandSlot(null);
                com.xiaowu.game.starveil.ui.core.GameUI.getInstance().updateHandSlot(null);
            }
        } else if (slotId.startsWith("backpack:")) {
            int idx = Integer.parseInt(slotId.split(":")[1]);
            itemId = inventory.getBackpackSlot(idx);
            if (itemId != null) {
                inventory.setBackpackSlot(idx, null);
            }
        }

        if (itemId != null) {
            com.xiaowu.game.starveil.game.ecs.comp.Transform t =
                com.xiaowu.game.starveil.game.state.GameInstance.getPlayerTransformComp();
            double px = t != null ? t.x : 0;
            double py = t != null ? t.y : 0;
            worldMap.dropItem(itemId, px + 50, py + 50);
            refreshAllCells();
            updateDetails(null);
        }
    }
}

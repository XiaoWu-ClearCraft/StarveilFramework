package com.xiaowu.game.starveil.ui.container;
import com.xiaowu.game.starveil.infrastructure.Fonts;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.game.item.Inventory;
import com.xiaowu.game.starveil.game.item.Item;
import com.xiaowu.game.starveil.game.item.ItemRegistry;
import com.xiaowu.game.starveil.game.world.Container;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;

/**
 * 容器 UI - 打开容器时显示的物品面板
 * 玩家可点击物品将其转移到背包
 */
public class ContainerUI {

    private static final int CELL_SIZE = 56;
    private static final int GAP = 6;

    private final Container container;
    private final Inventory inventory;
    private final Pane root;
    private GridPane itemsGrid;

    private Runnable onCloseCallback;

    public ContainerUI(Container container, Inventory inventory) {
        this.container = container;
        this.inventory = inventory;
        root = new Pane();
        root.setMouseTransparent(false);
        root.setFocusTraversable(true);
        createUI();
    }

    private void createUI() {
        // 半透明背景
        Rectangle bg = new Rectangle();
        bg.setFill(Color.rgb(0, 0, 0, 0.6));
        bg.widthProperty().bind(root.widthProperty());
        bg.heightProperty().bind(root.heightProperty());
        root.getChildren().add(bg);

        // 主容器
        VBox mainBox = new VBox(12);
        mainBox.setAlignment(Pos.CENTER);
        mainBox.setPadding(new Insets(24));

        // 标题
        Label title = new Label(container.getId());
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 24px;");
        title.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 24));

        // 物品网格
        itemsGrid = new GridPane();
        itemsGrid.setHgap(GAP);
        itemsGrid.setVgap(GAP);
        itemsGrid.setAlignment(Pos.CENTER);

        refreshGrid();

        mainBox.getChildren().addAll(title, itemsGrid);
        root.getChildren().add(mainBox);

        // 居中
        mainBox.layoutXProperty().bind(root.widthProperty().subtract(mainBox.widthProperty()).divide(2));
        mainBox.layoutYProperty().bind(root.heightProperty().subtract(mainBox.heightProperty()).divide(2));

        // 点击背景关闭
        bg.setOnMouseClicked(e -> {
            if (onCloseCallback != null) onCloseCallback.run();
        });
    }

    private void refreshGrid() {
        itemsGrid.getChildren().clear();
        List<String> items = container.getItems();

        int cols = Math.min(items.size(), 4);
        if (cols == 0) cols = 1;

        for (int i = 0; i < items.size(); i++) {
            String itemId = items.get(i);
            int col = i % cols;
            int row = i / cols;
            StackPane cell = createCell(itemId, i);
            itemsGrid.add(cell, col, row);
        }
    }

    private StackPane createCell(String itemId, int index) {
        StackPane cell = new StackPane();
        cell.setPrefSize(CELL_SIZE, CELL_SIZE);
        cell.setMinSize(CELL_SIZE, CELL_SIZE);
        cell.setMaxSize(CELL_SIZE, CELL_SIZE);

        Rectangle cellBg = new Rectangle(CELL_SIZE, CELL_SIZE);
        cellBg.setFill(Color.rgb(50, 50, 50, 0.9));
        cellBg.setStroke(Color.rgb(150, 120, 200));
        cellBg.setStrokeWidth(1.5);
        cell.getChildren().add(cellBg);

        Item item = ItemRegistry.getInstance().getItem(itemId);
        if (item != null && item.getIconPath() != null) {
            try {
                javafx.scene.Node iconNode = com.xiaowu.game.starveil.render.TextureNodeFactory.load(
                        item.getIconPath(), CELL_SIZE - 8, CELL_SIZE - 8);
                if (iconNode != null) {
                    cell.getChildren().add(iconNode);
                }
            } catch (Exception ignored) {}
        }

        // 点击拾取到背包
        cell.setOnMouseClicked(e -> {
            String takenItemId = container.getItems().get(index);
            // 检查背包空位
            int emptySlot = -1;
            for (int i = 0; i < inventory.getBackpackSize(); i++) {
                if (inventory.getBackpackSlot(i) == null) {
                    emptySlot = i;
                    break;
                }
            }

            if (emptySlot >= 0) {
                inventory.setBackpackSlot(emptySlot, takenItemId);
                container.removeItem(index);
                refreshGrid();
            } else {
                com.xiaowu.game.starveil.ui.core.GameUI.getInstance().addMessage("<red>背包已满!</red>");
            }
        });

        return cell;
    }

    public Pane getRoot() {
        return root;
    }

    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    public void refresh() {
        refreshGrid();
    }
}

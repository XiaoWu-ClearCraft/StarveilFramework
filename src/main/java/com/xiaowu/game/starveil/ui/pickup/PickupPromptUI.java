package com.xiaowu.game.starveil.ui.pickup;
import com.xiaowu.game.starveil.infrastructure.Fonts;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.item.Inventory;
import com.xiaowu.game.starveil.game.item.Item;
import com.xiaowu.game.starveil.game.item.ItemRegistry;
import com.xiaowu.game.starveil.game.world.DroppedItem;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.ui.core.GameUI;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * 拾取提示 UI - 显示玩家附近的掉落物（ECS 实体），支持滚轮选择和 F 键拾取
 */
public class PickupPromptUI {

    private static final double PICKUP_RADIUS = 120.0;
    private static final int MAX_VISIBLE = 3;
    private static final int ITEM_SLOT_SIZE = 40;
    private static final int GAP = 4;

    private final Pane root;
    private final VBox itemsContainer;
    private final WorldMap worldMap;
    private final Inventory inventory;

    // 缓存字体，延迟加载
    private javafx.scene.text.Font nameFont;
    private javafx.scene.text.Font descFont;
    private javafx.scene.text.Font hintFont;
    private boolean fontsLoaded = false;

    private int[] nearbyItems = new int[0];
    private int selectedIndex = 0;

    private double lastPlayerX = -999;
    private double lastPlayerY = -999;
    private int lastNearbyCount = -1; // 用于检测列表变化

    public PickupPromptUI(WorldMap worldMap, Inventory inventory) {
        this.worldMap = worldMap;
        this.inventory = inventory;

        root = new Pane();
        root.setMouseTransparent(true);

        itemsContainer = new VBox(GAP);
        itemsContainer.setAlignment(Pos.CENTER_RIGHT);
        itemsContainer.setPadding(new Insets(8));
        itemsContainer.setPrefWidth(320);

        root.getChildren().add(itemsContainer);

        // 根 Pane 接收滚轮事件
        root.setOnScroll(this::handleScroll);
    }

    private World world() {
        return worldMap != null ? worldMap.getEcsWorld() : null;
    }

    private String itemIdOf(int entity) {
        World w = world();
        DroppedItem d = w != null ? w.get(entity, DroppedItem.class) : null;
        return d != null ? d.itemId : null;
    }

    private double itemXOf(int entity) {
        World w = world();
        Transform t = w != null ? w.get(entity, Transform.class) : null;
        return t != null ? t.x : 0;
    }

    private double itemYOf(int entity) {
        World w = world();
        Transform t = w != null ? w.get(entity, Transform.class) : null;
        return t != null ? t.y : 0;
    }

    private void loadFonts() {
        if (fontsLoaded) return;
        try {
            nameFont = Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 13);
            descFont = Fonts.safeFont("starveil:fonts/zhengjing.ttf", 11);
            hintFont = Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 12);
        } catch (Exception e) {
            // ignore, use default fonts
        }
        if (nameFont == null) nameFont = javafx.scene.text.Font.font(13);
        if (descFont == null) descFont = javafx.scene.text.Font.font(11);
        if (hintFont == null) hintFont = javafx.scene.text.Font.font(12);
        fontsLoaded = true;
    }

    /**
     * 更新拾取提示（每帧调用）
     */
    public void update(double playerX, double playerY) {
        // 位置变化超过阈值才刷新
        if (Math.abs(playerX - lastPlayerX) < 5 && Math.abs(playerY - lastPlayerY) < 5) {
            return;
        }
        lastPlayerX = playerX;
        lastPlayerY = playerY;

        nearbyItems = worldMap != null
                ? worldMap.getNearbyDroppedEntities(playerX, playerY, PICKUP_RADIUS) : new int[0];

        if (nearbyItems.length == 0) {
            if (root.isVisible()) {
                root.setVisible(false);
            }
            lastNearbyCount = 0;
            return;
        }

        root.setVisible(true);
        selectedIndex = Math.min(selectedIndex, nearbyItems.length - 1);

        // 只在列表数量变化时才重建UI
        if (nearbyItems.length != lastNearbyCount) {
            lastNearbyCount = nearbyItems.length;
            refreshDisplay();
        } else {
            // 只更新选中状态和布局
            updateSelectionDisplay();
        }
        layoutRoot(root.getScene() != null ? root.getScene().getWidth() : 1200,
                   root.getScene() != null ? root.getScene().getHeight() : 675);
    }

    private void refreshDisplay() {
        loadFonts();
        itemsContainer.getChildren().clear();

        int total = nearbyItems.length;
        int count = Math.min(total, MAX_VISIBLE);
        int start = 0;

        // 当物品数量超过最大显示数时，使用滑动窗口
        if (total > MAX_VISIBLE) {
            // 让选中物品尽量在中间
            start = selectedIndex - 1;
            if (start < 0) start = 0;
            if (start + count > total) start = total - count;
        }

        for (int i = start; i < start + count; i++) {
            int entity = nearbyItems[i];
            HBox row = createItemRow(entity, i == selectedIndex);
            itemsContainer.getChildren().add(row);
        }
    }

    /**
     * 仅更新选中状态，不重建整个UI
     */
    private void updateSelectionDisplay() {
        int selected = (selectedIndex >= 0 && selectedIndex < nearbyItems.length)
                ? nearbyItems[selectedIndex] : -1;

        for (javafx.scene.Node node : itemsContainer.getChildren()) {
            if (node instanceof HBox) {
                HBox row = (HBox) node;
                Object data = row.getUserData();
                boolean isSelected = data instanceof Integer && (Integer) data == selected;

                // 第一个子元素是 F 标签
                if (!row.getChildren().isEmpty() && row.getChildren().get(0) instanceof Label) {
                    Label fLabel = (Label) row.getChildren().get(0);
                    fLabel.setVisible(isSelected);
                }
                // 第二个子元素是图标容器，更新边框颜色
                if (row.getChildren().size() > 1 && row.getChildren().get(1) instanceof StackPane) {
                    StackPane iconPane = (StackPane) row.getChildren().get(1);
                    if (!iconPane.getChildren().isEmpty() && iconPane.getChildren().get(0) instanceof Rectangle) {
                        Rectangle iconBg = (Rectangle) iconPane.getChildren().get(0);
                        iconBg.setStroke(isSelected ? Color.rgb(255, 200, 100) : Color.rgb(100, 100, 100));
                        iconBg.setStrokeWidth(isSelected ? 2 : 1.5);
                    }
                }
            }
        }
    }

    private HBox createItemRow(int entity, boolean isSelected) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setMouseTransparent(false);

        String itemId = itemIdOf(entity);
        Item item = itemId != null ? ItemRegistry.getInstance().getItem(itemId) : null;
        String name = item != null ? item.getName() : (itemId != null ? itemId : "?");
        String desc = item != null && item.getDescription() != null ? item.getDescription() : "";
        // 截断描述
        if (desc.length() > 30) {
            desc = desc.substring(0, 27) + "...";
        }

        // 物品图标
        StackPane iconPane = new StackPane();
        iconPane.setPrefSize(ITEM_SLOT_SIZE, ITEM_SLOT_SIZE);
        iconPane.setMinSize(ITEM_SLOT_SIZE, ITEM_SLOT_SIZE);
        Rectangle iconBg = new Rectangle(ITEM_SLOT_SIZE, ITEM_SLOT_SIZE);
        iconBg.setFill(Color.rgb(40, 40, 40, 0.85));
        iconBg.setStroke(isSelected ? Color.rgb(255, 200, 100) : Color.rgb(100, 100, 100));
        iconBg.setStrokeWidth(isSelected ? 2 : 1.5);
        iconPane.getChildren().add(iconBg);

        if (item != null && item.getIconPath() != null) {
            try {
                javafx.scene.Node iconNode = com.xiaowu.game.starveil.render.TextureNodeFactory.load(
                        item.getIconPath(), ITEM_SLOT_SIZE - 8, ITEM_SLOT_SIZE - 8);
                if (iconNode != null) {
                    iconPane.getChildren().add(iconNode);
                }
            } catch (Exception ignored) {}
        }

        // 文字信息
        VBox textArea = new VBox(2);
        textArea.setPrefWidth(200);
        Label nameLabel = new Label(name);
        nameLabel.setTextFill(Color.WHITE);
        nameLabel.setStyle("-fx-font-size: 13px;");
        nameLabel.setFont(nameFont);
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(200);

        Label descLabel = new Label(desc);
        descLabel.setTextFill(Color.rgb(180, 180, 180));
        descLabel.setStyle("-fx-font-size: 11px;");
        descLabel.setFont(descFont);
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(200);

        textArea.getChildren().addAll(nameLabel, descLabel);
        HBox.setHgrow(textArea, javafx.scene.layout.Priority.ALWAYS);

        // F 按键提示
        Label fLabel = new Label("F");
        fLabel.setTextFill(Color.rgb(255, 200, 100));
        fLabel.setStyle("-fx-font-size: 12px; -fx-background-color: rgba(0,0,0,0.6); -fx-padding: 2 6;");
        fLabel.setFont(hintFont);
        fLabel.setVisible(isSelected);

        row.getChildren().addAll(fLabel, iconPane, textArea);
        row.setUserData(entity);

        // 点击选中
        row.setOnMouseClicked(ev -> {
            int idx = -1;
            for (int i = 0; i < nearbyItems.length; i++) {
                if (nearbyItems[i] == entity) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                selectedIndex = idx;
                refreshDisplay();
            }
        });

        return row;
    }
    /**
     * 全局滚轮事件处理（从场景事件过滤器调用）
     */
    public void handleScrollGlobal(ScrollEvent e) {
        if (!root.isVisible()) return;
        if (nearbyItems.length == 0) return;

        // deltaY > 0 = 向上滚, deltaY < 0 = 向下滚
        if (e.getDeltaY() > 0) {
            // 向上滚：索引减小（循环）
            selectedIndex = (selectedIndex - 1 + nearbyItems.length) % nearbyItems.length;
        } else if (e.getDeltaY() < 0) {
            // 向下滚：索引增大（循环）
            selectedIndex = (selectedIndex + 1) % nearbyItems.length;
        } else {
            return;
        }

        // 如果选中的物品不在当前可见范围内，重建显示
        if (nearbyItems.length > MAX_VISIBLE) {
            int selectedEntity = nearbyItems[selectedIndex];
            int visibleIndex = -1;
            for (int i = 0; i < itemsContainer.getChildren().size(); i++) {
                javafx.scene.Node node = itemsContainer.getChildren().get(i);
                if (node instanceof HBox && node.getUserData() instanceof Integer) {
                    if (((Integer) node.getUserData()) == selectedEntity) {
                        visibleIndex = i;
                        break;
                    }
                }
            }
            if (visibleIndex < 0) {
                // 选中物品不在可见列表中，需要重建
                refreshDisplay();
                layoutRoot(root.getScene() != null ? root.getScene().getWidth() : 1200,
                           root.getScene() != null ? root.getScene().getHeight() : 675);
                e.consume();
                return;
            }
        }

        updateSelectionDisplay();
        e.consume();
    }

    private void handleScroll(ScrollEvent e) {
        if (nearbyItems.length == 0) return;

        // deltaY > 0 = 向上滚, deltaY < 0 = 向下滚
        if (e.getDeltaY() > 0) {
            // 向上滚：索引减小（循环）
            selectedIndex = (selectedIndex - 1 + nearbyItems.length) % nearbyItems.length;
        } else if (e.getDeltaY() < 0) {
            // 向下滚：索引增大（循环）
            selectedIndex = (selectedIndex + 1) % nearbyItems.length;
        }

        refreshDisplay();
        e.consume();
    }

    private void layoutRoot(double containerWidth, double containerHeight) {
        double w = itemsContainer.prefWidth(-1);
        double h = itemsContainer.prefHeight(-1);
        // 显示在右侧中间偏上
        itemsContainer.setLayoutX(containerWidth - w - 20);
        itemsContainer.setLayoutY(containerHeight / 2 - h / 2 - 40);
    }

    public Pane getRoot() {
        return root;
    }

    /**
     * 拾取当前选中的物品
     * @return 是否成功拾取
     */
    public boolean pickUpSelected() {
        if (nearbyItems.length == 0 || selectedIndex < 0 || selectedIndex >= nearbyItems.length) {
            return false;
        }

        int entity = nearbyItems[selectedIndex];
        String itemId = itemIdOf(entity);
        double dropX = itemXOf(entity);
        double dropY = itemYOf(entity);

        String picked = worldMap != null ? worldMap.pickUpDroppedEntity(entity) : null;
        if (picked == null) return false;
        itemId = picked;

        // 放入背包
        int emptySlot = -1;
        for (int i = 0; i < inventory.getBackpackSize(); i++) {
            if (inventory.getBackpackSlot(i) == null) {
                emptySlot = i;
                break;
            }
        }

        if (emptySlot >= 0) {
            inventory.setBackpackSlot(emptySlot, itemId);
        } else {
            // 背包已满，把物品重新放回地图
            worldMap.dropItem(itemId, dropX, dropY);
            GameUI.getInstance().addMessage("<red>背包已满，无法拾取!</red>");
            return false;
        }

        // 强制下次 update 时刷新显示
        lastNearbyCount = -1;

        // 立即刷新当前显示
        nearbyItems = worldMap != null
                ? worldMap.getNearbyDroppedEntities(lastPlayerX, lastPlayerY, PICKUP_RADIUS) : new int[0];
        if (nearbyItems.length == 0) {
            root.setVisible(false);
        } else {
            selectedIndex = Math.min(selectedIndex, nearbyItems.length - 1);
            lastNearbyCount = nearbyItems.length;
            refreshDisplay();
            layoutRoot(root.getScene() != null ? root.getScene().getWidth() : 1200,
                       root.getScene() != null ? root.getScene().getHeight() : 675);
        }
        return true;
    }

    /**
     * 是否有选中的物品
     */
    public boolean hasSelected() {
        return nearbyItems.length > 0
                && selectedIndex >= 0 && selectedIndex < nearbyItems.length;
    }

    /**
     * 是否有附近物品（用于全局滚轮事件过滤）
     */
    public boolean hasNearbyItems() {
        return nearbyItems.length > 0;
    }
}

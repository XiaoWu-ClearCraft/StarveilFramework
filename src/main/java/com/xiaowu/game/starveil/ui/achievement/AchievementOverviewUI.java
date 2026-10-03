package com.xiaowu.game.starveil.ui.achievement;

import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.game.quest.AchievementManager;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import com.xiaowu.game.starveil.render.engine.RenderEngineProvider;
import com.xiaowu.game.starveil.render.engine.handles.ButtonHandle;
import com.xiaowu.game.starveil.render.engine.handles.LabelHandle;
import com.xiaowu.game.starveil.render.engine.handles.AnimationHandle;
import com.xiaowu.game.starveil.render.engine.ColorDef;
import com.xiaowu.game.starveil.render.engine.TextStyle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.Node;

public class AchievementOverviewUI {

    private final StackPane root = new StackPane();
    private final RenderEngine renderEngine = RenderEngineProvider.getInstance().getEngine();

    public AchievementOverviewUI() {
        // 半透明遮罩层（保持黑色，不改变）
        root.setStyle("-fx-background-color: rgba(0,0,0,0.75);");
        root.setOpacity(0);

        // 卡片容器 - 使用粉色系
        VBox card = new VBox(20);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(30));
        card.setPrefSize(700, 500);
        card.setStyle("-fx-background-color: #2d1f2f; -fx-background-radius: 20;");

        // 标题 - 使用RenderEngine创建
        LabelHandle titleHandle = renderEngine.createLabel(
            "成就列表",
            TextStyle.title(24, renderEngine.createColor("#FF69B4"))
        );
        Node titleNode = (Node) titleHandle.getNativeHandle();

        // 关闭按钮 - 使用RenderEngine创建
        ButtonHandle closeBtnHandle = renderEngine.createButton(
            "关闭",
            TextStyle.simple(14, renderEngine.createColor("white")),
            renderEngine.createColor("#FF69B4"),
            renderEngine.createColor("#FF85C8"),
            10
        );
        Node closeBtnNode = (Node) closeBtnHandle.getNativeHandle();
        renderEngine.setButtonAction(closeBtnHandle, this::close);

        VBox listBox = new VBox(10);
        listBox.setAlignment(Pos.TOP_CENTER);
        listBox.setPadding(new Insets(10));

        AchievementManager mgr = AchievementManager.getInstance();
        mgr.getAllAchievements().forEach((id, ach) -> {
            HBox row = createRow(ach, mgr.isAchievementUnlocked(id));
            listBox.getChildren().add(row);
        });

        card.getChildren().addAll(titleNode, listBox, closeBtnNode);

        root.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        // 渐入动画 - 使用RenderEngine
        AnimationHandle fadeIn = renderEngine.createFadeIn(root, 300, 0, 1);
        renderEngine.playAnimation(fadeIn);
    }

     
    private HBox createRow(AchievementManager.Achievement ach, boolean unlocked) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10));
        row.setPrefWidth(650);
        // 使用粉色系边框
        row.setStyle(unlocked
                ? "-fx-background-color: #3d2d3d; -fx-background-radius: 12; -fx-border-color: #FF69B4; -fx-border-width: 2; -fx-border-radius: 12;"
                : "-fx-background-color: #2d1f2f; -fx-background-radius: 12; -fx-border-color: #FFB6C1; -fx-border-width: 2; -fx-border-radius: 12;");

        javafx.scene.image.ImageView iconView = null;
        if (ach.iconPath != null && !ach.iconPath.isBlank()) {
            javafx.scene.Node node = com.xiaowu.game.starveil.render.TextureNodeFactory.load(ach.iconPath, 48, 48);
            if (node instanceof javafx.scene.image.ImageView iv) {
                iconView = iv;
            }
        }

        VBox textBox = new VBox(4);
        textBox.setAlignment(Pos.CENTER_LEFT);

        // 使用RenderEngine创建名称Label
        LabelHandle nameHandle = renderEngine.createLabel(
            ach.name,
            unlocked ? TextStyle.bold(16, renderEngine.createColor("#FFE4E1")) 
                     : TextStyle.bold(16, renderEngine.createColor("#FFB6C1"))
        );
        Node nameNode = (Node) nameHandle.getNativeHandle();

        // 使用RenderEngine创建描述Label
        LabelHandle descHandle = renderEngine.createLabel(
            ach.description,
            unlocked ? TextStyle.simple(12, renderEngine.createColor("#FFC0CB")) 
                     : TextStyle.simple(12, renderEngine.createColor("#FFB6C1"))
        );
        Node descNode = (Node) descHandle.getNativeHandle();

        textBox.getChildren().addAll(nameNode, descNode);

        if (iconView != null) row.getChildren().add(iconView);
        row.getChildren().add(textBox);
        return row;
    }

    public StackPane getRoot() {
        return root;
    }

     
    public void close() {
        AnimationHandle fadeOut = renderEngine.createFadeOut(root, 200, 1, 0);
        fadeOut.setOnComplete(() ->
                GameManager.getInstance().getContentWithOverlays().getChildren().remove(root)
        );
        renderEngine.playAnimation(fadeOut);
    }
}
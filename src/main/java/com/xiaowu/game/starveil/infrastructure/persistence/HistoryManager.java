package com.xiaowu.game.starveil.infrastructure.persistence;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.config.GameConstants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.animation.TranslateTransition;
import javafx.animation.Interpolator;
import javafx.util.Duration;

import java.util.LinkedList;

public class HistoryManager {

    private static final int MAX_HISTORY = 80;
    private final LinkedList<HistoryEntry> chatHistory = new LinkedList<>();
    private VBox historyPanel;
    private final BooleanProperty historyVisible = new SimpleBooleanProperty(false);

    private final String ACCENT = "#FFB6C1";
    private final String CARD_BG = "#3d2d3d";
    private final String PRIMARY = ContentConfig.primaryColor();
    private final String TEXT_PRIMARY = "#FFE4E1";
    private final String TEXT_SECONDARY = ContentConfig.tertiaryColor();

    private Font xiaolaiFont;
    private boolean fontLoaded = false;

    private final Pane overlay;
    private final StackPane chatContainer;
    private javafx.event.EventHandler<javafx.scene.input.KeyEvent> historyKeyHandler;

    public HistoryManager(StackPane chatContainer, Pane overlay) {
        this.chatContainer = chatContainer;
        this.overlay = overlay;
        loadCustomFont();
    }

    private void loadCustomFont() {
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream("starveil:fonts/xiaolai-sc-regular.ttf")) {
            if (is != null) {
                xiaolaiFont = Font.loadFont(is, 16);
                fontLoaded = true;
            } else {
                xiaolaiFont = Font.font("System", 16);
            }
        } catch (Exception e) {
            xiaolaiFont = Font.font("System", 16);
        }
    }

    private String getFontStyle() {
        return fontLoaded ? "-fx-font-family: '" + xiaolaiFont.getFamily() + "'; " : "-fx-font-family: system; ";
    }

    public VBox buildHistoryPanel() {
        VBox v = new VBox(10);
        v.setAlignment(Pos.TOP_CENTER);
        v.setPadding(new Insets(20));
        v.setStyle("-fx-background-color: rgba(30,41,59,0.98);" +
                "-fx-background-radius:15;-fx-border-color:" + ACCENT + ";-fx-border-width:2;-fx-border-radius:15;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.5),20,0.5,0,5);");
        v.setVisible(false);
        v.setTranslateY(-800);

        Label title = new Label("对话历史");
        title.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:24px;-fx-font-weight:bold;-fx-padding:0 0 15 0;");

        ScrollPane sp = new ScrollPane();
        sp.setStyle("-fx-background-color:transparent;-fx-border-color:transparent;-fx-padding:0;");
        sp.setFitToWidth(true);

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        sp.setContent(content);

        v.getChildren().addAll(title, sp);

        historyVisible.addListener((ob, ov, nv) -> {
            if (nv) {
                updateHistoryContent(content);
                showHistoryPanel();
            } else {
                hideHistoryPanel();
            }
        });

        this.historyPanel = v;
        return v;
    }

    private void updateHistoryContent(VBox content) {
        content.getChildren().clear();
        if (chatHistory.isEmpty()) {
            Label empty = new Label("暂无对话历史");
            empty.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_SECONDARY + ";-fx-font-size:16px;-fx-font-style:italic;");
            content.getChildren().add(empty);
            return;
        }
        for (HistoryEntry e : chatHistory) {
            VBox entryBox = new VBox(5);
            entryBox.setPadding(new Insets(10));
            entryBox.setStyle("-fx-background-color:" + CARD_BG + ";-fx-background-radius:10;-fx-border-color:" + PRIMARY + "30;" +
                    "-fx-border-width:1;-fx-border-radius:10;");
            if (e.speaker != null && !e.speaker.isEmpty()) {
                Label sp = new Label(e.speaker + ":");
                sp.setStyle(getFontStyle() + "-fx-text-fill:" + ACCENT + ";-fx-font-size:14px;-fx-font-weight:bold;");
                entryBox.getChildren().add(sp);
            }
            Label msg = new Label(filterColorTags(e.message));
            msg.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:14px;-fx-wrap-text:true;");
            msg.setMaxWidth(600);
            Label time = new Label(e.timestamp);
            time.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_SECONDARY + ";-fx-font-size:12px;-fx-font-style:italic;");
            entryBox.getChildren().addAll(msg, time);
            content.getChildren().add(entryBox);
        }
        Platform.runLater(() -> ((ScrollPane) historyPanel.getChildren().get(1)).setVvalue(1.0));
    }

    private String filterColorTags(String text) {
        if (text == null || text.isEmpty()) return text;
        return text
                .replaceAll("<more>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\{#[0-9A-Fa-f]{6}}", "")
                .replaceAll("\\{[a-zA-Z]+}", "")
                .replace("{reset}", "")
                .trim();
    }

    private void showHistoryPanel() {
        updateContainerSize();
        historyPanel.setVisible(true);
        historyPanel.toFront();
        setupHistoryMouseBlocking();
        setupHistoryKeyListeners();
        TranslateTransition t = new TranslateTransition(Duration.millis(400), historyPanel);
        t.setToY(0);
        t.setInterpolator(Interpolator.EASE_OUT);
        t.play();
    }

    private void hideHistoryPanel() {
        removeHistoryMouseBlocking();
        TranslateTransition t = new TranslateTransition(Duration.millis(300), historyPanel);
        t.setToY(-800);
        t.setInterpolator(Interpolator.EASE_IN);
        t.setOnFinished(e -> historyPanel.setVisible(false));
        t.play();
        removeHistoryKeyListeners();
    }

    private void updateContainerSize() {
        Platform.runLater(() -> {
            GameManager gm = GameManager.getInstance();
            if (gm != null && gm.getPrimaryStage() != null) {
                javafx.scene.Scene sc = gm.getPrimaryStage().getScene();
                if (sc != null) {
                    double w = sc.getWidth(), h = sc.getHeight();
                    if (historyPanel != null) {
                        historyPanel.setPrefWidth(w * 0.8);
                        historyPanel.setMaxHeight(h * 0.7);
                    }
                }
            }
        });
    }

    private void setupHistoryMouseBlocking() {
        overlay.setVisible(true);
        overlay.setMouseTransparent(false);

        overlay.setOnMousePressed(javafx.event.Event::consume);
        overlay.setOnMouseClicked(javafx.event.Event::consume);
        overlay.setOnMouseDragged(javafx.event.Event::consume);
        overlay.setOnMouseMoved(javafx.event.Event::consume);

        historyPanel.setMouseTransparent(false);
        historyPanel.setOnMouseClicked(javafx.event.Event::consume);
        setupHistoryPanelMouseTransparency(historyPanel, false);
    }

    private void setupHistoryPanelMouseTransparency(javafx.scene.Node node, boolean transparent) {
        node.setMouseTransparent(transparent);

        if (node instanceof Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                setupHistoryPanelMouseTransparency(child, transparent);
            }
        }
    }

    private void setupHistoryKeyListeners() {
        if (historyKeyHandler != null) return;

        historyKeyHandler = event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                event.consume();
                historyVisible.set(false);
            }
        };

        javafx.scene.Scene scene = chatContainer.getScene();
        if (scene != null) {
            scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, historyKeyHandler);
        }
    }

    private void removeHistoryKeyListeners() {
        if (historyKeyHandler == null) return;

        javafx.scene.Scene scene = chatContainer.getScene();
        if (scene != null) {
            scene.removeEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, historyKeyHandler);
        }
        historyKeyHandler = null;
    }

    private void removeHistoryMouseBlocking() {
        overlay.setOnMousePressed(null);
        overlay.setOnMouseClicked(null);
        overlay.setOnMouseDragged(null);
        overlay.setOnMouseMoved(null);
        overlay.setMouseTransparent(true);

        historyPanel.setOnMouseClicked(null);
        setupHistoryPanelMouseTransparency(historyPanel, false);
    }

    public void addToHistory(String speaker, String message) {
        chatHistory.addLast(new HistoryEntry(speaker, message));
        if (chatHistory.size() > MAX_HISTORY) chatHistory.removeFirst();
    }

    public void appendToLastEntry(String text) {
        if (text == null || text.isEmpty() || chatHistory.isEmpty()) return;
        HistoryEntry last = chatHistory.getLast();
        last.message += text;
    }

    public boolean isHistoryVisible() {
        return historyVisible.get();
    }

    public void setHistoryVisible(boolean visible) {
        historyVisible.set(visible);
    }

    public BooleanProperty historyVisibleProperty() {
        return historyVisible;
    }

    private static class HistoryEntry {
        String speaker, message, timestamp;

        HistoryEntry(String sp, String msg) {
            this.speaker = sp;
            this.message = msg;
            this.timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }
}

package com.xiaowu.game.starveil.ui.dialog;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.render.AnimationController;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import com.xiaowu.game.starveil.infrastructure.persistence.HistoryManager;
import com.xiaowu.game.starveil.config.GameConstants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DialogUI {

    private final String PRIMARY = ContentConfig.primaryColor();
    private final String SECONDARY = ContentConfig.secondaryColor();
    private final String ACCENT = "#FFB6C1";
    private final String DARK_BG = "#2d1f2f";
    private final String CARD_BG = "#3d2d3d";
    private final String TEXT_PRIMARY = "#FFE4E1";
    private final String TEXT_SECONDARY = ContentConfig.tertiaryColor();
    private final String DANGER = "#FF6B6B";
    private final String SUCCESS = ContentConfig.primaryColor();
    private final String WARNING_COLOR = "#FFA500";

    private StackPane chatContainer;
    private Pane overlay;
    private AnimationController animationController;
    private RenderEngine renderEngine;
    private HistoryManager historyManager;

    private TextField inputField, yField, mField, dField;
    private VBox currentDialog;
    private TextFlow dialogText;
    private Label speakerLabel;

    private Font xiaolaiFont;
    private boolean fontLoaded = false;

    public DialogUI(StackPane chatContainer, Pane overlay, AnimationController animationController, 
                    RenderEngine renderEngine, HistoryManager historyManager) {
        this.chatContainer = chatContainer;
        this.overlay = overlay;
        this.animationController = animationController;
        this.renderEngine = renderEngine;
        this.historyManager = historyManager;
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

    public VBox createDialogPanel(String speaker, String message, String voicePath,
                                   Runnable onContinueClick, Runnable onAnimationComplete) {
        VBox v = new VBox(15);
        v.setAlignment(Pos.TOP_LEFT);
        v.setPadding(new Insets(25));
        v.setMaxWidth(850);
        v.setMaxHeight(220);
        v.setStyle("-fx-background-color:rgba(30,41,59,0.95);-fx-background-radius:20;" +
                "-fx-border-color:linear-gradient(to right," + PRIMARY + "," + SECONDARY + ");-fx-border-width:2;" +
                "-fx-border-radius:20;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.3),20,0.3,0,5);");

        if (speaker != null && !speaker.isEmpty()) {
            HBox sp = new HBox();
            sp.setAlignment(Pos.CENTER_LEFT);
            speakerLabel = new Label(speaker);
            speakerLabel.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:18px;-fx-font-weight:bold;" +
                    "-fx-padding:8 20 8 15;-fx-background-color:linear-gradient(to right," + PRIMARY + "," + SECONDARY + ");" +
                    "-fx-background-radius:15;-fx-effect:dropshadow(gaussian,rgba(99,102,241,0.3),10,0.2,0,2);");
            sp.getChildren().add(speakerLabel);
            v.getChildren().add(sp);
        } else {
            Region sp = new Region();
            sp.setPrefHeight(8);
            v.getChildren().add(sp);
        }

        dialogText = new TextFlow();
        dialogText.setMaxWidth(800);
        dialogText.setPadding(new Insets(10, 0, 0, 0));
        v.getChildren().add(dialogText);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        v.getChildren().add(grow);

        HBox promptContainer = new HBox();
        promptContainer.setAlignment(Pos.BOTTOM_RIGHT);
        promptContainer.setMaxWidth(Double.MAX_VALUE);
        Label continueLabel = new Label("点击继续");
        continueLabel.setStyle(getFontStyle() + "-fx-text-fill:" + ACCENT + ";-fx-font-size:14px;-fx-font-weight:600;-fx-font-style:italic;");
        Text arrow = new Text("➤ ");
        arrow.setStyle("-fx-fill:" + ACCENT + ";-fx-font-size:12px;-fx-font-weight:bold;");
        HBox promptContent = new HBox(5, arrow, continueLabel);
        promptContent.setAlignment(Pos.CENTER_RIGHT);
        promptContent.setOpacity(0);
        promptContainer.getChildren().add(promptContent);
        v.getChildren().add(promptContainer);

        v.setOpacity(0);

        animationController.startTextAnimation(message, dialogText, promptContent, onAnimationComplete);

        StackPane.setAlignment(v, Pos.BOTTOM_CENTER);
        StackPane.setMargin(v, new Insets(0, 0, 50, 0));

        javafx.animation.FadeTransition fd = new javafx.animation.FadeTransition(javafx.util.Duration.millis(250), v);
        fd.setFromValue(0);
        fd.setToValue(1);
        fd.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        fd.play();

        currentDialog = v;
        return v;
    }

    public VBox createInputPanel(String prompt, CompletableFuture<String> inputFuture) {
        return createInputPanel(prompt, inputFuture, null);
    }

    public VBox createInputPanel(String prompt, CompletableFuture<String> inputFuture, String simulatedText) {
        VBox v = new VBox(20);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(40));
        v.setMaxWidth(650);
        v.setMaxHeight(350);
        bindPosition(v, 650, 350);
        v.setStyle("-fx-background-color: rgba(30,41,59,0.98);-fx-background-radius:25;" +
                "-fx-border-color: linear-gradient(to right," + ACCENT + "," + PRIMARY + ");-fx-border-width:2;" +
                "-fx-border-radius:25;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.4),30,0.4,0,10);");

        Label promptLabel = new Label(prompt);
        promptLabel.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:20px;-fx-font-weight:600;");
        promptLabel.setWrapText(true);
        promptLabel.setMaxWidth(550);

        inputField = new TextField();
        inputField.setPrefSize(550, 50);
        inputField.setStyle(getFontStyle() + "-fx-background-color:" + DARK_BG + ";-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:16px;" +
                "-fx-font-weight:500;-fx-border-color:" + CARD_BG + ";-fx-border-radius:12;-fx-border-width:2;" +
                "-fx-background-radius:12;-fx-padding:15;");
        inputField.setPromptText("请输入内容...");
        inputField.focusedProperty().addListener((ob, ov, nv) -> inputField.setStyle(getFontStyle() +
                "-fx-background-color:" + DARK_BG + ";-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:16px;" +
                "-fx-font-weight:500;-fx-border-color:" + (nv ? ACCENT : CARD_BG) + ";-fx-border-radius:12;" +
                "-fx-border-width:2;-fx-background-radius:12;-fx-padding:15;" +
                (nv ? "-fx-effect:dropshadow(gaussian," + ACCENT + "25,10,0.3,0,0);" : "")));

        Button ok = createStyledButton("确认", SUCCESS, "#059669");
        ok.setPrefSize(120, 45);
        ok.setOnAction(e -> confirmInput(inputFuture));

        v.getChildren().addAll(promptLabel, inputField, ok);

        if (simulatedText != null) {
            inputField.setEditable(false);
            inputField.setPromptText("按任意键自动输入...");
            ok.setOnAction(e -> {});
            final int[] charIndex = {0};
            inputField.setOnKeyPressed(e -> {
                e.consume();
                if (charIndex[0] < simulatedText.length()) {
                    inputField.appendText(String.valueOf(simulatedText.charAt(charIndex[0])));
                    charIndex[0]++;
                } else {
                    confirmInput(inputFuture);
                }
            });
        } else {
            inputField.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    e.consume();
                    confirmInput(inputFuture);
                }
            });
        }

        return v;
    }

    public VBox createDateInputPanel(String prompt, CompletableFuture<String> dateFuture) {
        VBox v = new VBox(20);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(40));
        v.setMaxWidth(650);
        v.setMaxHeight(400);
        bindPosition(v, 650, 400);
        v.setStyle("-fx-background-color: rgba(30,41,59,0.98);-fx-background-radius:25;" +
                "-fx-border-color: linear-gradient(to right," + ACCENT + "," + PRIMARY + ");-fx-border-width:2;" +
                "-fx-border-radius:25;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.4),30,0.4,0,10);");

        Label promptLabel = new Label(prompt);
        promptLabel.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:20px;-fx-font-weight:600;");
        promptLabel.setWrapText(true);
        promptLabel.setMaxWidth(550);

        HBox dateRow = new HBox(15);
        dateRow.setAlignment(Pos.CENTER);

        yField = createDateTextField("年份", 4);
        mField = createDateTextField("月份", 2);
        dField = createDateTextField("日期", 2);

        dateRow.getChildren().addAll(box(yField, "年"), box(mField, "月"), box(dField, "日"));

        Label err = new Label();
        err.setStyle(getFontStyle() + "-fx-text-fill:" + DANGER + ";-fx-font-size:14px;");
        err.setVisible(false);

        Button ok = createStyledButton("继续", SUCCESS, "#059669");
        ok.setPrefSize(120, 45);
        ok.setDisable(true);
        ok.setOnAction(e -> confirmDateInput(dateFuture));

        setupDateValidation(ok, err);
        setupDateKeyEvents(dateFuture);

        v.getChildren().addAll(promptLabel, dateRow, err, ok);

        return v;
    }

    public void showChoiceButtons(List<String> choices, VBox choiceBox, CompletableFuture<Integer> choiceFuture) {
        choiceBox.getChildren().clear();
        choiceBox.setAlignment(Pos.CENTER);
        choiceBox.setFillWidth(true);

        if (choices.size() <= 3) {
            for (int i = 0; i < choices.size(); i++) {
                final int idx = i;
                Button b = createChoiceButton((i + 1) + ". " + choices.get(i), i);
                b.setOnAction(e -> confirmChoice(idx, choiceFuture));
                b.setPrefWidth(200);
                choiceBox.getChildren().add(b);
            }
        } else if (choices.size() <= 6) {
            int rows = (int) Math.ceil(choices.size() / 2.0);
            for (int r = 0; r < rows; r++) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER);
                for (int c = 0; c < 2; c++) {
                    int idx = r * 2 + c;
                    if (idx < choices.size()) {
                        Button b = createChoiceButton((idx + 1) + ". " + choices.get(idx), idx);
                        b.setOnAction(e -> confirmChoice(idx, choiceFuture));
                        b.setPrefWidth(180);
                        row.getChildren().add(b);
                    }
                }
                choiceBox.getChildren().add(row);
            }
        }
    }

    private VBox box(TextField tf, String label) {
        VBox vb = new VBox(5);
        vb.setAlignment(Pos.CENTER);
        Label l = new Label(label);
        l.setStyle(getFontStyle() + "-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:14px;");
        vb.getChildren().addAll(tf, l);
        return vb;
    }

    private TextField createDateTextField(String prompt, int maxLen) {
        TextField tf = new TextField();
        tf.setPrefSize(80, 50);
        tf.setStyle(getFontStyle() + "-fx-background-color:" + DARK_BG + ";-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:16px;" +
                "-fx-font-weight:500;-fx-border-color:" + CARD_BG + ";-fx-border-radius:8;-fx-border-width:2;" +
                "-fx-background-radius:8;-fx-padding:10;-fx-alignment:center;");
        tf.setPromptText(prompt);
        tf.textProperty().addListener((ob, ov, nv) -> {
            if (!nv.matches("\\d*")) tf.setText(nv.replaceAll("[^\\d]", ""));
            if (tf.getText().length() > maxLen) tf.setText(tf.getText().substring(0, maxLen));
        });
        tf.focusedProperty().addListener((ob, ov, nv) ->
                tf.setStyle(getFontStyle() + "-fx-background-color:" + DARK_BG + ";-fx-text-fill:" + TEXT_PRIMARY + ";-fx-font-size:16px;" +
                        "-fx-font-weight:500;-fx-border-color:" + (nv ? ACCENT : CARD_BG) + ";-fx-border-radius:8;-fx-border-width:2;" +
                        "-fx-background-radius:8;-fx-padding:10;-fx-alignment:center;" + (nv ? "-fx-effect:dropshadow(gaussian," + ACCENT + "25,10,0.3,0,0);" : ""))
        );
        return tf;
    }

    private void setupDateValidation(Button ok, Label err) {
        Runnable v = () -> {
            String y = yField.getText().trim(), m = mField.getText().trim(), d = dField.getText().trim();
            if (y.isEmpty() || m.isEmpty() || d.isEmpty()) {
                ok.setDisable(true);
                err.setVisible(false);
                return;
            }
            try {
                int year = Integer.parseInt(y), month = Integer.parseInt(m), day = Integer.parseInt(d);
                if (isValidDate(year, month, day)) {
                    ok.setDisable(false);
                    err.setVisible(false);
                } else {
                    ok.setDisable(true);
                    err.setText("无效的日期");
                    err.setVisible(true);
                }
            } catch (NumberFormatException e) {
                ok.setDisable(true);
                err.setText("请输入数字");
                err.setVisible(true);
            }
        };
        yField.textProperty().addListener((ob, ov, nv) -> v.run());
        mField.textProperty().addListener((ob, ov, nv) -> v.run());
        dField.textProperty().addListener((ob, ov, nv) -> v.run());
    }

    private boolean isValidDate(int y, int m, int d) {
        try {
            LocalDate.of(y, m, d);
            return y >= 1000 && y <= 9999;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private void setupDateKeyEvents(CompletableFuture<String> dateFuture) {
        yField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !yField.getText().trim().isEmpty()) mField.requestFocus();
        });
        mField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !mField.getText().trim().isEmpty()) dField.requestFocus();
        });
        dField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !dField.getText().trim().isEmpty()) confirmDateInput(dateFuture);
        });
    }

    private void confirmInput(CompletableFuture<String> inputFuture) {
        String txt = inputField.getText().trim();
        if (!txt.isEmpty() && inputFuture != null) {
            historyManager.addToHistory("玩家", txt);
            inputFuture.complete(txt);
        }
    }

    private void confirmDateInput(CompletableFuture<String> dateFuture) {
        String y = yField.getText().trim(), m = mField.getText().trim(), d = dField.getText().trim();
        if (!y.isEmpty() && !m.isEmpty() && !d.isEmpty()) {
            try {
                int year = Integer.parseInt(y), month = Integer.parseInt(m), day = Integer.parseInt(d);
                if (isValidDate(year, month, day)) {
                    String dateStr = String.format("%s年%s月%s日", y, m, d);
                    historyManager.addToHistory("玩家", dateStr);
                    dateFuture.complete(dateStr);
                }
            } catch (NumberFormatException ignore) {
            }
        }
    }

    private void confirmChoice(int idx, CompletableFuture<Integer> choiceFuture) {
        if (choiceFuture != null) {
            choiceFuture.complete(idx);
        }
    }

    private Button createChoiceButton(String text, int index) {
        Button b = new Button(text);
        b.setMaxWidth(200);
        b.setPrefHeight(45);
        String[] grads = {
                "linear-gradient(to right," + PRIMARY + "," + SECONDARY + ")",
                "linear-gradient(to right," + ACCENT + "," + PRIMARY + ")",
                "linear-gradient(to right," + WARNING_COLOR + "," + ACCENT + ")",
                "linear-gradient(to right," + SUCCESS + "," + ACCENT + ")"
        };
        String grad = grads[index % grads.length];
        b.setStyle(getFontStyle() + "-fx-background-color:" + grad + ";-fx-text-fill:white;-fx-font-size:14px;" +
                "-fx-font-weight:600;-fx-padding:10 20;-fx-background-radius:10;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.3),10,0.3,0,3);");
        b.setOnMouseEntered(e -> b.setStyle(b.getStyle() + ";-fx-scale-x:1.05;-fx-scale-y:1.05;" +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.5),15,0.4,0,5);"));
        b.setOnMouseExited(e -> b.setStyle(b.getStyle().replace("-fx-scale-x:1.05;-fx-scale-y:1.05;", "")
                .replace("-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.5),15,0.4,0,5);",
                        "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.3),10,0.3,0,3);")));
        return b;
    }

    private Button createStyledButton(String text, String baseColor, String hoverColor) {
        Button button = new Button(text);
        button.setStyle(getFontStyle() +
                "-fx-background-color: " + baseColor + "; " +
                "-fx-text-fill: white; -fx-font-size: 16px; " +
                "-fx-font-weight: 600; -fx-padding: 12 25; " +
                "-fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, " + baseColor + "50, 10, 0.3, 0, 2);");

        button.setOnMouseEntered(e ->
                button.setStyle(getFontStyle() +
                        "-fx-background-color: " + hoverColor + "; " +
                        "-fx-text-fill: white; -fx-font-size: 16px; " +
                        "-fx-font-weight: 600; -fx-padding: 12 25; " +
                        "-fx-background-radius: 12; " +
                        "-fx-effect: dropshadow(gaussian, " + hoverColor + "60, 15, 0.4, 0, 3);")
        );

        button.setOnMouseExited(e ->
                button.setStyle(getFontStyle() +
                        "-fx-background-color: " + baseColor + "; " +
                        "-fx-text-fill: white; -fx-font-size: 16px; " +
                        "-fx-font-weight: 600; -fx-padding: 12 25; " +
                        "-fx-background-radius: 12; " +
                        "-fx-effect: dropshadow(gaussian, " + baseColor + "50, 10, 0.3, 0, 2);")
        );

        return button;
    }

    private void bindPosition(Region node, double prefW, double prefH) {
        Platform.runLater(() -> {
            GameManager gm = GameManager.getInstance();
            if (gm != null && gm.getPrimaryStage() != null) {
                javafx.scene.Scene sc = gm.getPrimaryStage().getScene();
                if (sc != null) {
                    double sx = sc.getWidth();
                    double sy = sc.getHeight();
                    node.setLayoutX((sx - prefW) / 2);
                    node.setLayoutY((sy - prefH) / 2);
                }
            }
        });
    }

    public void updateDialogPromptToSelect() {
        if (currentDialog == null) return;

        javafx.scene.Node lastNode = currentDialog.getChildren().get(currentDialog.getChildren().size() - 1);
        if (lastNode instanceof HBox promptContainer) {
            promptContainer.getChildren().clear();

            Label selectPrompt = new Label("请选择...");
            selectPrompt.setStyle(getFontStyle() +
                    "-fx-text-fill: " + WARNING_COLOR + "; " +
                    "-fx-font-size: 14px; -fx-font-weight: 600; -fx-font-style: italic;");

            promptContainer.getChildren().add(selectPrompt);
        }
    }

    public VBox getCurrentDialog() {
        return currentDialog;
    }

    public TextFlow getDialogText() {
        return dialogText;
    }
}

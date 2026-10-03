package com.xiaowu.game.starveil.render.engine;

import com.xiaowu.game.starveil.render.engine.handles.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 渲染引擎接口 - 定义渲染契约,便于后期替换渲染引擎
 * 
 * 该接口抽象了游戏的核心渲染需求:
 * - 文本渲染(含彩色文本、逐字动画)
 * - UI 组件渲染(进度条、按钮、面板)
 * - 特效渲染(圆形、线条、过渡动画)
 * - 摄像机控制
 */
public interface RenderEngine {

    // ==================== 初始化与生命周期 ====================

    /**
     * 初始化渲染引擎
     * @param viewportWidth 视口宽度
     * @param viewportHeight 视口高度
     */
    void initialize(double viewportWidth, double viewportHeight);

    /**
     * 销毁渲染引擎,释放资源
     */
    void dispose();

    /**
     * 获取渲染引擎的主容器节点(用于添加到场景图)
     */
    Object getRootContainer();

    /**
     * 获取特效容器节点
     */
    Object getEffectContainer();

    // ==================== 视口与摄像机 ====================

    /**
     * 设置视口大小
     */
    void setViewportSize(double width, double height);

    /**
     * 获取视口宽度
     */
    double getViewportWidth();

    /**
     * 获取视口高度
     */
    double getViewportHeight();

    /**
     * 更新摄像机位置(基于玩家位置和死区)
     * @param worldNode 世界地图节点
     * @param playerCenterX 玩家中心X
     * @param playerCenterY 玩家中心Y
     * @param deltaTime 帧间隔时间(秒)
     */
    void updateCamera(Object worldNode, double playerCenterX, double playerCenterY, double deltaTime);

    /**
     * 初始化摄像机居中
     */
    void centerCamera(Object worldNode, double playerCenterX, double playerCenterY);

    // ==================== 文本渲染 ====================

    /**
     * 解析彩色文本字符串为文本段列表
     * 支持格式: {color}text{/color} 或 {#RRGGBB}text
     * @param text 带颜色标签的文本
     * @return 解析后的文本段列表
     */
    List<TextSegment> parseColoredText(String text);

    /**
     * 创建文本渲染器(返回一个可以后续更新的文本容器)
     */
    Object createTextContainer(TextStyle style);

    /**
     * 更新文本容器的内容
     * @param textContainer 文本容器
     * @param segments 文本段列表
     */
    void setTextContent(Object textContainer, List<TextSegment> segments);

    /**
     * 更新文本容器的部分内容(用于逐字动画)
     * @param textContainer 文本容器
     * @param segments 完整文本段列表
     * @param charCount 显示的字符数
     */
    void setPartialTextContent(Object textContainer, List<TextSegment> segments, int charCount);

    /**
     * 向文本容器追加内容（不清除已有内容）
     * @param textContainer 文本容器
     * @param segments 要追加的文本段列表
     */
    default void appendTextContent(Object textContainer, List<TextSegment> segments) {
        setTextContent(textContainer, segments);
    }

    /**
     * 启动逐字显示动画
     * @param message 完整消息
     * @param textContainer 文本容器
     * @param promptContainer 提示容器(动画完成后显示)
     * @param onComplete 动画完成回调
     * @return 动画控制器(可用于跳过动画)
     */
    TextAnimationHandle startTypewriterAnimation(String message, Object textContainer,
                                                    Object promptContainer, Runnable onComplete);

    /**
     * 启动逐字显示动画（支持偏移起始位置和自定义速度）
     * @param message 完整消息（包含之前已显示的内容）
     * @param textContainer 文本容器
     * @param promptContainer 提示容器
     * @param onComplete 动画完成回调
     * @param startCharOffset 从第几个字符开始动画（之前已显示的字符数）
     * @param charIntervalMs 每个字符的显示间隔（毫秒）
     * @return 动画控制器
     */
    default TextAnimationHandle startTypewriterAnimation(String message, Object textContainer,
                                                          Object promptContainer, Runnable onComplete,
                                                          int startCharOffset, int charIntervalMs) {
        return startTypewriterAnimation(message, textContainer, promptContainer, onComplete);
    }

    /**
     * 跳过正在进行的逐字动画
     */
    void skipTypewriterAnimation(TextAnimationHandle handle);

    /**
     * 停止逐字动画
     */
    void stopTypewriterAnimation(TextAnimationHandle handle);

    // ==================== UI 组件渲染 ====================

    /**
     * 创建进度条
     * @param width 宽度
     * @param height 高度
     * @param accentColor 进度条填充颜色
     * @param backgroundColor 背景颜色
     */
    ProgressBarHandle createProgressBar(double width, double height, ColorDef accentColor, ColorDef backgroundColor);

    /**
     * 设置进度条进度
     * @param handle 进度条句柄
     * @param progress 0.0-1.0
     */
    void setProgressBarProgress(ProgressBarHandle handle, double progress);

    /**
     * 设置进度条样式
     */
    void setProgressBarStyle(ProgressBarHandle handle, ColorDef accentColor);

    /**
     * 创建按钮
     * @param text 按钮文本
     * @param style 按钮样式
     */
    ButtonHandle createButton(String text, TextStyle style);

    /**
     * 创建按钮（带背景色和悬停效果）
     * @param text 按钮文本
     * @param style 按钮文本样式
     * @param backgroundColor 背景颜色
     * @param hoverColor 悬停时的颜色（null则不使用悬停效果）
     * @param cornerRadius 圆角半径
     */
    ButtonHandle createButton(String text, TextStyle style, ColorDef backgroundColor, ColorDef hoverColor, double cornerRadius);

    /**
     * 设置按钮点击事件
     */
    void setButtonAction(ButtonHandle handle, Runnable action);

    /**
     * 更新按钮文本。
     *
     * <p>用于「按钮文字会变」的场景（例如按键重绑时先显示「等待按键…」）。
     * 注意按钮上显示的文字与 {@code setUserData} 存的业务标识是两回事：
     * 改文字不该顺手把标识也改掉，否则之后再想恢复原文案就没有依据了。
     */
    void setButtonText(ButtonHandle handle, String text);

    /**
     * 创建标签
     */
    LabelHandle createLabel(String text, TextStyle style);

    /**
     * 更新标签文本
     */
    void setLabelText(LabelHandle handle, String text);

    /**
     * 给标签绑定点击事件。
     *
     * <p>标签默认不吃鼠标事件，用它做「可点击的文字」时（例如语言列表里的选项）
     * 必须走这个入口，而不是把 {@code setUserData} 当成回调注册处。
     */
    void setLabelClickAction(LabelHandle handle, Runnable action);

    /**
     * 创建输入框
     */
    TextFieldHandle createTextField(TextStyle style, String promptText);

    /**
     * 获取输入框内容
     */
    String getTextFieldContent(TextFieldHandle handle);

    // ==================== 面板与布局 ====================

    /**
     * 创建垂直布局面板
     */
    Object createVBox(double spacing, double padding);

    /**
     * 创建水平布局面板
     */
    Object createHBox(double spacing, double padding);

    /**
     * 创建堆叠面板
     */
    Object createStackPane();

    /**
     * 向面板添加子节点
     */
    void addChild(Object parent, Object child);

    /**
     * 从面板移除子节点
     */
    void removeChild(Object parent, Object child);

    /**
     * 清空面板
     */
    void clearChildren(Object parent);

    /**
     * 设置节点位置
     */
    void setPosition(Object node, double x, double y);

    /**
     * 设置节点大小
     */
    void setSize(Object node, double width, double height);

    /**
     * 设置节点不透明度
     */
    void setOpacity(Object node, double opacity);

    /**
     * 设置节点可见性
     */
    void setVisible(Object node, boolean visible);

    /**
     * 设置节点样式
     */
    void setStyle(Object node, String style);

    // ==================== 图形与特效 ====================

    /**
     * 创建圆形
     */
    CircleHandle createCircle(double centerX, double centerY, double radius, 
                              ColorDef fillColor, ColorDef strokeColor, double strokeWidth);

    /**
     * 更新圆形位置
     */
    void setCirclePosition(CircleHandle handle, double centerX, double centerY);

    /**
     * 更新圆形半径
     */
    void setCircleRadius(CircleHandle handle, double radius);

    /**
     * 更新圆形颜色
     */
    void setCircleColor(CircleHandle handle, ColorDef fillColor, ColorDef strokeColor);

    /**
     * 创建线条
     */
    LineHandle createLine(double startX, double startY, double endX, double endY, 
                          ColorDef color, double strokeWidth);

    /**
     * 创建径向渐变矩形(用于背景效果)
     */
    Object createRadialGradientRect(double width, double height, double focusX, double focusY, 
                                    List<GradientStop> stops);

    // ==================== 动画系统 ====================

    /**
     * 创建淡入动画
     */
    AnimationHandle createFadeIn(Object node, double durationMs, double fromOpacity, double toOpacity);

    /**
     * 创建淡出动画
     */
    AnimationHandle createFadeOut(Object node, double durationMs, double fromOpacity, double toOpacity);

    /**
     * 创建缩放动画
     */
    AnimationHandle createScaleAnimation(Object node, double durationMs, 
                                         double fromScaleX, double fromScaleY, 
                                         double toScaleX, double toScaleY);

    /**
     * 播放动画
     */
    void playAnimation(AnimationHandle handle);

    /**
     * 停止动画
     */
    void stopAnimation(AnimationHandle handle);

    /**
     * 创建顺序动画
     */
    AnimationHandle createSequentialAnimation(AnimationHandle... animations);

    /**
     * 创建并行动画
     */
    AnimationHandle createParallelAnimation(AnimationHandle... animations);

    /**
     * 创建延迟
     */
    AnimationHandle createPause(double durationMs, Runnable onComplete);

    // ==================== 交互挑战系统 ====================

    /**
     * 注册键盘事件处理器
     */
    EventHandlerHandle registerKeyHandler(FunctionalCallback<KeyCodeEvent> handler);

    /**
     * 注册鼠标事件处理器
     */
    EventHandlerHandle registerMouseHandler(FunctionalCallback<MouseEvent> handler);

    /**
     * 注销事件处理器
     */
    void unregisterHandler(EventHandlerHandle handle);

    // ==================== 工具方法 ====================

    /**
     * 在主线程执行
     */
    void runLater(Runnable action);

    /**
     * 创建颜色定义
     */
    ColorDef createColor(String hexOrName);

    /**
     * 创建文本样式
     */
    TextStyle createTextStyle(String fontFamily, double fontSize, boolean bold, 
                              boolean italic, ColorDef fillColor);

    /**
     * 显示/隐藏覆盖层
     */
    void setOverlayVisible(boolean visible, ColorDef overlayColor);

    // ==================== 高级 UI 组件 ====================

    /**
     * 创建网格面板
     */
    Object createGridPane(double hgap, double vgap);

    /**
     * 向网格面板添加组件
     */
    void addToGrid(Object gridPane, Object child, int columnIndex, int rowIndex);

    /**
     * 创建滚动面板
     */
    Object createScrollPane();

    /**
     * 设置滚动面板内容
     */
    void setScrollPaneContent(Object scrollPane, Object content);

    /**
     * 创建图像视图
     */
    Object createImageView(String imagePath, double fitWidth, double fitHeight);

    /**
     * 创建分隔线
     */
    Object createSeparator(double width, ColorDef color);

    /**
     * 设置对齐方式
     */
    void setAlignment(Object node, String alignment);

    /**
     * 设置内边距
     */
    void setPadding(Object node, double top, double right, double bottom, double left);

    /**
     * 设置背景样式
     */
    void setBackground(Object node, String backgroundStyle);

    /**
     * 设置鼠标事件拦截
     */
    void setMouseTransparent(Object node, boolean transparent);

    /**
     * 设置节点可聚焦
     */
    void setFocusTraversable(Object node, boolean focusable);

    /**
     * 设置光标样式
     */
    void setCursor(Object node, String cursorType);

    /** 把光标设成手型 —— 表示「这里可以点」。 */
    default void setCursorHand(Object node) {
        setCursor(node, "HAND");
    }

    /**
     * 请求焦点
     */
    void requestFocus(Object node);

    /**
     * 设置用户数据
     */
    void setUserData(Object node, Object data);

    /**
     * 获取用户数据
     */
    Object getUserData(Object node);

    /**
     * 设置节点置顶
     */
    void bringToFront(Object node);

    /**
     * 获取节点属性
     */
    double getNodeWidth(Object node);

    double getNodeHeight(Object node);

    double getNodeOpacity(Object node);

    /**
     * 获取节点平移X位置
     */
    double getNodeTranslateX(Object node);

    /**
     * 获取节点平移Y位置
     */
    double getNodeTranslateY(Object node);

    /**
     * 创建文本节点
     */
    Object createText(String text, TextStyle style);

    /**
     * 设置文本换行宽度
     */
    void setTextWrappingWidth(Object textNode, double width);

    /**
     * 创建区域占位符
     */
    Object createRegion(double minWidth, double minHeight);

    // ==================== 高级 UI 控件 ====================

    /**
     * 创建下拉框
     */
    ComboBoxHandle createComboBox(List<String> items, double prefWidth);

    /**
     * 设置下拉框选项
     */
    void setComboBoxItems(ComboBoxHandle handle, List<String> items);

    /**
     * 设置下拉框选中值
     */
    void setComboBoxSelectedValue(ComboBoxHandle handle, String value);

    /**
     * 获取下拉框选中值
     */
    String getComboBoxSelectedValue(ComboBoxHandle handle);

    /**
     * 设置下拉框选择事件
     */
    void setComboBoxOnAction(ComboBoxHandle handle, Runnable action);

    /**
     * 创建复选框
     */
    CheckBoxHandle createCheckBox(String text);

    /**
     * 获取复选框选中状态
     */
    boolean isCheckBoxSelected(CheckBoxHandle handle);

    /**
     * 设置复选框选中状态
     */
    void setCheckBoxSelected(CheckBoxHandle handle, boolean selected);

    /**
     * 添加复选框状态变化监听
     */
    void addCheckBoxListener(CheckBoxHandle handle, java.util.function.BiConsumer<Boolean, Boolean> listener);

    /**
     * 创建滑块
     */
    SliderHandle createSlider(double min, double max, double initialValue, double prefWidth);

    /**
     * 设置滑块值
     */
    void setSliderValue(SliderHandle handle, double value);

    /**
     * 获取滑块值
     */
    double getSliderValue(SliderHandle handle);

    /**
     * 添加滑块值变化监听
     */
    void addSliderListener(SliderHandle handle, java.util.function.BiConsumer<Double, Double> listener);

    /**
     * 配置滑块刻度
     */
    void setSliderTickConfig(SliderHandle handle, boolean showTicks, boolean showLabels, 
                              double majorUnit, int minorCount, boolean snapToTicks);

    // ==================== 动画增强 ====================

    /**
     * 创建平移动画
     */
    AnimationHandle createTranslateAnimation(Object node, double durationMs, double toX, double toY);

    /**
     * 创建平移动画（带插值器）
     */
    AnimationHandle createTranslateAnimation(Object node, double durationMs, double toX, double toY, String interpolator);

    /**
     * 创建暂停动画
     */
    AnimationHandle createPauseAnimation(double durationMs, Runnable onFinished);

    // ==================== 事件处理增强 ====================

    /**
     * 设置按钮鼠标进入事件
     */
    void setButtonOnMouseEnter(ButtonHandle handle, Runnable action);

    /**
     * 设置按钮鼠标退出事件
     */
    void setButtonOnMouseExit(ButtonHandle handle, Runnable action);

    /**
     * 设置节点鼠标点击事件
     * 注意：由于MouseEvent命名冲突，暂时使用JavaFX MouseEvent直接处理
     * TODO: 后期通过事件系统统一处理
     */
    // void setNodeOnMouseClicked(Object node, Object handler);

    /**
     * 清除节点鼠标点击事件
     */
    // void clearNodeOnMouseClicked(Object node);

    // ==================== 布局增强 ====================

    /**
     * 设置最大宽度
     */
    void setMaxWidth(Object node, double maxWidth);

    /**
     * 设置最大高度
     */
    void setMaxHeight(Object node, double maxHeight);

    /**
     * 设置首选宽度
     */
    void setPrefWidth(Object node, double prefWidth);

    /**
     * 设置首选高度
     */
    void setPrefHeight(Object node, double prefHeight);

    /**
     * 设置布局X位置
     */
    void setLayoutX(Object node, double x);

    /**
     * 设置布局Y位置
     */
    void setLayoutY(Object node, double y);

    /**
     * 设置StackPane对齐方式
     */
    void setStackPaneAlignment(Object node, String alignment);

    /**
     * 设置StackPane边距
     */
    void setStackPaneMargin(Object node, double top, double right, double bottom, double left);

    /**
     * 设置ScrollPane自适应宽度
     */
    void setScrollPaneFitToWidth(Object scrollPane, boolean fit);

    /**
     * 设置ScrollPane滚动条策略
     */
    void setScrollPaneBarPolicy(Object scrollPane, String hPolicy, String vPolicy);

    // ==================== 属性系统 ====================

    /**
     * 创建布尔属性
     */
    Object createBooleanProperty(boolean initial);

    /**
     * 设置布尔属性值
     */
    void setBooleanProperty(Object property, boolean value);

    /**
     * 获取布尔属性值
     */
    boolean getBooleanProperty(Object property);

    /**
     * 添加布尔属性变化监听
     */
    void addBooleanPropertyListener(Object property, 
                                     java.util.function.BiConsumer<Boolean, Boolean> listener);

    // ==================== 节点查询 ====================

    /**
     * 检查父节点是否包含子节点
     */
    boolean hasChild(Object parent, Object child);

    /**
     * 获取节点所属场景
     */
    Object getNodeScene(Object node);

    // ==================== 字体管理 ====================

    /**
     * 加载字体
     */
    void loadFont(String resourcePath, double size);

    /**
     * 加载字体（从输入流）
     */
    void loadFont(java.io.InputStream stream, double size);

    /**
     * 设置节点字体
     */
    void setFontFamily(Object node, String fontFamily);

    // ==================== 禁用/启用 ====================

    /**
     * 设置按钮禁用状态
     */
    void setButtonDisabled(ButtonHandle handle, boolean disabled);
}

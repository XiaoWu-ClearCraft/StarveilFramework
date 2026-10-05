// InputHandler.java
package com.xiaowu.game.starveil.input;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Set;

public class InputHandler {
    // 按键状态集合（避免 KeyCode.ordinal() 可能超出固定数组大小）
    private final Set<Integer> pressed = new HashSet<>();

    // 功能键常量
        public static final int MOVE_UP = KeyCode.W.ordinal();
        public static final int MOVE_DOWN = KeyCode.S.ordinal();
        public static final int MOVE_LEFT = KeyCode.A.ordinal();
        public static final int MOVE_RIGHT = KeyCode.D.ordinal();
        public static final int ACCELERATE = KeyCode.SHIFT.ordinal();
        public static final int DEBUG_TOGGLE = KeyCode.F3.ordinal();
        public static final int PAUSE_TOGGLE = KeyCode.ESCAPE.ordinal();
        public static final int INTERACT = KeyCode.F.ordinal();  // 交互按钮，默认F键
        public static final int MAGIC_ATTACK = KeyCode.Q.ordinal();  // 法阵攻击，默认Q键
        public static final int BACKPACK = KeyCode.TAB.ordinal();  // 背包，默认Tab键

        // 功能到键码的映射（方便后期更改）
        private final Map<String, Integer> functionKeyMap = new HashMap<>();

    // ==================== 事件回调系统 ====================
    
    /**
     * 按键事件接口
     */
    public interface KeyEventHandler {
        void handle(KeyEvent event);
    }

    /**
     * 按键事件注册信息
     */
    private static class EventRegistration {
        KeyEventHandler handler;
        boolean allowConcurrent;
        
        EventRegistration(KeyEventHandler handler, boolean allowConcurrent) {
            this.handler = handler;
            this.allowConcurrent = allowConcurrent;
        }
    }

    // 按键按下事件映射（使用LinkedHashMap保持插入顺序，后注册的优先）
    private final Map<Integer, List<EventRegistration>> keyPressedHandlers = new LinkedHashMap<>();
    
    // 按键释放事件映射（使用LinkedHashMap保持插入顺序，后注册的优先）
    private final Map<Integer, List<EventRegistration>> keyReleasedHandlers = new LinkedHashMap<>();
    
    // 按键按下防抖时间映射（防止按键事件重复触发）
    private final Map<Integer, Long> keyPressDebounceMap = new HashMap<>();

    // 通过 function name 注册的 handler（动态查找当前 keyCode）
    private final Map<String, List<EventRegistration>> functionKeyPressHandlers = new LinkedHashMap<>();
    private final Map<String, List<EventRegistration>> functionKeyReleaseHandlers = new LinkedHashMap<>();

    // 默认按键事件防抖时间（毫秒）
    private static final long DEFAULT_KEY_DEBOUNCE_TIME = 50;
    
    public InputHandler() {
        initializeKeyMap();
    }
    
    private void initializeKeyMap() {
        // 设置默认键位映射
        functionKeyMap.put("MOVE_UP", MOVE_UP);
        functionKeyMap.put("MOVE_DOWN", MOVE_DOWN);
        functionKeyMap.put("MOVE_LEFT", MOVE_LEFT);
        functionKeyMap.put("MOVE_RIGHT", MOVE_RIGHT);
        functionKeyMap.put("ACCELERATE", ACCELERATE);
        functionKeyMap.put("DEBUG_TOGGLE", DEBUG_TOGGLE);
        functionKeyMap.put("INTERACT", INTERACT);  // 添加交互按钮映射
        functionKeyMap.put("MAGIC_ATTACK", MAGIC_ATTACK);  // 添加法阵攻击映射
        functionKeyMap.put("BACKPACK", BACKPACK);  // 添加背包映射
    }
    
    /**
     * 获取功能对应的键码
     */
    public int getKeyForFunction(String function) {
        return functionKeyMap.getOrDefault(function, -1);
    }
    
    /**
     * 重新映射功能键
     */
    public void remapKey(String function, int keyCode) {
        functionKeyMap.put(function, keyCode);
    }
    
    /**
     * 检查按键是否按下
     */
    public boolean isKeyPressed(String function) {
        int keyCode = getKeyForFunction(function);
        return keyCode >= 0 && pressed.contains(keyCode);
    }
    
    /**
     * 直接检查按键码
     */
    public boolean isKeyCodePressed(int keyCode) {
        return keyCode >= 0 && pressed.contains(keyCode);
    }
    
    /**
     * 获取移动方向向量（使用 deltaTime 使移动与帧率无关）
     */
    public double[] getMovementVector(double baseSpeed, boolean isAccelerating, double deltaTime) {
        // 如果控制被禁用（任一来源持有锁），返回零向量
        if (isControlsDisabled()) {
            return new double[]{0, 0};
        }

        // 以 60 FPS 为基准计算速度缩放因子
        double timeScale = deltaTime * 60.0;
        double moveSpeed = isAccelerating ? baseSpeed * 2 : baseSpeed;
        double moveX = 0, moveY = 0;

        if (isKeyPressed("MOVE_LEFT")) moveX -= moveSpeed * timeScale;
        if (isKeyPressed("MOVE_RIGHT")) moveX += moveSpeed * timeScale;
        if (isKeyPressed("MOVE_UP")) moveY -= moveSpeed * timeScale;
        if (isKeyPressed("MOVE_DOWN")) moveY += moveSpeed * timeScale;

        return new double[]{moveX, moveY};
    }
    
    /**
     * 处理按键按下事件
     */
    public void handleKeyPressed(KeyEvent e) {
        pressed.add(e.getCode().ordinal());
        // 触发按键按下事件回调
        triggerKeyPressedEvents(e);
        // 触发 function-based handlers（动态匹配当前 keyCode）
        triggerFunctionKeyPressEvents(e);
    }

    /**
     * 处理按键释放事件
     */
    public void handleKeyReleased(KeyEvent e) {
        pressed.remove(e.getCode().ordinal());
        // 触发按键释放事件回调
        triggerKeyReleasedEvents(e);
        // 触发 function-based handlers（动态匹配当前 keyCode）
        triggerFunctionKeyReleaseEvents(e);
    }
    
    /**
     * 重置所有按键状态
     */
    public void resetAllKeys() {
        pressed.clear();
    }

    /**
     * 锁定操作，按<b>来源</b>记账。
     *
     * <p>为什么不能用一个 boolean：同时有好几个独立系统需要锁操作
     * （剧情层、弹窗、教程遮罩、致谢、地图切换……）。用单一标志时，
     * 任何一方的「解锁」都会把别人的锁一起解掉 ——
     * 例如教程遮罩每关一次就 enable 一次，直接把剧情层在章节开始时
     * 加的锁冲掉，于是视觉小说模式下按 WASD 还能走动。
     *
     * <p>改成按来源记账后，各系统互不干扰；同一来源重复加锁也只算一次，
     * 所以「重复 show 却只 hide 一次」也不会泄漏。
     */
    public static final Object LOCK_STORY = new Object();
    public static final Object LOCK_TUTORIAL = new Object();
    public static final Object LOCK_POPUP = new Object();
    public static final Object LOCK_CREDITS = new Object();
    public static final Object LOCK_MAP_TRANSITION = new Object();
    public static final Object LOCK_DEATH = new Object();
    /** 断网提示（全屏模态）持有；它还会顺手清掉「断开瞬间正按着」的按键状态。 */
    public static final Object LOCK_OFFLINE = new Object();
    /** 无来源调用（已废弃的 disableControls/enableControls）使用的兜底来源。 */
    public static final Object LOCK_SYSTEM = new Object();

    private final Set<Object> controlLocks = new LinkedHashSet<>();

    /**
     * 按来源锁定操作（幂等：同一来源重复调用只算一次）。
     *
     * @param owner 来源标识，用上面那些 {@code LOCK_*} 常量
     */
    public void lockControls(Object owner) {
        if (owner != null && controlLocks.add(owner)) {
            resetAllKeys(); // 刚进入锁定：清掉当前按键状态
        }
    }

    /** 按来源解锁。 */
    public void unlockControls(Object owner) {
        if (owner != null) {
            controlLocks.remove(owner);
        }
    }

    /**
     * 禁用用户控制（旧的无来源版本）。
     *
     * @deprecated 用 {@link #lockControls(Object)} —— 无来源的调用会与其它系统互相干扰。
     */
    @Deprecated
    public void disableControls() {
        lockControls(LOCK_SYSTEM);
    }

    /**
     * 启用用户控制（旧的无来源版本）。
     *
     * @deprecated 用 {@link #unlockControls(Object)}。
     */
    @Deprecated
    public void enableControls() {
        unlockControls(LOCK_SYSTEM);
    }

    /**
     * 检查控制是否被禁用（任一来源持有锁即视为禁用）。
     */
    public boolean isControlsDisabled() {
        return !controlLocks.isEmpty();
    }

    /**
     * 检查交互按钮是否被按下（单次触发）
     */
    public boolean isInteractPressed() {
        return isKeyCodePressed(INTERACT);
    }

    // ==================== 事件回调系统方法 ====================

    /**
     * 注册按键按下事件处理器（默认优先处理模式，不允许并发）
     * @param keyCode 按键码
     * @param handler 事件处理器
     */
    public void onKeyPressed(int keyCode, KeyEventHandler handler) {
        onKeyPressed(keyCode, handler, false);
    }

    /**
     * 注册按键按下事件处理器
     * @param keyCode 按键码
     * @param handler 事件处理器
     * @param allowConcurrent 是否允许与其他处理器同时处理（true=同时处理，false=优先处理）
     */
    public void onKeyPressed(int keyCode, KeyEventHandler handler, boolean allowConcurrent) {
        List<EventRegistration> handlers = keyPressedHandlers.computeIfAbsent(keyCode, k -> new ArrayList<>());
        handlers.add(new EventRegistration(handler, allowConcurrent));
    }

    /**
     * 注册按键释放事件处理器（默认优先处理模式，不允许并发）
     * @param keyCode 按键码
     * @param handler 事件处理器
     */
    public void onKeyReleased(int keyCode, KeyEventHandler handler) {
        onKeyReleased(keyCode, handler, false);
    }

    /**
     * 注册按键释放事件处理器
     * @param keyCode 按键码
     * @param handler 事件处理器
     * @param allowConcurrent 是否允许与其他处理器同时处理（true=同时处理，false=优先处理）
     */
    public void onKeyReleased(int keyCode, KeyEventHandler handler, boolean allowConcurrent) {
        List<EventRegistration> handlers = keyReleasedHandlers.computeIfAbsent(keyCode, k -> new ArrayList<>());
        handlers.add(new EventRegistration(handler, allowConcurrent));
    }

    /**
     * 使用功能名称注册按键按下事件处理器
     * @param function 功能名称（如"MOVE_UP"）
     * @param handler 事件处理器
     */
    public void onKeyPressed(String function, KeyEventHandler handler) {
        List<EventRegistration> handlers = functionKeyPressHandlers.computeIfAbsent(function, k -> new ArrayList<>());
        handlers.add(new EventRegistration(handler, false));
    }

    /**
     * 使用功能名称注册按键按下事件处理器
     * @param function 功能名称（如"MOVE_UP"）
     * @param handler 事件处理器
     * @param allowConcurrent 是否允许并发处理
     */
    public void onKeyPressed(String function, KeyEventHandler handler, boolean allowConcurrent) {
        List<EventRegistration> handlers = functionKeyPressHandlers.computeIfAbsent(function, k -> new ArrayList<>());
        handlers.add(new EventRegistration(handler, allowConcurrent));
    }

    /**
     * 使用功能名称注册按键释放事件处理器
     * @param function 功能名称（如"MOVE_UP"）
     * @param handler 事件处理器
     */
    public void onKeyReleased(String function, KeyEventHandler handler) {
        List<EventRegistration> handlers = functionKeyReleaseHandlers.computeIfAbsent(function, k -> new ArrayList<>());
        handlers.add(new EventRegistration(handler, false));
    }

    /**
     * 使用功能名称注册按键释放事件处理器
     * @param function 功能名称（如"MOVE_UP"）
     * @param handler 事件处理器
     * @param allowConcurrent 是否允许并发处理
     */
    public void onKeyReleased(String function, KeyEventHandler handler, boolean allowConcurrent) {
        List<EventRegistration> handlers = functionKeyReleaseHandlers.computeIfAbsent(function, k -> new ArrayList<>());
        handlers.add(new EventRegistration(handler, allowConcurrent));
    }

    /**
     * 使用KeyCode枚举注册按键按下事件处理器
     * @param keyCode KeyCode枚举
     * @param handler 事件处理器
     */
    public void onKeyPressed(KeyCode keyCode, KeyEventHandler handler) {
        onKeyPressed(keyCode.ordinal(), handler);
    }

    /**
     * 使用KeyCode枚举注册按键按下事件处理器
     * @param keyCode KeyCode枚举
     * @param handler 事件处理器
     * @param allowConcurrent 是否允许并发处理
     */
    public void onKeyPressed(KeyCode keyCode, KeyEventHandler handler, boolean allowConcurrent) {
        onKeyPressed(keyCode.ordinal(), handler, allowConcurrent);
    }

    /**
     * 使用KeyCode枚举注册按键释放事件处理器
     * @param keyCode KeyCode枚举
     * @param handler 事件处理器
     */
    public void onKeyReleased(KeyCode keyCode, KeyEventHandler handler) {
        onKeyReleased(keyCode.ordinal(), handler);
    }

    /**
     * 使用KeyCode枚举注册按键释放事件处理器
     * @param keyCode KeyCode枚举
     * @param handler 事件处理器
     * @param allowConcurrent 是否允许并发处理
     */
    public void onKeyReleased(KeyCode keyCode, KeyEventHandler handler, boolean allowConcurrent) {
        onKeyReleased(keyCode.ordinal(), handler, allowConcurrent);
    }

    /**
     * 取消注册按键按下事件处理器
     * @param keyCode 按键码
     * @param handler 要取消的处理器
     * @return 是否成功取消
     */
    public boolean removeKeyPressedHandler(int keyCode, KeyEventHandler handler) {
        List<EventRegistration> handlers = keyPressedHandlers.get(keyCode);
        if (handlers != null) {
            return handlers.removeIf(reg -> reg.handler == handler);
        }
        return false;
    }

    /**
     * 取消注册按键释放事件处理器
     * @param keyCode 按键码
     * @param handler 要取消的处理器
     * @return 是否成功取消
     */
    public boolean removeKeyReleasedHandler(int keyCode, KeyEventHandler handler) {
        List<EventRegistration> handlers = keyReleasedHandlers.get(keyCode);
        if (handlers != null) {
            return handlers.removeIf(reg -> reg.handler == handler);
        }
        return false;
    }

    /**
     * 使用功能名称取消注册按键按下事件处理器
     * @param function 功能名称
     * @param handler 要取消的处理器
     * @return 是否成功取消
     */
    public boolean removeKeyPressedHandler(String function, KeyEventHandler handler) {
        int keyCode = getKeyForFunction(function);
        if (keyCode >= 0) {
            return removeKeyPressedHandler(keyCode, handler);
        }
        return false;
    }

    /**
     * 使用功能名称取消注册按键释放事件处理器
     * @param function 功能名称
     * @param handler 要取消的处理器
     * @return 是否成功取消
     */
    public boolean removeKeyReleasedHandler(String function, KeyEventHandler handler) {
        int keyCode = getKeyForFunction(function);
        if (keyCode >= 0) {
            return removeKeyReleasedHandler(keyCode, handler);
        }
        return false;
    }

    /**
     * 清除指定按键的所有按下事件处理器
     * @param keyCode 按键码
     */
    public void clearKeyPressedHandlers(int keyCode) {
        keyPressedHandlers.remove(keyCode);
    }

    /**
     * 清除指定按键的所有释放事件处理器
     * @param keyCode 按键码
     */
    public void clearKeyReleasedHandlers(int keyCode) {
        keyReleasedHandlers.remove(keyCode);
    }

    /**
     * 清除所有按键事件处理器
     */
    public void clearAllKeyHandlers() {
        keyPressedHandlers.clear();
        keyReleasedHandlers.clear();
    }

    /**
     * 触发按键按下事件
     * @param event 按键事件
     */
    private void triggerKeyPressedEvents(KeyEvent event) {
        int keyCode = event.getCode().ordinal();
        List<EventRegistration> handlers = keyPressedHandlers.get(keyCode);
        
        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        // 检查防抖
        long currentTime = System.currentTimeMillis();
        Long lastPressTime = keyPressDebounceMap.get(keyCode);
        if (lastPressTime != null && currentTime - lastPressTime < DEFAULT_KEY_DEBOUNCE_TIME) {
            return;
        }
        keyPressDebounceMap.put(keyCode, currentTime);

        // 从后往前遍历，优先处理最后注册的处理器
        EventRegistration priorityHandler = null;
        List<EventRegistration> concurrentHandlers = new ArrayList<>();

        for (int i = handlers.size() - 1; i >= 0; i--) {
            EventRegistration reg = handlers.get(i);
            if (reg.allowConcurrent) {
                concurrentHandlers.add(reg);
            } else if (priorityHandler == null) {
                // 找到第一个（从后往前）不允许并发的处理器作为优先处理器
                priorityHandler = reg;
                break; // 找到优先处理器后停止查找
            }
        }

        // 先处理优先处理器
        if (priorityHandler != null) {
            priorityHandler.handler.handle(event);
        } else {
            // 没有优先处理器，处理所有允许并发的处理器
            for (EventRegistration reg : concurrentHandlers) {
                reg.handler.handle(event);
            }
        }
    }

    /**
     * 触发按键释放事件
     * @param event 按键事件
     */
    private void triggerKeyReleasedEvents(KeyEvent event) {
        int keyCode = event.getCode().ordinal();
        List<EventRegistration> handlers = keyReleasedHandlers.get(keyCode);
        
        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        // 从后往前遍历，优先处理最后注册的处理器
        EventRegistration priorityHandler = null;
        List<EventRegistration> concurrentHandlers = new ArrayList<>();

        for (int i = handlers.size() - 1; i >= 0; i--) {
            EventRegistration reg = handlers.get(i);
            if (reg.allowConcurrent) {
                concurrentHandlers.add(reg);
            } else if (priorityHandler == null) {
                priorityHandler = reg;
                break;
            }
        }

        // 先处理优先处理器
        if (priorityHandler != null) {
            priorityHandler.handler.handle(event);
        } else {
            for (EventRegistration reg : concurrentHandlers) {
                reg.handler.handle(event);
            }
        }

        // 清除该按键的防抖时间
        keyPressDebounceMap.remove(keyCode);
    }

    /**
     * 触发 function-based 按键按下事件（动态匹配当前 keyCode）
     */
    private void triggerFunctionKeyPressEvents(KeyEvent event) {
        int pressedCode = event.getCode().ordinal();
        for (Map.Entry<String, List<EventRegistration>> entry : functionKeyPressHandlers.entrySet()) {
            String function = entry.getKey();
            int functionKeyCode = getKeyForFunction(function);
            if (functionKeyCode == pressedCode) {
                List<EventRegistration> handlers = entry.getValue();
                EventRegistration priorityHandler = null;
                List<EventRegistration> concurrentHandlers = new ArrayList<>();
                for (int i = handlers.size() - 1; i >= 0; i--) {
                    EventRegistration reg = handlers.get(i);
                    if (reg.allowConcurrent) {
                        concurrentHandlers.add(reg);
                    } else if (priorityHandler == null) {
                        priorityHandler = reg;
                        break;
                    }
                }
                if (priorityHandler != null) {
                    priorityHandler.handler.handle(event);
                } else {
                    for (EventRegistration reg : concurrentHandlers) {
                        reg.handler.handle(event);
                    }
                }
            }
        }
    }

    /**
     * 触发 function-based 按键释放事件（动态匹配当前 keyCode）
     */
    private void triggerFunctionKeyReleaseEvents(KeyEvent event) {
        int pressedCode = event.getCode().ordinal();
        for (Map.Entry<String, List<EventRegistration>> entry : functionKeyReleaseHandlers.entrySet()) {
            String function = entry.getKey();
            int functionKeyCode = getKeyForFunction(function);
            if (functionKeyCode == pressedCode) {
                List<EventRegistration> handlers = entry.getValue();
                EventRegistration priorityHandler = null;
                List<EventRegistration> concurrentHandlers = new ArrayList<>();
                for (int i = handlers.size() - 1; i >= 0; i--) {
                    EventRegistration reg = handlers.get(i);
                    if (reg.allowConcurrent) {
                        concurrentHandlers.add(reg);
                    } else if (priorityHandler == null) {
                        priorityHandler = reg;
                        break;
                    }
                }
                if (priorityHandler != null) {
                    priorityHandler.handler.handle(event);
                } else {
                    for (EventRegistration reg : concurrentHandlers) {
                        reg.handler.handle(event);
                    }
                }
            }
        }
    }

    /**
     * 设置按键防抖时间
     * @param debounceTime 防抖时间（毫秒）
     */
    public void setKeyDebounceTime(long debounceTime) {
        // 可以根据需要实现全局或单个按键的防抖时间设置
    }

    /**
     * 获取指定按键的按下事件处理器数量
     * @param keyCode 按键码
     * @return 处理器数量
     */
    public int getKeyPressedHandlerCount(int keyCode) {
        List<EventRegistration> handlers = keyPressedHandlers.get(keyCode);
        return handlers != null ? handlers.size() : 0;
    }

    /**
     * 获取指定按键的释放事件处理器数量
     * @param keyCode 按键码
     * @return 处理器数量
     */
    public int getKeyReleasedHandlerCount(int keyCode) {
        List<EventRegistration> handlers = keyReleasedHandlers.get(keyCode);
        return handlers != null ? handlers.size() : 0;
    }
}
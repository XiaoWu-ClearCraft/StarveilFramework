package com.xiaowu.game.starveil.render.engine;

/**
 * 键盘事件抽象类
 */
public class KeyCodeEvent {
    public enum KeyCode {
        SPACE, ENTER, ESCAPE,
        A, B, C, D, E, F, G, H, I, J, K, L, M,
        N, O, P, Q, R, S, T, U, V, W, X, Y, Z,
        DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
        DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
        UP, DOWN, LEFT, RIGHT,
        UNKNOWN
    }

    private final KeyCode keyCode;
    private final char character;
    private boolean consumed = false;

    public KeyCodeEvent(KeyCode keyCode, char character) {
        this.keyCode = keyCode;
        this.character = character;
    }

    public KeyCode getKeyCode() { return keyCode; }
    public char getCharacter() { return character; }
    public void consume() { consumed = true; }
    public boolean isConsumed() { return consumed; }

    /**
     * 从按键字符创建事件
     */
    public static KeyCodeEvent fromChar(char c) {
        KeyCode code = mapCharToKeyCode(c);
        return new KeyCodeEvent(code, c);
    }

    private static KeyCode mapCharToKeyCode(char c) {
        if (c == ' ') return KeyCode.SPACE;
        if (c >= 'A' && c <= 'Z') return KeyCode.valueOf(String.valueOf(c));
        if (c >= 'a' && c <= 'z') return KeyCode.valueOf(String.valueOf(Character.toUpperCase(c)));
        if (c >= '0' && c <= '9') return KeyCode.valueOf("DIGIT_" + c);
        return KeyCode.UNKNOWN;
    }
}

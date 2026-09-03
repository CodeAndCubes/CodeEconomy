package com.mrleonardos.codeeconomy.client.settings;

/**
 * Угол экрана, в котором стоит показ.
 *
 * <p>
 * Имя из файла разбирается без учёта регистра, а непонятное значение считается заводским: опечатку в
 * своём файле игрок правит сам, а отрисовка не должна падать каждый кадр.
 */
public enum HudCorner {

    TOP_LEFT("top-left", true, true),
    TOP_RIGHT("top-right", true, false),
    BOTTOM_LEFT("bottom-left", false, true),
    BOTTOM_RIGHT("bottom-right", false, false);

    private final String id;
    private final boolean top;
    private final boolean left;

    HudCorner(String id, boolean top, boolean left) {
        this.id = id;
        this.top = top;
        this.left = left;
    }

    /** Имя, которое игрок пишет в своём файле. */
    public String id() {
        return id;
    }

    /** Считать отступ от верхнего края, а не от нижнего. */
    public boolean top() {
        return top;
    }

    /** Считать отступ от левого края, а не от правого. */
    public boolean left() {
        return left;
    }

    /** Угол по имени из файла; незнакомое имя даёт {@link #TOP_RIGHT}. */
    public static HudCorner of(String value) {
        if (value != null) {
            String trimmed = value.trim();
            for (HudCorner corner : values()) {
                if (corner.id.equalsIgnoreCase(trimmed)) {
                    return corner;
                }
            }
        }
        return TOP_RIGHT;
    }
}

package com.mrleonardos.codeeconomy.client.settings;

import com.mrleonardos.codecore.api.config.Comment;

/** Содержимое {@code config/code/economy/client/economy-client.toml}: как баланс выглядит у игрока. */
@Comment({ "Показ баланса на экране. Файл ваш: сервер сюда не заглядывает и ничего не навязывает.",
    "Показ работает, пока на сервере стоит CodeEconomy; без него баланса просто не будет." })
public final class ClientSettings {

    public static final float MIN_SCALE = 0.5F;
    public static final float MAX_SCALE = 2F;

    @Comment("Показывать ли баланс на экране.")
    public boolean enabled = true;

    @Comment("Угол экрана: top-left, top-right, bottom-left, bottom-right.")
    public String corner = HudCorner.TOP_RIGHT.id();

    @Comment("Отступ от края по горизонтали в пикселях интерфейса.")
    public int offsetX = 4;

    @Comment("Отступ от края по вертикали.")
    public int offsetY = 4;

    @Comment("Масштаб надписи. Ниже 0.5 и выше 2.0 не уйдёт.")
    public float scale = 1F;

    @Comment("Подписывать ли сумму названием валюты.")
    public boolean showCurrencyName = true;

    @Comment("Какая валюта показывается. Пусто означает валюту сервера по умолчанию.")
    public String currency = "";

    @Comment("Прятать ли показ, когда открыт любой экран.")
    public boolean hideWithGui = true;

    /** Угол из файла; непонятное значение считается заводским. */
    public HudCorner corner() {
        return HudCorner.of(corner);
    }

    /** Масштаб, зажатый границами: на нуле надпись пропала бы, на двадцати заняла бы весь экран. */
    public float scale() {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
}

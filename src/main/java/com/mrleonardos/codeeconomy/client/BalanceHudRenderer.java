package com.mrleonardos.codeeconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

import com.mrleonardos.codecore.api.client.ui.render.Draw;
import com.mrleonardos.codecore.api.client.ui.theme.Theme;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeeconomy.api.Amounts;
import com.mrleonardos.codeeconomy.client.settings.ClientSettings;
import com.mrleonardos.codeeconomy.client.settings.HudCorner;

/**
 * Рисует баланс в углу экрана.
 *
 * <p>
 * Масштаб применяется матрицей, поэтому угол и отступы считаются в координатах после него: иначе на
 * половинном масштабе надпись уехала бы к середине экрана вместо края.
 */
public final class BalanceHudRenderer {

    private static final int SHADOW_SPREAD = 2;
    private static final float SHADOW_ALPHA = 0.45F;
    private static final float PANEL_ALPHA = 0.55F;
    private static final float OPAQUE = 1F;
    private static final String SEPARATOR = " ";

    private final Minecraft minecraft;
    private final ClientBalance balance;
    private final ConfigFile<ClientSettings> settings;

    public BalanceHudRenderer(Minecraft minecraft, ClientBalance balance, ConfigFile<ClientSettings> settings) {
        this.minecraft = minecraft;
        this.balance = balance;
        this.settings = settings;
    }

    public void render(int screenWidth, int screenHeight) {
        ClientSettings options = settings.get();
        if (!options.enabled || !balance.known()) {
            return;
        }
        if (options.hideWithGui && minecraft.currentScreen != null) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        String text = text(options);
        int width = font.getStringWidth(text) + Theme.PADDING * 2;
        int height = font.FONT_HEIGHT + Theme.PADDING * 2;

        float scale = options.scale();
        int areaWidth = Math.round(screenWidth / scale);
        int areaHeight = Math.round(screenHeight / scale);
        HudCorner corner = options.corner();
        int left = corner.left() ? options.offsetX : areaWidth - options.offsetX - width;
        int top = corner.top() ? options.offsetY : areaHeight - options.offsetY - height;

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, OPAQUE);
        Draw.shadow(left, top, left + width, top + height, SHADOW_SPREAD, SHADOW_ALPHA);
        Draw.roundedRect(left, top, left + width, top + height, Theme.CORNER, Theme.SURFACE, PANEL_ALPHA);
        font.drawStringWithShadow(text, left + Theme.PADDING, top + Theme.PADDING, Draw.withAlpha(Theme.TEXT, OPAQUE));
        GL11.glPopMatrix();
    }

    private String text(ClientSettings options) {
        String amount = Amounts.formatAmount(balance.amount(), balance.decimals());
        return options.showCurrencyName ? amount + SEPARATOR + balance.currencyId() : amount;
    }
}

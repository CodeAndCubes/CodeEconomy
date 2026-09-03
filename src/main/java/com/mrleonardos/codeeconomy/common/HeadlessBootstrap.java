package com.mrleonardos.codeeconomy.common;

/**
 * Клиентская половина там, где клиента нет.
 *
 * <p>
 * Пара для {@code @SidedProxy} на выделенном сервере: рисовать ему нечем и незачем, а поле прокси без
 * второй половины не заполнится.
 */
public final class HeadlessBootstrap implements SideBootstrap {

    @Override
    public void install() {}
}

package com.mrleonardos.codeeconomy.internal.command;

import java.util.List;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Богатые ответы команд экономики: карточки, постраничные списки и отчёты.
 *
 * <p>
 * Сюда команды обращаются за тем единственным, чего им не сделать самим: карточки собираются кирпичами
 * ядра, которым нужен тип игры, а команды проверяются тестом без запуска Minecraft. Выбирать тут нечего
 * и незачем: это мост между слоями, как шов мутаций, а не точка расширения.
 */
public interface EconomyPresents {

    /**
     * Список веток {@code /eco}, доступных отправителю по правам.
     *
     * <p>
     * Команды уже отфильтровали ветки по нодам, список пуст когда нод нет вовсе.
     */
    void branches(CommandContext context, List<CommandNode> visible);

    /** Карточка баланса: свой счёт или чужой, поле лимитов и кнопки идут по правам отправителя. */
    void balance(CommandContext context, UUID player, boolean own, CurrencyRecord currency, long amount);

    /** Топ по валюте страницей: весь топ одним списком, страницы нарезает показ. */
    void top(CommandContext context, CurrencyRecord currency, List<BalanceEntry> entries, int page);

    /** Операции игрока страницей: свежие раньше, записи уже отобраны по игроку. */
    void history(CommandContext context, UUID player, boolean own, List<TransactionRecord> records, int page);

    /** Отчёт импорта балансов: сводка и построчные пояснения из обслуживания. */
    void importReport(CommandContext context, boolean apply, long moved, long rejected,
        List<MaintenanceOutcome.Row> rows);
}

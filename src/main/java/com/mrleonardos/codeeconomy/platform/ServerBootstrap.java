package com.mrleonardos.codeeconomy.platform;

import java.nio.file.Path;
import java.nio.file.Paths;

import net.minecraftforge.common.DimensionManager;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeeconomy.CodeEconomyMod;
import com.mrleonardos.codeeconomy.api.EconomyApi;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.common.EconomyBridge;
import com.mrleonardos.codeeconomy.common.ServerInstaller;
import com.mrleonardos.codeeconomy.internal.EconomyBootstrap;
import com.mrleonardos.codeeconomy.internal.command.EconomyCommands;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.hud.BalanceHud;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Серверная половина: деньги, команды и журнал.
 *
 * <p>
 * Создаётся по имени класса из общей части, поэтому работает одинаково и на выделенном сервере, и во
 * встроенном, который поднимается в одиночной игре. В клиентском jar этого класса нет, и тогда
 * серверная половина просто не поднимается.
 */
public final class ServerBootstrap implements ServerInstaller {

    /** Показ баланса только смотрит, поэтому в цепочке слушателей стоит последним. */
    private static final int HUD_PRIORITY = 1000;

    private final MainThread mainThread = new MainThread();
    private final ServerClock clock = new ServerClock();
    private final CorePlayerLookup lookup = new CorePlayerLookup();

    private EventDispatcher events;
    private EconomyBootstrap bootstrap;
    private PlatformLifecycle lifecycle;

    @Override
    public void init() {
        events = new EventDispatcher(CodeEconomyMod.LOG);
        EconomyApi.install(
            () -> CodeApi.services()
                .require(EconomyService.class),
            events);
        bootstrap = new EconomyBootstrap(
            CodeApi.configs(),
            CodeApi.scheduler(),
            mainThread,
            clock,
            System::currentTimeMillis,
            lookup,
            events,
            CodeEconomyMod.LOG);
        bootstrap.declare(CodeApi.adapters());
    }

    /**
     * Владельца роли ядро выбирает в конце своей постинициализации, а команды отдаёт стартующему серверу
     * в своём обработчике {@code FMLServerStarting}, который идёт раньше нашего. Поэтому вопрос о
     * владельце и вся сборка стоят здесь: раньше ответа ещё нет, позже команды уже отданы.
     */
    @Override
    public void postInit() {
        EconomyApi.freeze();
        bootstrap.start(CodeApi.adapters(), this::takeTheRole);
    }

    @Override
    public void serverStarting() {
        if (lifecycle == null) {
            return;
        }
        mainThread.attach(Thread.currentThread());
        lifecycle.onServerStart();
    }

    @Override
    public void serverStopping() {
        if (lifecycle == null) {
            return;
        }
        lifecycle.onServerStop();
    }

    /** Роль осталась за нами: корни команд, подписки и фоновый писатель. */
    private void takeTheRole(LedgerService service) {
        NameResolver names = new NameResolver(
            () -> service.ledger()
                .state()
                .accounts());
        EconomyCommands commands = new EconomyCommands(
            service,
            new PlatformMutations(service),
            new PlatformMaintenance(service, bootstrap.limits(), ServerBootstrap::serverRoot, CodeEconomyMod.LOG),
            new PlatformArguments(names, service),
            new SenderSubjects(
                () -> CodeApi.services()
                    .require(PermissionService.class),
                names),
            bootstrap.pageSize());
        commands.register(CodeApi.commands());

        BalanceHud hud = new BalanceHud(service, lookup, new BalanceSender(), CodeEconomyMod.LOG);
        events.register(HUD_PRIORITY, hud);
        EconomyBridge.server(new BalanceRequests(hud));

        lifecycle = new PlatformLifecycle(service, hud);
        FMLCommonHandler.instance()
            .bus()
            .register(
                new ForgeLifecycle(
                    lifecycle,
                    service,
                    clock,
                    bootstrap.config()
                        .autosaveTicks()));
    }

    private static Path serverRoot() {
        java.io.File root = DimensionManager.getCurrentSaveRootDirectory();
        return root == null ? Paths.get(".") : Paths.get(root.toURI());
    }
}

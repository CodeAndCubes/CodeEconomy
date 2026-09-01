package com.mrleonardos.codeeconomy.platform;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeeconomy.Tags;
import com.mrleonardos.codeeconomy.api.EconomyApi;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.internal.EconomyBootstrap;
import com.mrleonardos.codeeconomy.internal.command.EconomyCommands;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = "codeeconomy",
    name = "CodeEconomy",
    version = Tags.VERSION,
    dependencies = "required-after:codecore",
    acceptableRemoteVersions = "*")
public final class CodeEconomyMod {

    public static final Logger LOG = LogManager.getLogger("CodeEconomy");

    private final MainThread mainThread = new MainThread();
    private final ServerClock clock = new ServerClock();

    private EconomyBootstrap bootstrap;
    private PlatformLifecycle lifecycle;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodeEconomy {} is starting up", Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        EventDispatcher events = new EventDispatcher(LOG);
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
            new CorePlayerLookup(),
            events,
            LOG);
        bootstrap.declare(CodeApi.adapters());
    }

    /**
     * Владельца роли ядро выбирает в конце своей постинициализации, а команды отдаёт стартующему серверу
     * в своём обработчике {@code FMLServerStarting}, который идёт раньше нашего. Поэтому вопрос о
     * владельце и вся сборка стоят здесь: раньше ответа ещё нет, позже команды уже отданы.
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        EconomyApi.freeze();
        if (!bootstrap.decide(CodeApi.adapters())) {
            return;
        }
        LedgerService service = bootstrap.service();
        NameResolver names = new NameResolver(
            () -> service.ledger()
                .state()
                .accounts());
        EconomyCommands commands = new EconomyCommands(
            service,
            new PlatformMutations(service),
            new PlatformMaintenance(service, bootstrap.limits(), CodeEconomyMod::serverRoot, LOG),
            new PlatformArguments(names, service),
            new SenderSubjects(
                () -> CodeApi.services()
                    .require(PermissionService.class),
                names),
            bootstrap.pageSize());
        commands.register(CodeApi.commands());

        lifecycle = new PlatformLifecycle(service);
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

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (lifecycle == null) {
            return;
        }
        mainThread.attach(Thread.currentThread());
        lifecycle.onServerStart();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (lifecycle == null) {
            return;
        }
        lifecycle.onServerStop();
    }

    private static Path serverRoot() {
        java.io.File root = net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory();
        return root == null ? Paths.get(".") : Paths.get(root.toURI());
    }
}

package com.mrleonardos.codeeconomy.platform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.IntSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.Tags;
import com.mrleonardos.codeeconomy.api.EconomyApi;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.EconomySettings;
import com.mrleonardos.codeeconomy.internal.command.EconomyCommands;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;
import com.mrleonardos.codeeconomy.internal.store.Currencies;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

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

    private ConfigFile<EconomySettings> settings;
    private LedgerService service;
    private PlatformLifecycle lifecycle;
    private ServerClock clock;
    private MainThread mainThread = new MainThread();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodeEconomy {} is starting up", Tags.VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ConfigService configs = CodeApi.configs();
        Scheduler scheduler = CodeApi.scheduler();

        settings = configs.open(EconomySettings.spec());
        EconomySettings config = settings.get();
        EconomyLimits limits = config.ceilings(LOG);
        List<CurrencyRecord> currencies = Currencies.load(
            configs.open(Currencies.spec())
                .get(),
            limits,
            LOG);

        clock = new ServerClock();
        service = LedgerService.create(
            config,
            currencies,
            configs.open(JsonEconomyStore.spec()),
            scheduler,
            mainThread,
            clock,
            System::currentTimeMillis,
            new CorePlayerLookup(),
            LOG);

        EconomyApi.install(
            () -> CodeApi.services()
                .require(EconomyService.class),
            service.ledger()
                .events());
        ServicePriority priority = config.priority(LOG);
        ServiceBridge.register(service, priority);

        NameResolver names = new NameResolver(
            () -> service.ledger()
                .state()
                .accounts());
        EconomyCommands commands = new EconomyCommands(
            service,
            new PlatformMutations(service),
            new PlatformMaintenance(service, limits, CodeEconomyMod::serverRoot, LOG),
            new PlatformArguments(names, service),
            new SenderSubjects(
                () -> CodeApi.services()
                    .require(PermissionService.class),
                names),
            pageSize());
        commands.register(CodeApi.commands());

        lifecycle = new PlatformLifecycle(service);
        FMLCommonHandler.instance()
            .bus()
            .register(new ForgeLifecycle(lifecycle, service, clock, config.autosaveTicks()));

        LOG.info(
            "EconomyService is offered by {} with weight {}, storage provider from config is {}",
            LedgerService.IMPLEMENTATION,
            priority,
            config.provider());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        EconomyApi.freeze();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        mainThread.attach(Thread.currentThread());
        lifecycle.onServerStart();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        lifecycle.onServerStop();
    }

    private static Path serverRoot() {
        java.io.File root = net.minecraftforge.common.DimensionManager.getCurrentSaveRootDirectory();
        return root == null ? Paths.get(".") : Paths.get(root.toURI());
    }

    private IntSupplier pageSize() {
        return () -> settings.get()
            .pageSize();
    }
}

package com.mrleonardos.codeeconomy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.common.ServerInstaller;
import com.mrleonardos.codeeconomy.common.SideParts;
import com.mrleonardos.codeeconomy.network.EconomyPackets;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

/**
 * Точка входа мода.
 *
 * <p>
 * Здесь только то, что общее для обеих сторон: логгер и передача фаз FML половинам. Деньги живут в
 * серверной половине, показ баланса в клиентской, и обе подставляются по имени класса, поэтому любую
 * из них можно вырезать из сборки.
 *
 * <p>
 * {@code acceptableRemoteVersions = "*"} остаётся: сервер обязан пускать игрока без клиентской части,
 * тот просто не увидит показа.
 */
@Mod(
    modid = EconomyConstants.MODID,
    name = EconomyConstants.MOD_NAME,
    version = Tags.VERSION,
    dependencies = EconomyConstants.DEPENDENCIES,
    acceptableRemoteVersions = "*")
public final class CodeEconomyMod {

    public static final Logger LOG = LogManager.getLogger(EconomyConstants.MOD_NAME);

    private ServerInstaller server;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("CodeEconomy {} is starting up", Tags.VERSION);
        EconomyPackets.register();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        server = SideParts.serverInstaller();
        if (server == null) {
            LOG.info("Server side is not present in this build, running as a client only");
            return;
        }
        server.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (server != null) {
            server.postInit();
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (server != null) {
            server.serverStarting();
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (server != null) {
            server.serverStopping();
        }
    }
}

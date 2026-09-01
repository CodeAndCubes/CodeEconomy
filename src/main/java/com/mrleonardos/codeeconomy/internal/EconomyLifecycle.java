package com.mrleonardos.codeeconomy.internal;

import java.util.UUID;

public interface EconomyLifecycle {

    void onServerStart();

    void onServerStop();

    void onJoin(UUID player, String name);

    void onQuit(UUID player);
}

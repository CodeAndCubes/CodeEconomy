package com.mrleonardos.codeeconomy.platform;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeeconomy.api.EconomyService;

final class ServiceBridge {

    private ServiceBridge() {}

    static void register(EconomyService implementation) {
        CodeApi.services()
            .register(EconomyService.class, implementation, ServicePriority.BUILTIN);
    }
}

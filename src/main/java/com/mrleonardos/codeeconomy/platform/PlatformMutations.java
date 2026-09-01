package com.mrleonardos.codeeconomy.platform;

import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.command.EconomyMutations;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

final class PlatformMutations implements EconomyMutations {

    private final LedgerService service;

    PlatformMutations(LedgerService service) {
        this.service = service;
    }

    @Override
    public TransferResult apply(TransferRequest request, ChangeCause cause, boolean floorBypass) {
        return service.execute(request, cause, floorBypass);
    }
}

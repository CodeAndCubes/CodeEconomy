package com.mrleonardos.codeeconomy.internal.command;

import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;

public interface EconomyMutations {

    TransferResult apply(TransferRequest request, ChangeCause cause);
}

package com.liteworkflow.ai.rag;

import java.util.List;
import java.util.UUID;

public interface RagIndexStore {

    Claim claim(RagSourceEvent event, boolean redelivered);

    FinalizeResult finalizeVersion(RagSourceEvent event, List<UUID> vectorIds);

    void markFailed(UUID eventId, Throwable failure);

    enum Claim { PROCESS, DUPLICATE, STALE, EXHAUSTED }
    enum FinalizeResult { ACTIVATED, STALE }
}

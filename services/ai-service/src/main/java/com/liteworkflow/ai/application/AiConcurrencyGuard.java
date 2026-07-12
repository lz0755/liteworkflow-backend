package com.liteworkflow.ai.application;

import com.liteworkflow.ai.config.AiProperties;
import com.liteworkflow.common.core.error.BizException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class AiConcurrencyGuard {

    private final Semaphore permits;
    private final AiProperties properties;

    public AiConcurrencyGuard(AiProperties properties) {
        this.properties = properties;
        this.permits = new Semaphore(properties.getMaxConcurrentRequests(), true);
    }

    public Permit acquire() {
        try {
            if (!permits.tryAcquire(properties.getConcurrencyAcquireTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BizException(AiErrorCode.CONCURRENCY_LIMIT);
            }
            return new Permit(permits);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(AiErrorCode.CONCURRENCY_LIMIT);
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!closed) {
                semaphore.release();
                closed = true;
            }
        }
    }
}

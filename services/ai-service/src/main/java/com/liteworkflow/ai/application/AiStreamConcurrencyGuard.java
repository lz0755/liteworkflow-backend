package com.liteworkflow.ai.application;

import com.liteworkflow.ai.config.AiProperties;
import com.liteworkflow.common.core.error.BizException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Separate concurrency bulkhead for long-lived streaming requests. */
@Component
public class AiStreamConcurrencyGuard {

    private final Semaphore permits;
    private final AiProperties properties;

    public AiStreamConcurrencyGuard(AiProperties properties) {
        this.properties = properties;
        this.permits = new Semaphore(properties.getMaxConcurrentStreams(), true);
    }

    public Permit acquire() {
        try {
            if (!permits.tryAcquire(
                    properties.getStreamConcurrencyAcquireTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new BizException(AiErrorCode.STREAM_CONCURRENCY_LIMIT);
            }
            return new Permit(permits);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(AiErrorCode.STREAM_CONCURRENCY_LIMIT);
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}

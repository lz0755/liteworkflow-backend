package com.liteworkflow.common.core.async;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/** Copies MDC into worker threads and restores any worker context afterwards. */
public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> submittingContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            try {
                replaceContext(submittingContext);
                runnable.run();
            } finally {
                replaceContext(workerContext);
            }
        };
    }

    private static void replaceContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}

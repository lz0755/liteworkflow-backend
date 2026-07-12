package com.liteworkflow.common.mq.trace;

import com.liteworkflow.common.core.trace.MdcScope;
import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.common.core.trace.TraceIds;
import com.liteworkflow.common.mq.event.EventHeaders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;

/** Restores message correlation headers in MDC for the complete listener/retry invocation. */
public final class RabbitMdcInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Message message = findMessage(invocation.getArguments());
        if (message == null) {
            return invocation.proceed();
        }

        Map<String, String> context = new LinkedHashMap<>();
        context.put(
                TraceConstants.TRACE_ID,
                TraceIds.resolve(header(message, EventHeaders.TRACE_ID)));
        putSafe(context, TraceConstants.EVENT_ID, header(message, EventHeaders.EVENT_ID));
        putSafe(context, TraceConstants.WORKSPACE_ID, header(message, EventHeaders.WORKSPACE_ID));
        putSafe(context, TraceConstants.PROJECT_ID, header(message, EventHeaders.PROJECT_ID));
        try (MdcScope ignored = MdcScope.open(context)) {
            return invocation.proceed();
        }
    }

    private static Message findMessage(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Message message) {
                return message;
            }
            if (argument instanceof List<?> messages) {
                for (Object candidate : messages) {
                    if (candidate instanceof Message message) {
                        return message;
                    }
                }
            }
        }
        return null;
    }

    private static String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return value == null ? null : value.toString();
    }

    private static void putSafe(Map<String, String> context, String key, String candidate) {
        String safe = TraceIds.safeOrNull(candidate);
        if (safe != null) {
            context.put(key, safe);
        }
    }
}

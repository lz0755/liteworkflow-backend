package com.liteworkflow.common.mq.trace;

import java.util.Arrays;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.BaseRabbitListenerContainerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** Adds MDC advice without replacing Spring Boot's finite-retry advice chain. */
public final class RabbitMdcBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof BaseRabbitListenerContainerFactory<?> factory)) {
            return bean;
        }
        Advice[] existing = factory.getAdviceChain();
        if (existing != null && Arrays.stream(existing).anyMatch(RabbitMdcInterceptor.class::isInstance)) {
            return bean;
        }
        int existingLength = existing == null ? 0 : existing.length;
        Advice[] combined = new Advice[existingLength + 1];
        combined[0] = new RabbitMdcInterceptor();
        if (existingLength > 0) {
            System.arraycopy(existing, 0, combined, 1, existingLength);
        }
        factory.setAdviceChain(combined);
        return bean;
    }
}

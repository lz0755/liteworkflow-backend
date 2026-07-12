package com.liteworkflow.common.mq.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;

class RabbitMdcBeanPostProcessorTest {

    @Test
    void prependsMdcAdviceWithoutReplacingRetryAdviceOrDuplicatingItself() {
        var factory = new SimpleRabbitListenerContainerFactory();
        MethodInterceptor retryAdvice = invocation -> invocation.proceed();
        factory.setAdviceChain(retryAdvice);
        var processor = new RabbitMdcBeanPostProcessor();

        processor.postProcessAfterInitialization(factory, "rabbitListenerContainerFactory");
        processor.postProcessAfterInitialization(factory, "rabbitListenerContainerFactory");

        assertThat(factory.getAdviceChain()).hasSize(2);
        assertThat(factory.getAdviceChain()[0]).isInstanceOf(RabbitMdcInterceptor.class);
        assertThat(factory.getAdviceChain()[1]).isSameAs(retryAdvice);
    }
}

package com.liteworkflow.infra.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExportAmqpConfigurationTest {

    @Test
    void statusQueueDeadLettersPoisonOutcomes() {
        var queue = new ExportAmqpConfiguration().exportStatusQueue();

        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", ExportAmqpConfiguration.STATUS_DLX)
                .containsEntry("x-dead-letter-routing-key", ExportAmqpConfiguration.STATUS_DLQ);
    }
}

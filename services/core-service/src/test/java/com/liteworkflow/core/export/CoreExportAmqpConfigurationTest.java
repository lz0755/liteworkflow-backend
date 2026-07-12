package com.liteworkflow.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreExportAmqpConfigurationTest {

    @Test
    void requestedQueueHasFiniteFailureDestination() {
        var configuration = new CoreExportAmqpConfiguration();
        var queue = configuration.exportRequestQueue();

        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", CoreExportAmqpConfiguration.REQUEST_DLX)
                .containsEntry("x-dead-letter-routing-key", CoreExportAmqpConfiguration.REQUEST_DLQ);

        CoreExportProperties properties = new CoreExportProperties();
        assertThat(properties.getConsumer().getMaxAttempts()).isBetween(1, 10);
    }
}

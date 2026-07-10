package com.liteworkflow.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.core.trace.TraceConstants;
import com.liteworkflow.identity.infrastructure.PasswordResetMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "debug=false")
class IdentityServiceSmokeTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PasswordResetMailSender passwordResetMailSender;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void startsAndReportsHealthyWithTraceHeader() {
        assertHealthy("identity-smoke-trace");
        assertThat(passwordResetMailSender).isNotNull();
        assertThat(rabbitTemplate.isMandatoryFor(MessageBuilder.withBody(new byte[0]).build())).isTrue();
        assertThat(rabbitTemplate.getConnectionFactory()).isInstanceOfSatisfying(
                CachingConnectionFactory.class, connectionFactory -> {
                    assertThat(connectionFactory.isPublisherReturns()).isTrue();
                    assertThat(new DirectFieldAccessor(connectionFactory).getPropertyValue("confirmType"))
                            .isEqualTo(CachingConnectionFactory.ConfirmType.CORRELATED);
                });
    }

    private void assertHealthy(String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TraceConstants.TRACE_ID_HEADER, traceId);
        ResponseEntity<String> response = http.exchange(
                "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(TraceConstants.TRACE_ID_HEADER)).isEqualTo(traceId);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}

package com.liteworkflow.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("liteworkflow.gateway")
public class GatewayProperties {

    private final Services services = new Services();
    private final OpenApi openapi = new OpenApi();
    private final Cors cors = new Cors();
    private final RateLimits rateLimits = new RateLimits();
    private final Timeouts timeouts = new Timeouts();

    public Services getServices() {
        return services;
    }

    public OpenApi getOpenapi() {
        return openapi;
    }

    public Cors getCors() {
        return cors;
    }

    public RateLimits getRateLimits() {
        return rateLimits;
    }

    public Timeouts getTimeouts() {
        return timeouts;
    }

    public static final class Services {
        private URI identity = URI.create("http://localhost:8081");
        private URI core = URI.create("http://localhost:8082");
        private URI infra = URI.create("http://localhost:8083");
        private URI ai = URI.create("http://localhost:8084");

        public URI getIdentity() { return identity; }
        public void setIdentity(URI identity) { this.identity = identity; }
        public URI getCore() { return core; }
        public void setCore(URI core) { this.core = core; }
        public URI getInfra() { return infra; }
        public void setInfra(URI infra) { this.infra = infra; }
        public URI getAi() { return ai; }
        public void setAi(URI ai) { this.ai = ai; }
    }

    public static final class OpenApi {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static final class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public static final class RateLimits {
        private final Limit login = new Limit(10, 600, 60);
        private final Limit userSearch = new Limit(60, 3600, 60);
        private final Limit ai = new Limit(30, 1800, 60);
        private final Limit sse = new Limit(5, 300, 60);

        public Limit getLogin() { return login; }
        public Limit getUserSearch() { return userSearch; }
        public Limit getAi() { return ai; }
        public Limit getSse() { return sse; }
    }

    public static final class Timeouts {
        private Duration aiResponseTimeout = Duration.ofSeconds(75);

        public Duration getAiResponseTimeout() { return aiResponseTimeout; }
        public void setAiResponseTimeout(Duration aiResponseTimeout) {
            if (aiResponseTimeout == null || aiResponseTimeout.isZero() || aiResponseTimeout.isNegative()) {
                throw new IllegalArgumentException("AI response timeout must be positive");
            }
            this.aiResponseTimeout = aiResponseTimeout;
        }
    }

    public static final class Limit {
        private int replenishRate;
        private int burstCapacity;
        private int requestedTokens;

        public Limit() {
        }

        Limit(int replenishRate, int burstCapacity, int requestedTokens) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
            this.requestedTokens = requestedTokens;
        }

        public int getReplenishRate() { return replenishRate; }
        public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }
        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
        public int getRequestedTokens() { return requestedTokens; }
        public void setRequestedTokens(int requestedTokens) { this.requestedTokens = requestedTokens; }
    }
}

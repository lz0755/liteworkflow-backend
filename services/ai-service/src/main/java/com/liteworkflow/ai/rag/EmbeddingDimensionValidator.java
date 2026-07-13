package com.liteworkflow.ai.rag;

import com.liteworkflow.ai.config.RagProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "liteworkflow.ai.rag", name = {"enabled", "startup-validation"},
        havingValue = "true", matchIfMissing = true)
public class EmbeddingDimensionValidator implements SmartInitializingSingleton {

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbc;
    private final RagProperties properties;

    public EmbeddingDimensionValidator(
            EmbeddingModel embeddingModel, JdbcTemplate jdbc, RagProperties properties) {
        this.embeddingModel = embeddingModel;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        int configured = properties.getEmbeddingDimensions();
        float[] actual = embeddingModel.embed("liteworkflow embedding dimension startup probe");
        if (actual == null || actual.length != configured) {
            throw new IllegalStateException("Embedding API dimension "
                    + (actual == null ? "unknown" : actual.length)
                    + " does not match configured dimension " + configured);
        }
        String databaseType = jdbc.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'rag' AND c.relname = 'vector_store'
                  AND a.attname = 'embedding' AND NOT a.attisdropped
                """, String.class);
        String expected = "vector(" + configured + ")";
        if (!expected.equalsIgnoreCase(databaseType)) {
            throw new IllegalStateException(
                    "Database embedding column " + databaseType + " does not match configured " + expected);
        }
    }
}

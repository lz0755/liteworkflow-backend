package com.liteworkflow.ai.rag;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liteworkflow.ai.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

class EmbeddingDimensionValidatorTest {

    @Test
    void actualEmbeddingApiDimensionMismatchStopsStartup() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagProperties properties = properties(4);
        when(model.embed("liteworkflow embedding dimension startup probe"))
                .thenReturn(new float[] {1, 2, 3});

        assertThatThrownBy(() -> new EmbeddingDimensionValidator(model, jdbc, properties)
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding API dimension 3")
                .hasMessageContaining("configured dimension 4");
    }

    @Test
    void databaseColumnDimensionMismatchStopsStartup() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RagProperties properties = properties(4);
        when(model.embed("liteworkflow embedding dimension startup probe"))
                .thenReturn(new float[] {1, 2, 3, 4});
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(String.class))).thenReturn("vector(3)");

        assertThatThrownBy(() -> new EmbeddingDimensionValidator(model, jdbc, properties)
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector(3)")
                .hasMessageContaining("vector(4)");
    }

    private static RagProperties properties(int dimensions) {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingModel("test-embedding");
        properties.setEmbeddingDimensions(dimensions);
        return properties;
    }
}

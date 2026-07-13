package com.liteworkflow.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import com.liteworkflow.ai.config.RagProperties;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DocumentTokenChunkerTest {

    @Test
    void documentUsesConfiguredTokenWindowsAndOverlap() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingModel("test");
        properties.setEmbeddingDimensions(3);
        properties.setDocumentChunkTokens(650);
        properties.setDocumentChunkOverlapTokens(100);
        DocumentTokenChunker chunker = new DocumentTokenChunker(properties);
        String text = IntStream.range(0, 1800).mapToObj(index -> "word" + index)
                .collect(java.util.stream.Collectors.joining(" "));

        List<String> chunks = chunker.split(text);
        var encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(encoding.countTokens(chunk)).isLessThanOrEqualTo(650));
        var first = encoding.encode(chunks.get(0));
        var second = encoding.encode(chunks.get(1));
        assertThat(IntStream.range(0, 100).map(index -> first.get(first.size() - 100 + index)).toArray())
                .containsExactly(IntStream.range(0, 100).map(second::get).toArray());
    }
}

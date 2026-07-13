package com.liteworkflow.ai.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import com.liteworkflow.ai.config.RagProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentTokenChunker {

    private final RagProperties properties;
    private final Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    public DocumentTokenChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        IntArrayList tokens = encoding.encode(text.strip());
        int chunkSize = properties.getDocumentChunkTokens();
        int overlap = properties.getDocumentChunkOverlapTokens();
        if (tokens.size() <= chunkSize) return List.of(text.strip());

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += chunkSize - overlap) {
            int end = Math.min(tokens.size(), start + chunkSize);
            IntArrayList window = new IntArrayList(end - start);
            for (int index = start; index < end; index++) window.add(tokens.get(index));
            // Preserve boundary whitespace: trimming changes BPE tokenization and would make the
            // configured overlap smaller than the actual token window.
            String chunk = encoding.decode(window);
            if (!chunk.isBlank()) chunks.add(chunk);
            if (chunks.size() > properties.getMaxDocumentChunks()) {
                throw new IllegalArgumentException("Document produces too many RAG chunks");
            }
            if (end == tokens.size()) break;
        }
        return List.copyOf(chunks);
    }
}

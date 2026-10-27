package com.ecomassistant.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic bag-of-words embedding model used in tests so similarity assertions are
 * meaningful without calling an external embedding API.
 *
 * <p>Texts are tokenized, each token is hashed into one of {@value DIMENSIONS} dimensions
 * with a sign derived from the hash, and the resulting vector is L2-normalized. Texts
 * that share words therefore have a positive cosine similarity, while unrelated texts are
 * (near) orthogonal.
 */
@Component
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 256;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> results = new ArrayList<>(request.getInstructions().size());
        int index = 0;
        for (String text : request.getInstructions()) {
            results.add(new Embedding(embed(text), index++));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String token : tokenize(text)) {
            int hash = token.hashCode();
            int dimension = Math.floorMod(hash, DIMENSIONS);
            float sign = ((hash >>> 16) & 1) == 0 ? 1.0f : -1.0f;
            vector[dimension] += sign;
        }

        double norm = 0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        if (norm == 0) {
            return vector;
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] words = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>(words.length);
        for (String word : words) {
            if (!word.isEmpty()) {
                tokens.add(word);
            }
        }
        return tokens;
    }
}

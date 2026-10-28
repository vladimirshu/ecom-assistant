package com.ecomassistant.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Joins retrieved documents preserving retrieval order and deduplicating by document id
 * (first occurrence wins).
 *
 * <p>Spring AI's default {@code ConcatenationDocumentJoiner} re-sorts the documents by
 * score in descending order, which destroys the lowest-price ordering the retriever
 * produces for {@code ecom_sort_by_lowest_price}. The product-search pipeline defines
 * response order purely by retrieval, so the join step must not reorder.
 */
public class OrderPreservingDocumentJoiner implements DocumentJoiner {

    @Override
    public List<Document> join(Map<Query, List<List<Document>>> documentsForQuery) {
        List<Document> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (List<List<Document>> perQuery : documentsForQuery.values()) {
            for (List<Document> documents : perQuery) {
                for (Document document : documents) {
                    if (seen.add(document.getId())) {
                        result.add(document);
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}

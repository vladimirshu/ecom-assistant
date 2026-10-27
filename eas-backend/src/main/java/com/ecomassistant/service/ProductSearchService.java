package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.entity.Product;
import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.guardrails.QueryAnalysisValidator;
import com.ecomassistant.guardrails.QueryAnalyzer;
import com.ecomassistant.guardrails.RetrievalParametersMapper;
import com.ecomassistant.retrieval.QdrantRecommendDocumentRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final QueryAnalyzer queryAnalyzer;
    private final QueryAnalysisValidator queryAnalysisValidator;
    private final RetrievalParametersMapper retrievalParametersMapper;
    private final QdrantRecommendDocumentRetriever retriever;

    public ProductSearchService(QueryAnalyzer queryAnalyzer,
            QueryAnalysisValidator queryAnalysisValidator,
            RetrievalParametersMapper retrievalParametersMapper,
            QdrantRecommendDocumentRetriever retriever) {
        this.queryAnalyzer = queryAnalyzer;
        this.queryAnalysisValidator = queryAnalysisValidator;
        this.retrievalParametersMapper = retrievalParametersMapper;
        this.retriever = retriever;
    }

    /**
     * Analyzes the request, validates the analysis, maps it to retrieval parameters and
     * delegates to the retriever. Throws {@link com.ecomassistant.guardrails.GuardrailRejectedException}
     * for rejected requests and {@link com.ecomassistant.exception.IllegalAssistantResponseException}
     * for semantically invalid analyses.
     */
    public List<Product> searchProducts(String message, String conversationId) {
        QueryAnalysis analysis = queryAnalyzer.analyze(message, conversationId);
        queryAnalysisValidator.validate(analysis);

        log.info("Searching products with query: '{}', negatives: {}, price: {}, sortByLowestPrice: {}",
                analysis.searchQuery(), analysis.negativeExamples(), analysis.priceConstraint(),
                analysis.sortByLowestPrice());

        Map<String, Object> context = retrievalParametersMapper.toContext(analysis);
        Query query = Query.builder().text(analysis.searchQuery()).context(context).build();

        List<Document> foundDocuments = retriever.retrieve(query);
        List<Product> foundProducts = foundDocuments.stream().map(VectorStoreUtils::documentToProduct).toList();

        log.info("Found {} products matching the search criteria", foundProducts.size());
        return foundProducts;
    }
}

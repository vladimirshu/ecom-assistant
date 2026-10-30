package com.ecomassistant.listing;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.catalog.CatalogCategoryRegistry;
import com.ecomassistant.catalog.Product;
import com.ecomassistant.config.EcomBrowsingProperties;
import com.ecomassistant.dto.ProductDto;
import com.ecomassistant.dto.ProductDtoMapper;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, no-LLM catalog listing. It reads Qdrant payloads directly (no similarity
 * search, no embeddings, no search controller, no search quota) and traverses products in
 * a stable SKU-derived order, bound to a signed, revision/category/limit-scoped cursor.
 */
@Service
public class CatalogListingService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final QdrantClient qdrantClient;
    private final CatalogCategoryRegistry categoryRegistry;
    private final CatalogReadiness catalogReadiness;
    private final ProductDtoMapper productDtoMapper;
    private final CatalogCursorCodec cursorCodec;
    private final EcomBrowsingProperties browsingProperties;
    private final Clock clock;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:products}")
    private String collectionName;

    public CatalogListingService(QdrantClient qdrantClient, CatalogCategoryRegistry categoryRegistry,
            CatalogReadiness catalogReadiness, ProductDtoMapper productDtoMapper,
            CatalogCursorCodec cursorCodec, EcomBrowsingProperties browsingProperties, Clock clock) {
        this.qdrantClient = qdrantClient;
        this.categoryRegistry = categoryRegistry;
        this.catalogReadiness = catalogReadiness;
        this.productDtoMapper = productDtoMapper;
        this.cursorCodec = cursorCodec;
        this.browsingProperties = browsingProperties;
        this.clock = clock;
    }

    public CatalogPage list(String category, Integer limit, String cursorToken) {
        int effectiveLimit = limit == null ? browsingProperties.defaultLimit() : limit;
        validateLimit(effectiveLimit);
        validateCategory(category);

        if (!catalogReadiness.isReady()) {
            throw new CatalogUnavailableException("Catalog is not ready");
        }
        String revision = catalogReadiness.verifiedRevision();

        int offset = resolveOffset(category, effectiveLimit, cursorToken, revision);

        List<Product> products = listEligible(category, revision);
        products.sort(Comparator.comparing(Product::getSku));

        int from = Math.min(offset, products.size());
        int to = Math.min(offset + effectiveLimit, products.size());
        List<ProductDto> page = products.subList(from, to).stream()
                .map(productDtoMapper::toDto)
                .toList();

        String nextCursor = to < products.size()
                ? cursorCodec.encode(new CatalogCursor(revision, category, effectiveLimit, to,
                        clock.instant().getEpochSecond()))
                : null;

        return new CatalogPage(page, nextCursor);
    }

    private int resolveOffset(String category, int effectiveLimit, String cursorToken, String revision) {
        if (cursorToken == null) {
            return 0;
        }
        CatalogCursor cursor = cursorCodec.decode(cursorToken);
        if (!revision.equals(cursor.revision())) {
            throw new CatalogRequestException("Catalog changed, start the category again");
        }
        if (!Objects.equals(category, cursor.category())) {
            throw new CatalogRequestException("Cursor does not match the requested category");
        }
        if (cursor.limit() != effectiveLimit) {
            throw new CatalogRequestException("Cursor does not match the requested page size");
        }
        long ageSeconds = clock.instant().getEpochSecond() - cursor.issuedAt();
        if (ageSeconds > browsingProperties.cursorTtl().toSeconds()) {
            throw new CatalogRequestException("Cursor has expired");
        }
        if (cursor.offset() < 0) {
            throw new CatalogRequestException("Cursor is malformed");
        }
        return cursor.offset();
    }

    private void validateLimit(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new CatalogRequestException("limit must be between 1 and 100");
        }
    }

    private void validateCategory(String category) {
        if (category != null && !categoryRegistry.isKnown(category)) {
            throw new CatalogRequestException("Unknown category");
        }
    }

    private List<Product> listEligible(String category, String revision) {
        Common.Filter filter = category == null
                ? null
                : Common.Filter.newBuilder()
                    .addMust(ConditionFactory.matchKeyword("categoryPathIds", category))
                    .build();

        List<Product> result = new ArrayList<>();
        for (Points.RetrievedPoint point : scrollAll(filter)) {
            Product product = VectorStoreUtils.payloadToProduct(point.getPayloadMap());
            if (revision.equals(product.getCatalogRevision())) {
                result.add(product);
            }
        }
        return result;
    }

    private List<Points.RetrievedPoint> scrollAll(Common.Filter filter) {
        List<Points.RetrievedPoint> all = new ArrayList<>();
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                throw new CatalogUnavailableException("Catalog is unavailable");
            }
            Common.PointId offset = null;
            while (true) {
                Points.ScrollPoints.Builder builder = Points.ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(256)
                        .setWithPayload(WithPayloadSelectorFactory.enable(true));
                if (filter != null) {
                    builder.setFilter(filter);
                }
                if (offset != null) {
                    builder.setOffset(offset);
                }
                Points.ScrollResponse response = qdrantClient.scrollAsync(builder.build()).get();
                all.addAll(response.getResultList());
                if (response.hasNextPageOffset()) {
                    offset = response.getNextPageOffset();
                }
                else {
                    break;
                }
            }
            return all;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException("Catalog is unavailable");
        }
        catch (Exception e) {
            throw new CatalogUnavailableException("Catalog is unavailable");
        }
    }
}

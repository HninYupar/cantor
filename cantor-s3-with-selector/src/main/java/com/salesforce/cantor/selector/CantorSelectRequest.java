package com.salesforce.cantor.selector;

import java.util.Collections;
import java.util.Map;

// Builder pattern
public class CantorSelectRequest {
    private final String bucketName;
    private final String objectKey;
    private final long startTimestampMillis;
    private final long endTimestampMillis;
    private final Map<String, String> metadataQuery;
    private final Map<String, String> dimensionsQuery;

    public CantorSelectRequest(final String bucketName,
                               final String objectKey,
                               final long startTimestampMillis,
                               final long endTimestampMillis,
                               final Map<String, String> metadataQuery,
                               final Map<String, String> dimensionsQuery) {
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.startTimestampMillis = startTimestampMillis;
        this.endTimestampMillis = endTimestampMillis;
        this.metadataQuery = (metadataQuery != null) ? metadataQuery : Collections.emptyMap();
        this.dimensionsQuery = (dimensionsQuery != null) ? dimensionsQuery : Collections.emptyMap();
    }

    public String getBucketName() {
        return this.bucketName;
    }

    public String getObjectKey() {
        return this.objectKey;
    }

    public long getStartTimestampMillis() {
        return this.startTimestampMillis;
    }

    public long getEndTimestampMillis() {
        return this.endTimestampMillis;
    }

    public Map<String, String> getMetadataQuery() {
        return this.metadataQuery;
    }

    public Map<String, String> getDimensionsQuery() {
        return this.dimensionsQuery;
    }
}

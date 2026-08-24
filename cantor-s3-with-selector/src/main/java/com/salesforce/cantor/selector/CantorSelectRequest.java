/*
 * Copyright (c) 2020, Salesforce.com, Inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.cantor.selector;

import java.util.Collections;
import java.util.Map;

/**
 * Defines the request type.
 */
public class CantorSelectRequest {
    public enum Selection {ALL, METADATA, DIMENSION}

    private final String bucketName;
    private final String objectKey;
    private final long startTimestampMillis;
    private final long endTimestampMillis;
    private final Map<String, String> metadataQuery;
    private final Map<String, String> dimensionsQuery;
    private final Selection selection;
    private final String selectionKey;

    public CantorSelectRequest(final String bucketName,
                               final String objectKey,
                               final long startTimestampMillis,
                               final long endTimestampMillis,
                               final Map<String, String> metadataQuery,
                               final Map<String, String> dimensionsQuery,
                               final Selection selection,
                               final String selectionKey) {
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.startTimestampMillis = startTimestampMillis;
        this.endTimestampMillis = endTimestampMillis;
        this.metadataQuery = (metadataQuery != null) ? metadataQuery : Collections.emptyMap();
        this.dimensionsQuery = (dimensionsQuery != null) ? dimensionsQuery : Collections.emptyMap();
        this.selection = selection;
        this.selectionKey = selectionKey;
    }

    public static CantorSelectRequest selectAll(final String bucketName,
                                                final String objectKey,
                                                final long startTimestampMillis,
                                                final long endTimestampMillis,
                                                final Map<String, String> metadataQuery,
                                                final Map<String, String> dimensionsQuery) {
        return new CantorSelectRequest(bucketName, objectKey, startTimestampMillis, endTimestampMillis,
                metadataQuery, dimensionsQuery, Selection.ALL, null);
    }

    public static CantorSelectRequest selectMetadata(final String bucketName,
                                                final String objectKey,
                                                final long startTimestampMillis,
                                                final long endTimestampMillis,
                                                final Map<String, String> metadataQuery,
                                                final Map<String, String> dimensionsQuery,
                                                final String metadataKey) {
        return new CantorSelectRequest(bucketName, objectKey, startTimestampMillis, endTimestampMillis,
                metadataQuery, dimensionsQuery, Selection.METADATA, metadataKey);
    }

    public static CantorSelectRequest selectDimension(final String bucketName,
                                                     final String objectKey,
                                                     final long startTimestampMillis,
                                                     final long endTimestampMillis,
                                                     final Map<String, String> metadataQuery,
                                                     final Map<String, String> dimensionsQuery,
                                                     final String dimensionKey) {
        return new CantorSelectRequest(bucketName, objectKey, startTimestampMillis, endTimestampMillis,
                metadataQuery, dimensionsQuery, Selection.DIMENSION, dimensionKey);
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

    public Selection getSelection() {
        return this.selection;
    }

    public String getSelectionKey() {
        return this.selectionKey;
    }
}

package com.salesforce.cantor.selector;

import com.amazonaws.services.s3.AmazonS3;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * This class implements client-side select.
 */
public class LocalSelect extends SelectUtils{
    private static final Logger logger = LoggerFactory.getLogger(LocalSelect.class);

    private final AmazonS3 s3Client;

    private final Gson parser = new Gson();

    private static class StoredEvent {
        long timestampMillis;
        Map<String, String> metadata;
        Map<String, Double> dimensions;
    }

    public LocalSelect(final AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public String query(final CantorSelectRequest request) throws IOException {
        final long before = System.nanoTime();
        try {
            // download the whole object
            final byte[] bytes = S3Utils.getObjectBytes(this.s3Client, request.getBucketName(), request.getObjectKey());
            final String content = new String(bytes);

            final StringBuilder results = new StringBuilder();
            for (final String line : content.split("\n")) {
                if (line.isEmpty()) {
                    continue;
                }
                final StoredEvent event = this.parser.fromJson(line, StoredEvent.class);
                if (match(event, request)) {
                    results.append(selectRequestField(line, event, request)).append("\n");
                }
            }
            return results.toString();
        } finally {
            logger.info("local select query - bucket: {} - key: {}; time spent: {}ms",
                    request.getBucketName(), request.getObjectKey(),
                    ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    private String selectRequestField(final String originalLine, final StoredEvent event, final CantorSelectRequest request) {
        switch (request.getSelection()) {
            case METADATA: {
                final String metadataKey = request.getSelectionKey();
                return this.parser.toJson(Collections.singletonMap(metadataKey, event.metadata.get(metadataKey)));
            }
            case DIMENSION: {
                final String dimensionKey = request.getSelectionKey();
                final Map<String, Object> filteredResult = new LinkedHashMap<>();
                filteredResult.put("timestampMillis", event.timestampMillis);
                filteredResult.put(dimensionKey, event.dimensions.get(dimensionKey));
                return this.parser.toJson(filteredResult);
            }
            case ALL:
            default:
                return originalLine;
        }
    }

    // A line matches when it falls inside the timestamp window AND satisfies every metadata condition AND every dimension condition.
    private boolean match(final StoredEvent event, final CantorSelectRequest request) {
        // timestamp window: [request.startTimeStampMillis, request.endTimeStampMillis]
        if (event.timestampMillis < request.getStartTimestampMillis()
                || event.timestampMillis > request.getEndTimestampMillis()) {
            return false;
        }

        for (final Map.Entry<String, String> entry : request.getMetadataQuery().entrySet()) {
            final String actual = event.metadata.get(entry.getKey());
            final String pattern = entry.getValue();
            if (!metadataMatch(pattern, actual)) {
                return false;
            }
        }

        for (final Map.Entry<String, String> entry : request.getDimensionsQuery().entrySet()) {
            final Double actual = event.dimensions.get(entry.getKey());
            final String pattern = entry.getValue();
            if (!dimensionMatch(pattern, actual)) {
                return false;
            }
        }

        return true;
    }

    private boolean metadataMatch(final String pattern, final String actual) {
        if (actual == null) {
            return false;
        }
        // metadataMatch("!~user-*", "web-1") --> like("user-*", "web-1") --> !(False) --> True
        if (pattern.startsWith("!~")) {
            return !like(pattern.substring(2), actual);
        // metadataMatch("~user-*", "user-1") --> like("user-*", "user-1") --> True
        } else if (pattern.startsWith("~")) {
            return like(pattern.substring(1), actual);
        // metadataMatch("!=user-1", "user-1") --> "user-1" = "user-1" --> !(True) --> False
        } else if (pattern.startsWith("!=")) {
            return !actual.equals(pattern.substring(2));
        } else if (pattern.startsWith("=")) {
            return actual.equals(pattern.substring(1));
        } else {
            return actual.equals(pattern);
        }
    }

    private boolean dimensionMatch(final String pattern, final Double actual) {
        if (actual == null) {
            return false;
        }
        final double value = actual;
        if (pattern.contains("..")) {
            final int split = pattern.indexOf("..");
            final double firstValue = Double.parseDouble(pattern.substring(0, split));
            final double secValue = Double.parseDouble(pattern.substring(split + 2));
            return value >= firstValue && value <= secValue;
        } else if (pattern.startsWith(">=")) {
            return value >= Double.parseDouble(pattern.substring(2));
        } else if (pattern.startsWith("<=")) {
            return value <= Double.parseDouble(pattern.substring(2));
        } else if (pattern.startsWith(">")) {
            return value > Double.parseDouble(pattern.substring(1));
        } else if (pattern.startsWith("<")) {
            return value < Double.parseDouble(pattern.substring(1));
        } else if (pattern.startsWith("!=")) {
            return value != Double.parseDouble(pattern.substring(2));
        } else if (pattern.startsWith("=")) {
            return value == Double.parseDouble(pattern.substring(1));
        } else {
            return value == Double.parseDouble(pattern);
        }
    }

    private boolean like(final String pattern, final String value) {
        final StringBuilder regex = new StringBuilder();
        for (final String chunk : split(pattern)) {
            if ("*".equals(chunk)) {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(chunk));
            }
        }
        return value.matches(regex.toString());
    }

    private String[] split(final String pattern) {
        // split on '*'
        return pattern.split("(?=\\*)|(?<=\\*)");
    }
}

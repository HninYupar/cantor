package com.salesforce.cantor.selector;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Scanner;

/**
 * This class uses S3 select.
 */
public class S3Select extends SelectUtils {
    private static final Logger logger = LoggerFactory.getLogger(S3Select.class);

    private final AmazonS3 s3Client;

    public S3Select(final AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public String query(final CantorSelectRequest request) throws IOException {
        final long before = System.nanoTime();
        try {
            final String querySQL = generateQuery(request);
            return queryObjectJson(
                    this.s3Client, request.getBucketName(), request.getObjectKey(), querySQL);
        } finally {
            logger.info("s3 select query - bucket: {} - key: {}; time spent: {}ms",
                    request.getBucketName(), request.getObjectKey(),
                    ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    private String generateQuery(final CantorSelectRequest request) {
        switch (request.getSelection()) {
            case METADATA:
                return generateMetadataQuery(request.getSelectionKey(),
                        request.getStartTimestampMillis(), request.getEndTimestampMillis(),
                        request.getMetadataQuery(), request.getDimensionsQuery());
            case DIMENSION:
                return generateDimensionQuery(request.getSelectionKey(),
                        request.getStartTimestampMillis(), request.getEndTimestampMillis(),
                        request.getMetadataQuery(), request.getDimensionsQuery());
            case ALL:
            default:
                return generateGetQuery(request.getStartTimestampMillis(), request.getEndTimestampMillis(),
                        request.getMetadataQuery(), request.getDimensionsQuery());
        }
    }

    // creates an s3 select compatible query
    // see https://docs.aws.amazon.com/AmazonS3/latest/dev/s3-glacier-select-sql-reference-select.html
    private String generateGetQuery(final long startTimestampMillis,
                                    final long endTimestampMillis,
                                    final Map<String, String> metadataQuery,
                                    final Map<String, String> dimensionsQuery) {
        final String timestampClause = String.format("s.timestampMillis >= %d AND s.timestampMillis <= %d", startTimestampMillis, endTimestampMillis);
        return String.format("SELECT * FROM s3object[*] s WHERE %s %s %s",
                timestampClause,
                getMetadataQuerySql(metadataQuery),
                getDimensionsQuerySql(dimensionsQuery)
        );
    }

    private String generateMetadataQuery(final String metadataKey,
                                         final long startTimestampMillis,
                                         final long endTmestampMillis,
                                         final Map<String, String> metadataQuery,
                                         final Map<String, String> dimensionsQuery) {
        final String timestampClause = String.format("s.timestampMillis >= %d AND s.timestampMillis <= %d", startTimestampMillis, endTmestampMillis);
        return String.format("SELECT s.metadata.\"%s\" FROM s3object[*] s WHERE %s %s %s",
                metadataKey,
                timestampClause,
                getMetadataQuerySql(metadataQuery),
                getDimensionsQuerySql(dimensionsQuery)
        );
    }

    private String generateDimensionQuery(final String dimensionKey,
                                          final long startTimestampMillis,
                                          final long endTmestampMillis,
                                          final Map<String, String> metadataQuery,
                                          final Map<String, String> dimensionsQuery) {
        final String timestampClause = String.format("s.timestampMillis >= %d AND s.timestampMillis <= %d", startTimestampMillis, endTmestampMillis);
        return String.format("SELECT s.timestampMillis, s.dimensions.\"%s\" FROM s3object[*] s WHERE %s %s %s",
                dimensionKey,
                timestampClause,
                getMetadataQuerySql(metadataQuery),
                getDimensionsQuerySql(dimensionsQuery)
        );
    }

    // the metadata query object can contain these patterns:
    // '' (just a string): equals - 'user-id' => 'user-1'
    // '=': equals - 'user-id' => '=user-1'
    // '!=': not equals - 'user-id' => '!=user-1'
    // '~': limited regex like - 'user-id' => '~user-*'
    // '!~': inverted limited regex like - 'user-id' => '!~user-*'
    private String getMetadataQuerySql(final Map<String, String> metadataQuery) {
        if (metadataQuery.isEmpty()) {
            return "";
        }
        final StringBuilder sql = new StringBuilder();
        for (final Map.Entry<String, String> entry : metadataQuery.entrySet()) {
            final String metadataName = prefixMetadata(entry.getKey());
            final String query = entry.getValue();
            // s3 select only supports limited regex
            if (query.startsWith("~")) {
                sql.append(" AND ").append(metadataName).append(" LIKE ").append(quote(regexToSql(query.substring(1))));
            } else if (query.startsWith("!~")) {
                sql.append(" AND ").append(metadataName).append(" NOT LIKE ").append(quote(regexToSql(query.substring(2))));
            } else if (query.startsWith("=")) {
                sql.append(" AND ").append(metadataName).append("=").append(quote(query.substring(1)));
            } else if (query.startsWith("!=")) {
                sql.append(" AND ").append(metadataName).append("!=").append(quote(query.substring(2)));
            } else {
                sql.append(" AND ").append(metadataName).append("=").append(quote(query));
            }
        }
        return sql.toString();
    }

    private String regexToSql(final String regex) {
        return regex
                .replace("*", "%")
                .replace("_", "\\\\_");
    }

    // the dimension query object can contain these patterns:
    // '' (just a number): equals - 'cpu' => '90'
    // '=': equals - 'cpu' => '=90'
    // '!=': not equals - 'cpu' => '!=90'
    // '..': between - 'cpu' => '90..100'
    // '>': greater than - 'cpu' => '>90'
    // '>=': greater than or equals - 'cpu' => '>=90'
    // '<': less than - 'cpu' => '<90'
    // '<=': less than or equals - 'cpu' => '<=90'
    private String getDimensionsQuerySql(final Map<String, String> dimensionsQuery) {
        if (dimensionsQuery.isEmpty()) {
            return "";
        }
        final StringBuilder sql = new StringBuilder();
        for (final Map.Entry<String, String> entry : dimensionsQuery.entrySet()) {
            final String dimensionName = prefixDimension(entry.getKey());
            final String query = entry.getValue();
            if (query.contains("..")) {
                sql.append(" AND ")
                        .append(dimensionName)
                        .append(" BETWEEN ")
                        .append(Double.valueOf(query.substring(0, query.indexOf(".."))))
                        .append(" AND ")
                        .append(Double.valueOf(query.substring(query.indexOf("..") + 2)));
            } else if (query.startsWith(">=")) {
                sql.append(" AND ").append(dimensionName).append(">=").append(query.substring(2));
            } else if (query.startsWith("<=")) {
                sql.append(" AND ").append(dimensionName).append("<=").append(query.substring(2));
            } else if (query.startsWith(">")) {
                sql.append(" AND ").append(dimensionName).append(">").append(query.substring(1));
            } else if (query.startsWith("<")) {
                sql.append(" AND ").append(dimensionName).append("<").append(query.substring(1));
            } else if (query.startsWith("!=")) {
                sql.append(" AND ").append(dimensionName).append("!=").append(query.substring(2));
            } else if (query.startsWith("=")) {
                sql.append(" AND ").append(dimensionName).append("=").append(query.substring(1));
            } else {
                sql.append(" AND ").append(dimensionName).append("=").append(query);
            }
        }
        return sql.toString();
    }

    private String quote(final String key) {
        return String.format("'%s'", key);
    }

    private String prefixMetadata(final String key) {
        return String.format("s.metadata.\"%s\"", key);
    }

    private String prefixDimension(final String key) {
        return String.format("CAST ( s.dimensions.\"%s\" as decimal)", key);
    }

    public static String queryObjectJson(final AmazonS3 s3Client,
                                         final String bucket,
                                         final String key,
                                         final String query) throws IOException {
        return queryObject(s3Client, generateJsonRequest(bucket, key, query));
    }

    public static String queryObjectCsv(final AmazonS3 s3Client,
                                        final String bucket,
                                        final String key,
                                        final String query) throws IOException {
        return queryObject(s3Client, generateCsvRequest(bucket, key, query));
    }

    public static String queryObject(final AmazonS3 s3Client,
                                     final SelectObjectContentRequest request) throws IOException {

        final long before = System.nanoTime();
        try {
            final StringBuilder results = new StringBuilder();
            try (final SelectObjectContentResult result = s3Client.selectObjectContent(request)) {
                try (final InputStream inputStream = result.getPayload().getRecordsInputStream(
                        new SelectObjectContentEventVisitor() {
                            @Override
                            public void visit(final SelectObjectContentEvent.StatsEvent event) {
                                logger.info("s3 select query stats: bucket='{}' key='{}' bytes-scanned='{}' bytes-processed='{}' bytes-returned='{}'",
                                        request.getBucketName(),
                                        request.getKey(),
                                        event.getDetails().getBytesProcessed(),
                                        event.getDetails().getBytesScanned(),
                                        event.getDetails().getBytesReturned()
                                );
                            }
                        }
                )) {
                    try (final Scanner lineReader = new Scanner(inputStream)) {
                        // json events are stored in json lines format, so one json object per line
                        while (lineReader.hasNext()) {
                            results.append(lineReader.nextLine()).append("\n");
                        }
                    }
                }
            }
            return results.toString();
        } finally {
            final long timeSpent = (System.nanoTime() - before) / 1_000_000;
            logger.debug("s3 select query: bucket={} key={} type={} expression={}; time spent: {}ms",
                    request.getBucketName(), request.getKey(), request.getExpressionType(), request.getExpression(), timeSpent);
            logger.info("query object - bucket: {} - key: {}; time spent: {}ms",
                    request.getBucketName(), request.getKey(), timeSpent
            );
        }
    }

    /**
     * Request will allow a limited for of SQL describe here: https://docs.aws.amazon.com/AmazonS3/latest/dev/s3-glacier-select-sql-reference.html
     */
    public static SelectObjectContentRequest generateJsonRequest(final String bucket,
                                                                 final String key,
                                                                 final String query) {
        final SelectObjectContentRequest request = new SelectObjectContentRequest();
        request.setBucketName(bucket);
        request.setKey(key);
        request.setExpression(query);
        request.setExpressionType(ExpressionType.SQL);

        // queries will be made against an array of json objects
        final InputSerialization inputSerialization = new InputSerialization();
        inputSerialization.setJson(new JSONInput().withType(JSONType.LINES));
        inputSerialization.setCompressionType(CompressionType.NONE);
        request.setInputSerialization(inputSerialization);

        // response will be a json object
        final OutputSerialization outputSerialization = new OutputSerialization();
        outputSerialization.setJson(new JSONOutput());
        request.setOutputSerialization(outputSerialization);

        return request;
    }

    /**
     * Generate an S3 Select query against a csv file
     */
    public static SelectObjectContentRequest generateCsvRequest(final String bucket,
                                                                final String key,
                                                                final String query) {
        final SelectObjectContentRequest request = new SelectObjectContentRequest();
        request.setBucketName(bucket);
        request.setKey(key);
        request.setExpression(query);
        request.setExpressionType(ExpressionType.SQL);

        // queries will be made against an array of json objects
        final InputSerialization inputSerialization = new InputSerialization();
        inputSerialization.setCsv(new CSVInput().withFileHeaderInfo(FileHeaderInfo.USE).withFieldDelimiter(","));
        inputSerialization.setCompressionType(CompressionType.NONE);
        request.setInputSerialization(inputSerialization);

        // response will be a json object
        final OutputSerialization outputSerialization = new OutputSerialization();
        outputSerialization.setCsv(new CSVOutput());
        request.setOutputSerialization(outputSerialization);

        return request;
    }
}
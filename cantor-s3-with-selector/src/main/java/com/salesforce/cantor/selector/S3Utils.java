package com.salesforce.cantor.selector;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for all direct communication to s3 objects
 */
public class S3Utils {
    private static final Logger logger = LoggerFactory.getLogger(S3Utils.class);

    // read objects in 4MB chunks
    private static final int streamingChunkSize = 4 * 1024 * 1024;

    // in memory object cache
    private static final Cache<String, byte[]> cache = CacheBuilder.newBuilder()
            .maximumWeight(1024 * 1024 * 1024) // 1GB cache
            .weigher(new ObjectWeigher())
            .build();

    public static Collection<String> getKeys(final AmazonS3 s3Client,
                                             final String bucketName,
                                             final String prefix) throws IOException {
        return getKeys(s3Client, bucketName, prefix, 0, -1);
    }

    public static Collection<String> getKeys(final AmazonS3 s3Client,
                                             final String bucketName,
                                             final String prefix,
                                             final int start,
                                             final int count) throws IOException {
        final long before = System.nanoTime();
        try {
            final Set<String> keys = new HashSet<>();
            int index = 0;
            ObjectListing listing = null;
            do {
                if (listing == null) {
                    listing = s3Client.listObjects(bucketName, prefix);
                } else {
                    listing = s3Client.listNextBatchOfObjects(listing);
                }

                final List<S3ObjectSummary> objectSummaries = listing.getObjectSummaries();
                // skip sections that the start index wouldn't include
                if ((objectSummaries.size() - 1) + index < start) {
                    index += objectSummaries.size();
                    logger.debug("skipping {} objects to index={}", objectSummaries.size(), index);
                    listing = s3Client.listNextBatchOfObjects(listing);
                    continue;
                }

                for (final S3ObjectSummary summary : objectSummaries) {
                    if (start > index++) {
                        continue;
                    }
                    keys.add(summary.getKey());

                    if (keys.size() == count) {
                        logger.debug("retrieved {}/{} keys, returning early", keys.size(), count);
                        return keys;
                    }
                }

                logger.debug("got {} keys from {}", listing.getObjectSummaries().size(), listing);
            } while (listing.isTruncated());
            return keys;
        } finally {
            logger.info("get keys - bucket: {} - prefix: {} - start: {} - count: {}; time spent: {}ms",
                    bucketName, prefix, start, count, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static byte[] getObjectBytes(final AmazonS3 s3Client,
                                        final String bucketName,
                                        final String key) throws IOException {
        return getObjectBytes(s3Client, bucketName, key, 0, -1);
    }

    public static byte[] getObjectBytes(final AmazonS3 s3Client,
                                        final String bucketName,
                                        final String key,
                                        final long start,
                                        final long end) throws IOException {
        final long before = System.nanoTime();
        try {
            final GetObjectRequest request = new GetObjectRequest(bucketName, key);
            if (start >= 0 && end > 0) {
                request.setRange(start, end);
            } else if (start > 0 && end < 0) {
                request.setRange(start);
            }
            final S3Object s3Object = s3Client.getObject(request);
            try (final InputStream inputStream = s3Object.getObjectContent()) {
                try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    final byte[] data = new byte[streamingChunkSize];
                    int read;
                    while ((read = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, read);
                    }
                    buffer.flush();
                    return buffer.toByteArray();
                }
            }
        } finally {
            logger.info("get object bytes - bucket: {} - key: {} - start: {} - end: {}; time spent: {}ms",
                    bucketName, key, start, end, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static boolean doesObjectExist(final AmazonS3 s3Client,
                                          final String bucketName,
                                          final String key) {
        final long before = System.nanoTime();
        try {
            return s3Client.doesObjectExist(bucketName, key);
        } finally {
            logger.info("does object exist - bucket: {} - key: {}; time spent: {}ms",
                    bucketName, key, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static InputStream getObjectStream(final AmazonS3 s3Client,
                                              final String bucketName,
                                              final String key) {
        final long before = System.nanoTime();
        try {
            return s3Client.getObject(bucketName, key).getObjectContent();
        } finally {
            logger.info("get object stream - bucket: {} - key: {}; time spent: {}ms",
                    bucketName, key, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static void putObject(final AmazonS3 s3Client,
                                 final String bucketName,
                                 final String key,
                                 final InputStream content,
                                 final ObjectMetadata metadata) throws IOException {
        final long before = System.nanoTime();
        try {
            final PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, content, metadata);
            putObjectRequest.withCannedAcl(CannedAccessControlList.BucketOwnerFullControl);
            s3Client.putObject(putObjectRequest);
        } finally {
            logger.info("put object - bucket: {} - key: {}; time spent: {}ms",
                    bucketName, key, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static boolean deleteObject(final AmazonS3 s3Client, final String bucketName, final String key) {
        final long before = System.nanoTime();
        try {
            if (!s3Client.doesObjectExist(bucketName, key)) {
                return false;
            }
            s3Client.deleteObject(bucketName, key);
            return true;
        } finally {
            logger.info("delete object - bucket: {} - key: {}; time spent: {}ms",
                    bucketName, key, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static void deleteObjects(final AmazonS3 s3Client, final String bucketName, final Collection<String> keys) {
        final long before = System.nanoTime();
        try {
            if (keys == null || keys.isEmpty()) {
                return;
            }
            final DeleteObjectsRequest request = new DeleteObjectsRequest(bucketName);
            request.setKeys(keys.stream().map(DeleteObjectsRequest.KeyVersion::new).collect(Collectors.toList()));
            s3Client.deleteObjects(request);
        } finally {
            logger.info("delete objects - bucket: {} - keys: {}; time spent: {}ms",
                    bucketName, keys, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static void deleteObjects(final AmazonS3 s3Client,
                                     final String bucketName,
                                     final String prefix) {
        final long before = System.nanoTime();
        try {
            // delete all objects
            ObjectListing objectListing = s3Client.listObjects(bucketName, prefix);
            while (true) {
                for (final S3ObjectSummary summary : objectListing.getObjectSummaries()) {
                    s3Client.deleteObject(bucketName, summary.getKey());
                }
                if (objectListing.isTruncated()) {
                    objectListing = s3Client.listNextBatchOfObjects(objectListing);
                } else {
                    break;
                }
            }
        } finally {
            logger.info("delete objects - bucket: {} - prefix: {}; time spent: {}ms",
                    bucketName, prefix, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static int getSize(final AmazonS3 s3Client, final String bucketName, final String bucketPrefix) {
        final long before = System.nanoTime();
        try {
            int totalSize = 0;
            ObjectListing listing = null;
            do {
                if (listing == null) {
                    listing = s3Client.listObjects(bucketName, bucketPrefix);
                } else {
                    listing = s3Client.listNextBatchOfObjects(listing);
                }
                totalSize += listing.getObjectSummaries().size();
                logger.debug("got {} keys from {}", listing.getObjectSummaries().size(), listing);
            } while (listing.isTruncated());
            return totalSize;
        } finally {
            logger.info("get size - bucket: {} - prefix: {}; time spent: {}ms",
                    bucketName, bucketPrefix, ((System.nanoTime() - before) / 1_000_000)
            );
        }
    }

    public static String getCleanKeyForNamespace(final String namespace) {
        final String cleanName = namespace.replaceAll("[^A-Za-z0-9_\\-/]", "").toLowerCase();
        return String.format("cantor-%s-%s",
                cleanName.substring(0, Math.min(32, cleanName.length())), Math.abs(namespace.hashCode()));
    }

    private static class ObjectWeigher implements Weigher<String, byte[]> {
        @Override
        public int weigh(final String keyIgnored, final byte[] value) {
            return value.length;
        }
    }
}


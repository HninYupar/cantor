package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import com.salesforce.cantor.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import static org.testng.Assert.*;

public class IntegrationEventsTest {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationEventsTest.class);

    private static final int INVOCATIONS = 10;

    private static Cantor cantor;

    private static final long VISIBILITY_TIMEOUT_MS = 1_200_000;
    private static final long POLL_INTERVAL_MS = 2_000;

    private static final int BUCKETS = 3;

    private static final String[] REGIONS = {"us-west-1a", "us-west-1b", "us-east-1a"};

    private static final String EXACT_METADATA_FILTER = "user-0";
    private static final String REGEX_METADATA_FILTER = "~us-west-1*";

    private static final String EXACT_DIMENSION_FILTER = "0";
    private static final String RANGE_DIMENSION_FILTER = "0..1";

    private static int eventCount;
    private static int eventsPerBucket;
    private static int eventsMatchingMetadataRegex;
    private static int eventsMatchingDimensionRange;

    private long startWindow;
    private long endWindow;

    private final String namespace = UUID.randomUUID().toString();

    public static void setEventCount(final int count) {
        eventCount = count;
        eventsPerBucket = count / BUCKETS;
        eventsMatchingMetadataRegex = eventsPerBucket * 2;
        eventsMatchingDimensionRange = eventsPerBucket * 2;
    }

    public static void setCantor(final Cantor client) {
        cantor = client;
    }

    @BeforeClass
    public void before() throws Exception {
        getEvents().create(this.namespace);

        // Store baseline data once, then wait until it's queryable.
        // On S3 this waits for buffer flush.
        // On H2 and MySQL it returns immediately.
        final List<Events.Event> storedEvents = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        this.startWindow = timestamp;
        for (int i = 0; i < eventCount; ++i) {
            final Map<String, String> metadata = getRandomMetadata(5);
            metadata.put("user", "user-" + (i % BUCKETS));
            metadata.put("region", REGIONS[i % BUCKETS]);

            final Map<String, Double> dimensions = getRandomDimensions(5);
            dimensions.put("latency", (double) (i % BUCKETS));

            final byte[] payload = getRandomPayload(1024);
            timestamp += 1;
            storedEvents.add(new Events.Event(timestamp, metadata, dimensions, payload));
        }
        this.endWindow = timestamp + 1;

        getEvents().store(this.namespace, storedEvents);
        logger.info("Stored {} events", eventCount);
        pollUntilVisible(eventCount);
    }

    @AfterClass(alwaysRun = true)
    public void after() throws Exception {
        getEvents().drop(this.namespace);
    }

    @Test(invocationCount = INVOCATIONS)
    public void getAll() throws Exception {
        final Events events = getEvents();

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(this.namespace, this.startWindow, this.endWindow);
        logger.info("took {}ms to fetch all {} events", System.currentTimeMillis() - startTimestamp, eventCount);

        assertEquals(results.size(), eventCount);
    }

    // Get the values of "metadata-key-0" across all events
    @Test(invocationCount = INVOCATIONS)
    public void getMetadata() throws Exception {
        final Events events = getEvents();
        final String metadataKey = "metadata-key-0";

        final long startTimestamp = System.currentTimeMillis();
        final Set<String> results = events.metadata(this.namespace, metadataKey,
                this.startWindow, this.endWindow, Collections.emptyMap(), Collections.emptyMap());
        logger.info("took {}ms to get the values for '{}'", System.currentTimeMillis() - startTimestamp, metadataKey);

        assertEquals(results.size(), eventCount);
    }

    @Test(invocationCount = INVOCATIONS)
    public void getDimension() throws Exception {
        final Events events = getEvents();
        final String dimensionKey = "dimension-key-0";

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.dimension(this.namespace, dimensionKey,
                this.startWindow, this.endWindow, Collections.emptyMap(), Collections.emptyMap());
        logger.info("took {}ms to fetch {} events with dimension key '{}'", System.currentTimeMillis() - startTimestamp, results.size(), dimensionKey);

        assertEquals(results.size(), eventCount);
    }

    // query the events that exactly match with expected metadata value
    @Test(invocationCount = INVOCATIONS)
    public void getWithMetadataQuery1() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = Collections.singletonMap("user", EXACT_METADATA_FILTER);

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(this.namespace, this.startWindow, this.endWindow,
                metadataQuery, Collections.emptyMap());
        logger.info("took {}ms to get {} events with metadata value '{}'", System.currentTimeMillis() - startTimestamp, results.size(), EXACT_METADATA_FILTER);

        assertEquals(results.size(), eventsPerBucket);
    }

    // query the events that match with metadata regex pattern
    @Test(invocationCount = INVOCATIONS)
    public void getWithMetadataQuery2() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = Collections.singletonMap("region", REGEX_METADATA_FILTER);

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(this.namespace, this.startWindow, this.endWindow,
                metadataQuery, Collections.emptyMap());
        logger.info("took {}ms to get {} events where metadata matches regex '{}'", System.currentTimeMillis() - startTimestamp, results.size(), REGEX_METADATA_FILTER);

        assertEquals(results.size(), eventsMatchingMetadataRegex);
    }

    // query the events that match an exact dimension value
    @Test(invocationCount = INVOCATIONS)
    public void getWithDimensionQuery1() throws Exception {
        final Events events = getEvents();
        final Map<String, String> dimensionsQuery = Collections.singletonMap("latency", EXACT_DIMENSION_FILTER);

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(this.namespace, this.startWindow, this.endWindow,
                Collections.emptyMap(), dimensionsQuery);
        logger.info("took {}ms to get {} events with dimension value '{}'", System.currentTimeMillis() - startTimestamp, results.size(), EXACT_DIMENSION_FILTER);

        assertEquals(results.size(), eventsPerBucket);
    }

    // query the events that match a dimension range (between)
    @Test(invocationCount = INVOCATIONS)
    public void getWithDimensionQuery2() throws Exception {
        final Events events = getEvents();
        final Map<String, String> dimensionsQuery = Collections.singletonMap("latency", RANGE_DIMENSION_FILTER);

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(this.namespace, this.startWindow, this.endWindow,
                Collections.emptyMap(), dimensionsQuery);
        logger.info("took {}ms to get {} events where dimension values fall within '{}'", System.currentTimeMillis() - startTimestamp, results.size(), RANGE_DIMENSION_FILTER);

        assertEquals(results.size(), eventsMatchingDimensionRange);
    }

    private void pollUntilVisible(final int expected) throws Exception {
        final long deadline = System.currentTimeMillis() + VISIBILITY_TIMEOUT_MS;
        List<Events.Event> results = getEvents().get(this.namespace, this.startWindow, this.endWindow);
        int attempt = 1;
        while (results.size() < expected && System.currentTimeMillis() < deadline) {
            logger.info("attempt {}: got {}/{} events.",
                    attempt, results.size(), expected);
            Thread.sleep(POLL_INTERVAL_MS);
            results = getEvents().get(this.namespace, this.startWindow, this.endWindow);
            attempt++;
        }
        logger.info("Data is queryable after {} attempt(s): {}/{} events", attempt, results.size(), expected);
        if (results.size() < expected) {
            throw new AssertionError("Timeout waiting for events to become visible: got "
                    + results.size() + "/" + expected + " after " + attempt + " attempts");
        }
    }

    protected Map<String, Double> getRandomDimensions(final int count) {
        final Map<String, Double> dimensions = new HashMap<>();
        for (int i = 0; i < count; ++i) {
            dimensions.put("dimension-key-" + i, ThreadLocalRandom.current().nextDouble());
        }
        return dimensions;
    }

    protected Map<String, String> getRandomMetadata(final int count) {
        final Map<String, String> metadata = new HashMap<>();
        for (int i = 0; i < count; ++i) {
            metadata.put("metadata-key-" + i, UUID.randomUUID().toString());
        }
        return metadata;
    }

    protected byte[] getRandomPayload(final int size) {
        final byte[] buffer = new byte[size];
        new Random().nextBytes(buffer);
        return buffer;
    }

    protected Events getEvents() throws IOException {
        return cantor.events();
    }
}

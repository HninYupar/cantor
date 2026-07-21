package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import com.salesforce.cantor.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class IntegrationEventsTest {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationEventsTest.class);

    private static final int EVENT_COUNT = 30;
    private static final int INVOCATIONS = 100;

    private static Cantor cantor;

    private final String namespace = UUID.randomUUID().toString();

    public static void setCantor(final Cantor client) {
        cantor = client;
    }

    @BeforeMethod
    public void before() throws Exception {
        getEvents().create(this.namespace);
    }

    @AfterMethod(alwaysRun = true)
    public void after() throws Exception {
        getEvents().drop(this.namespace);
    }

    @Test(invocationCount = INVOCATIONS)
    public void store30() throws Exception {
        final Events events = getEvents();
        final List<Events.Event> storedEvents = new ArrayList<>();
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < EVENT_COUNT; ++i) {
            final Map<String, String> metadata = getRandomMetadata(5);
            final Map<String, Double> dimensions = getRandomDimensions(5);
            final byte[] payload = getRandomPayload(1024);
            timestamp += 1;
            storedEvents.add(new Events.Event(timestamp, metadata, dimensions, payload));
        }
        final long startTimestamp = System.currentTimeMillis();
        events.store(this.namespace, storedEvents);
        logger.info("took {}ms to store {} events", System.currentTimeMillis() - startTimestamp, EVENT_COUNT);
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

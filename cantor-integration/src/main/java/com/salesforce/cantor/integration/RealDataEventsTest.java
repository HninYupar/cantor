/*
 * Copyright (c) 2020, Salesforce.com, Inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import com.salesforce.cantor.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RealDataEventsTest {

    private static final Logger logger = LoggerFactory.getLogger(RealDataEventsTest.class);

    private static final int INVOCATIONS = 10;

    private static Cantor cantor;

    // Fixed tenant and instance
    private static final String TENANT = "falcon-aws-prod0-uswest2-core1-usa4s";
    private static final String INSTANCE = "usa4s-casam-app-green-7494bc8846-7z6wd";

    private static final String INSTANCE_METADATA_KEY = "host";

    private static final String NAMESPACE = "maiev-tenant-" + TENANT;

    // Fixed UTC window (6h span)
    // start = 2026-08-27 15:49:06.367 UTC
    // end = 2026-08-27 21:49:06.367 UTC
    private static final long START_TIMESTAMP_MILLIS = 1787845746367L;
    private static final long END_TIMESTAMP_MILLIS = 1787867346367L;

    public static void setCantor(final Cantor client) {
        cantor = client;
    }

    // query all events for the selected instance "usa4s-casam-app-green-7494bc8846-7z6wd"
    @Test(invocationCount = INVOCATIONS)
    public void getAll() throws Exception {
        final Events events = getEvents();
        final Map<String, String> instanceQuery = Collections.singletonMap(INSTANCE_METADATA_KEY, INSTANCE);

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(
                NAMESPACE, START_TIMESTAMP_MILLIS, END_TIMESTAMP_MILLIS, instanceQuery, Collections.emptyMap());
        logger.info("getAll: took {}ms to fetch {} events in '{}' instance in the '{}' tenant",
                System.currentTimeMillis() - startTimestamp, results.size(), INSTANCE, TENANT);
    }

    // query events for the selected instance where metadata "name" equals "top"
    @Test(invocationCount = INVOCATIONS)
    public void getMetadataExactMatch() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = new HashMap<>();
        metadataQuery.put(INSTANCE_METADATA_KEY, INSTANCE);
        metadataQuery.put("name", "top");

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(
                NAMESPACE, START_TIMESTAMP_MILLIS, END_TIMESTAMP_MILLIS, metadataQuery, Collections.emptyMap());
        logger.info("getMetadataExactMatch: took {}ms to fetch {} events with metadata key '{}' set to '{}' in '{}' instance",
                System.currentTimeMillis() - startTimestamp, results.size(), "name", "top", INSTANCE);
    }

    // query events for the selected instance where metadata "name" equals "enableasyncmem" (an ultra-selective case)
    // filter (~12 of ~1880 events in the window)
    @Test(invocationCount = INVOCATIONS)
    public void getMetadataHighSelectivityMatch() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = new HashMap<>();
        metadataQuery.put(INSTANCE_METADATA_KEY, INSTANCE);
        metadataQuery.put("name", "enableasyncmem");

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(
                NAMESPACE, START_TIMESTAMP_MILLIS, END_TIMESTAMP_MILLIS, metadataQuery, Collections.emptyMap());
        logger.info("getMetadataHighSelectivityMatch: took {}ms to fetch {} events with metadata key '{}' set to '{}' in '{}' instance",
                System.currentTimeMillis() - startTimestamp, results.size(), "name", "enableasyncmem", INSTANCE);
    }

    // query events for the selected instance where metadata "script_name" does NOT equal "jstack.sh"
    @Test(invocationCount = INVOCATIONS)
    public void getMetadataNotEqualMatch() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = new HashMap<>();
        metadataQuery.put(INSTANCE_METADATA_KEY, INSTANCE);
        metadataQuery.put("script_name", "!=jstack.sh");

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(
                NAMESPACE, START_TIMESTAMP_MILLIS, END_TIMESTAMP_MILLIS, metadataQuery, Collections.emptyMap());
        logger.info("getMetadataNotEqualMatch: took {}ms to fetch {} events with metadata key '{}' not set to '{}' in '{}' instance",
                System.currentTimeMillis() - startTimestamp, results.size(), "script_name", "jstack.sh", INSTANCE);
    }

    // query events for the selected instance where metadata "script_name" matches the wildcard "*stat.sh" (ex: netstat.sh, vmstat.sh, pidstat.sh)
    @Test(invocationCount = INVOCATIONS)
    public void getMetadataPatternMatch() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = new HashMap<>();
        metadataQuery.put(INSTANCE_METADATA_KEY, INSTANCE);
        metadataQuery.put("script_name", "~*stat.sh");

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(
                NAMESPACE, START_TIMESTAMP_MILLIS, END_TIMESTAMP_MILLIS, metadataQuery, Collections.emptyMap());
        logger.info("getMetadataPatternMatch: took {}ms to fetch {} events with metadata key '{}' matching wildcard '{}' in '{}' instance",
                System.currentTimeMillis() - startTimestamp, results.size(), "script_name", "*stat.sh", INSTANCE);
        logger.info("Return events: {}", results);
    }

    // query events for the selected instance where metadata key "script_param1" exists (any value)
    @Test(invocationCount = INVOCATIONS)
    public void getMetadataKeyExists() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = new HashMap<>();
        metadataQuery.put(INSTANCE_METADATA_KEY, INSTANCE);
        metadataQuery.put("script_param1", "~*");

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(
                NAMESPACE, START_TIMESTAMP_MILLIS, END_TIMESTAMP_MILLIS, metadataQuery, Collections.emptyMap());
        logger.info("getMetadataKeyExists: took {}ms to fetch {} events where metadata key '{}' exists in '{}' instance",
                System.currentTimeMillis() - startTimestamp, results.size(), "script_param1", INSTANCE);
    }

    // query events for the selected instance where dimension has a key "nmethods" with a value ">= 200000"
    @Test(invocationCount = INVOCATIONS)
    public void getDimensionComparisonMatch() throws Exception {
        final Events events = getEvents();
        final Map<String, String> metadataQuery = Collections.singletonMap(INSTANCE_METADATA_KEY, INSTANCE);
        final Map<String, String> dimensionsQuery = Collections.singletonMap("nmethods", ">=200000");

        final long startTimestamp = System.currentTimeMillis();
        final List<Events.Event> results = events.get(
                NAMESPACE, START_TIMESTAMP_MILLIS, END_TIMESTAMP_MILLIS, metadataQuery, dimensionsQuery);
        logger.info("getDimensionComparisonMatch: took {}ms to fetch {} events with dimension key '{}' matching '{}' in '{}' instance",
                System.currentTimeMillis() - startTimestamp, results.size(), "nmethods", ">=200000", INSTANCE);
    }

    private Events getEvents() throws IOException {
        return cantor.events();
    }
}

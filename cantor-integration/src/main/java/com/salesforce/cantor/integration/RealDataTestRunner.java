/*
 * Copyright (c) 2020, Salesforce.com, Inc.
 * All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause
 * For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.TestNG;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs RealDataEventsTest.
 */
public class RealDataTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(RealDataTestRunner.class);

    // IntegrationTestRunner.java sets an exact event count (30/300/3000), but real data has no fixed count.
    // maiev keeps writing new events, so the number is always changing and we don't control it. We
    // still reuse the CSV schema shared with IntegrationTestRunner, which has an EventCount column,
    // so this placeholder just fills that column for RealDataTestRunner. The reporter ignores it on read.
    private static final int NO_EVENT_COUNT = 0;

    public static void main(String[] args) {

        String version = "";
        for (int i = 0; i < args.length; i++) {
            if ("--type".equals(args[i]) && i + 1 < args.length) {
                version = args[i + 1];
            }
        }

        int exitCode = 0;

        final String hostEnv = System.getenv("CANTOR_SERVER_HOST");
        final String host = (hostEnv != null) ? hostEnv : "localhost";

        final String portEnv = System.getenv("CANTOR_SERVER_PORT");
        final int port = (portEnv != null) ? Integer.parseInt(portEnv) : 7443;

        ServerManager serverManager = new ServerManager(host, port);
        try {
            serverManager.connect();
            Cantor client = serverManager.getClient();

            RealDataEventsTest.setCantor(client);

            TimingListener timingListener = new TimingListener();
            TestNG testng = new TestNG();
            testng.setTestClasses(new Class<?>[]{RealDataEventsTest.class});
            testng.addListener(timingListener);
            testng.run();

            if (testng.getStatus() != 0) {
                exitCode = 1;
            }

            Map<String, List<Long>> methods = new LinkedHashMap<>();
            for (TimingListener.TestTiming t : timingListener.getResults()) {
                String key = t.getMethodName();
                List<Long> values = methods.get(key);
                if (values == null) {
                    values = new ArrayList<>();
                    methods.put(key, values);
                }
                values.add(t.getDurationMs());
            }

            List<BenchmarkStats> stats = new ArrayList<>();
            for (Map.Entry<String, List<Long>> e : methods.entrySet()) {
                stats.add(new BenchmarkStats(NO_EVENT_COUNT, e.getKey(), e.getValue()));
            }


            final String reportsDir = "cantor-integration/reports/real-data-results";
            final String reportPath = reportsDir + "/" + version + ".csv";
            CSVReporter.generate(reportPath, stats);
            logger.info("Performance report generated at {}", reportPath);

            final String htmlPath = reportsDir + "/cantor-realdata-performance-metric.html";
            RealDataHTMLReporter.generate(reportsDir, htmlPath);
            logger.info("HTML report generated at {}", htmlPath);

            logger.info("All tests completed.");

        } catch (Exception e) {
            logger.error("ERROR: {}", e.getMessage(), e);
            exitCode = 1;
        }

        logger.info("Done.");

        System.exit(exitCode);
    }
}

package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.TestNG;

import java.util.*;

/**
 * Integration test harness for Cantor.
 * Usage: ./integration-test --type CantorOnH2
 */
public class IntegrationTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestRunner.class);

    public static void main(String[] args) {

        // Parse command-line arguments
        String version = "CantorOnH2"; // default

        for (int i = 0; i < args.length; i++) {
            if ("--type".equals(args[i]) && i + 1 < args.length) {
                version = args[i + 1];
            }
        }

        // Cantor's config expects the backend as a bare storage type (h2/mysql/s3),
        // so translate CantorOn<Version> -> <version> lowercased.
        final String storageType = toStorageType(version);

        int exitCode = 0;

        logger.info("CANTOR INTEGRATION TEST on {}", storageType);

        final String host = System.getenv("CANTOR_SERVER_HOST");
        final int port = Integer.parseInt(System.getenv("CANTOR_SERVER_PORT"));

        ServerManager serverManager = new ServerManager(host, port);
        try {
            serverManager.connect();
            Cantor client = serverManager.getClient();

            // Run tests
            logger.info("Running tests...");

            // The test classes live in src/test/java, which the main source set
            // cannot reference at compile time. Load them by name at runtime
            // (they must be on the runtime classpath, hence exec.classpathScope=test
            // in the ./integration-test launcher).
            final String[] testClassNames = {
                    "com.salesforce.cantor.integration.IntegrationEventsTest",
                    "com.salesforce.cantor.integration.IntegrationObjectsTest",
                    "com.salesforce.cantor.integration.IntegrationSetsTest"
            };

            final Class<?>[] testClasses = new Class<?>[testClassNames.length];
            for (int i = 0; i < testClassNames.length; i++) {
                final Class<?> testClass = Class.forName(testClassNames[i]);
                // inject the client via the static setCantor(Cantor) method
                testClass.getMethod("setCantor", Cantor.class).invoke(null, client);
                testClasses[i] = testClass;
            }

            TimingListener timingListener = new TimingListener();

            TestNG testng = new TestNG();
            testng.setTestClasses(testClasses);
            testng.addListener(timingListener);
            testng.run();

            if (testng.getStatus() != 0) {
                exitCode = 1;
            }

            Map<String, List<Long>> methods = new LinkedHashMap<>();
            for (TimingListener.TestTiming t : timingListener.getResults()) {
                String key = t.getClassName() + "." + t.getMethodName();
                List<Long> values = methods.get(key);
                if (values == null) {
                    values = new ArrayList<>();
                    methods.put(key, values);
                }
                values.add(t.getDurationMs());
            }

            List<BenchmarkStats> stats = new ArrayList<>();
            for (Map.Entry<String, List<Long>> e : methods.entrySet()) {
                stats.add(new BenchmarkStats(e.getKey(), e.getValue()));
            }

            final String reportPath = "cantor-integration/reports/" + version + ".csv";
            CSVReporter.generate(reportPath, stats);
            logger.info("Performance report generated at {}", reportPath);

            logger.info("All tests completed.");

        } catch (Exception e) {
            logger.error("ERROR: {}", e.getMessage(), e);
            exitCode = 1;
        }

        logger.info("Done.");

        System.exit(exitCode);
    }

    /**
     * Translates a user-supplied Cantor version (e.g. "CantorOnH2") into the
     * bare storage type Cantor's config expects (e.g. "h2"). Accepts either the
     * CantorOn<Version> form or a plain type like "h2".
     */
    private static String toStorageType(final String version) {
        String type = version.trim();
        if (type.regionMatches(true, 0, "CantorOn", 0, "CantorOn".length())) {
            type = type.substring("CantorOn".length());
        }
        return type.toLowerCase();
    }
}

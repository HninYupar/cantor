package com.salesforce.cantor.integration;

import com.salesforce.cantor.Cantor;
import org.testng.TestNG;

/**
 * Integration test harness for Cantor.
 * Usage: ./integration-test --type CantorOnH2
 */
public class IntegrationTestRunner {

    public static void main(String[] args) {

        // Parse command-line arguments
        // e.g. --type CantorOnH2 / CantorOnMysql / CantorOnS3
        String version = "CantorOnH2"; // default

        for (int i = 0; i < args.length; i++) {
            if ("--type".equals(args[i]) && i + 1 < args.length) {
                version = args[i + 1];
            }
        }

        // Cantor's config expects the backend as a bare storage type (h2/mysql/s3),
        // so translate CantorOn<Version> -> <version> lowercased.
        final String storageType = toStorageType(version);

        System.out.println("CANTOR INTEGRATION TEST HARNESS");
        System.out.println("Version: " + version + " (storage type: " + storageType + ")");
        System.out.println();

        // Start server
        ServerManager serverManager = new ServerManager(storageType);
        try {
            serverManager.start();
            Cantor client = serverManager.getClient();

            // Run tests
            System.out.println("Running tests");

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

            TestNG testng = new TestNG();
            testng.setTestClasses(testClasses);
            testng.run();

            System.out.println();
            System.out.println("All tests completed.");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always tear down, even if tests fail
            serverManager.stop();
        }

        System.out.println("Done.");

        // Force the JVM to exit. The gRPC client (CantorOnGrpc) starts a
        // background channel-refresh thread that has no public shutdown hook,
        // so it keeps the process alive after main() returns. Since this is a
        // one-shot CLI tool, exit explicitly once teardown is complete.
        System.exit(0);
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

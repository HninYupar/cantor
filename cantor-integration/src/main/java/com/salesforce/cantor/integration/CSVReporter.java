package com.salesforce.cantor.integration;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.io.IOException;

/**
 * Writes performance results to a CSV file.
 */

public class CSVReporter {

    public static void generate(final String outputPath, final List<TimingListener.TestTiming> results) throws IOException{
        final Path path = Paths.get(outputPath);

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {

            writer.println("Class,Method,Duration(ms),Status");
            for (final TimingListener.TestTiming t : results) {
                writer.println(String.format("%s,%s,%d,%s",
                        t.getClassName(), t.getMethodName(), t.getDurationMs(), t.getStatus()));
            }
        }
    }
}

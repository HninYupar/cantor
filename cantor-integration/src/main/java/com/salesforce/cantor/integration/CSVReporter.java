package com.salesforce.cantor.integration;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.io.IOException;

/*
 * Writes performance results to a CSV file.
 */

public class CSVReporter {

    public static void generate(final String outputPath, final List<BenchmarkStats> stats) throws IOException {
        final Path path = Paths.get(outputPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
            writer.println("EventCount,Method,Count,Sum,Avg,Min,Max,P50,P90,P95,P99");
            for (final BenchmarkStats s : stats) {
                writer.println(String.format("%d,%s,%d,%d,%.1f,%d,%d,%d,%d,%d,%d",
                        s.getEventCount(), s.getName(), s.getCount(), s.getSum(), s.getAvg(),
                        s.getMin(), s.getMax(),
                        s.getP50(), s.getP90(), s.getP95(), s.getP99()));
            }
        }
    }
}

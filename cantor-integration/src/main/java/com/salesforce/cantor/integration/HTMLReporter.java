package com.salesforce.cantor.integration;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * Reads the per-storage CSV reports from the reports folder and creates a
 * single HTML report. The report is auto regenerated on every test run.
 *
 * To regenerate manually, run the following command from the cantor-integration directory:
 *   mvn compile exec:java@html-report
 */
public class HTMLReporter {

    private static final String TITLE = "Cantor Performance Metric";
    private static final String DESCRIPTION =
            "This report shows the performance of each Cantor instance run on the following tests. "
                    + "Performance is measured as the latency per operation, in milliseconds. "
                    + "For Cantor on S3, we compare two approaches: S3 Select and Client-side Select.";

    private static final String[][] TEST_CASES = {
            {"getAll", "retrieve all stored events"},
            {"getMetadata", "get the value of a metadata key for each event"},
            {"getDimension", "query events that have a given dimension key"},
            {"getMetadataExactMatch", "query events whose metadata contains a key with an exact value"},
            {"getMetadataPatternMatch",
                    "query events whose metadata contains a key with a value matching a prefix pattern"},
            {"getDimensionExactMatch", "query events whose dimension contains a key with an exact value"},
            {"getDimensionRangeMatch",
                    "query events whose dimension contains a key with a value that falls within an inclusive range"},
    };

    private static final String H2_LABEL = "H2";
    private static final String MYSQL_LABEL = "MySQL";
    private static final String S3_LABEL = "S3";
    private static final String S3_SELECT_LABEL = "S3 Select";
    private static final String LOCAL_SELECT_LABEL = "Local Select";
    private static final String CLIENT_SIDE_LABEL = "Client-Side Select";

    // Chart colors for the S3 Select vs Client-Side Select line chart.
    private static final String S3_COLOR = "#0066CC";        // blue  — S3 Select
    private static final String CLIENT_COLOR = "#6D8764";    // green — Client-Side Select

    private static final String H2_CSV = "CantorOnH2.csv";
    private static final String MYSQL_CSV = "CantorOnMySQL.csv";
    private static final String S3_SELECT_CSV = "CantorOnS3-S3Select.csv";
    private static final String CLIENT_SIDE_CSV = "CantorOnS3-LocalSelect.csv";

    // Columns shown in each table (everything except EventCount, which is the group heading).
    private static final String[] DISPLAY_COLUMNS =
            {"Method", "Count", "Sum", "Avg", "Min", "Max", "P50", "P90", "P95", "P99"};

    // CSV column indices: EventCount,Method,Count,Sum,Avg,Min,Max,P50,P90,P95,P99
    private static final int IDX_METHOD = 1;
    private static final int IDX_AVG = 4;
    private static final int IDX_P50 = 7;
    private static final int IDX_P90 = 8;
    private static final int IDX_P99 = 10;

    public static void main(final String[] args) throws IOException {
        final String reportsDir = args.length > 0 ? args[0] : "reports";
        final String outputPath = args.length > 1
                ? args[1]
                : Paths.get(reportsDir, "cantor-performance-metric.html").toString();
        generate(reportsDir, outputPath);
        System.out.println("Wrote " + Paths.get(outputPath).toAbsolutePath());
    }

    /*
     * Reads the per-storage report CSVs under reportsDir and writes the HTML report.
     */
    public static void generate(final String reportsDir, final String outputPath) throws IOException {
        // EventCount -> (Method -> row cells), preserving file order for both keys.
        final Map<String, Map<String, String[]>> h2 = readGrouped(Paths.get(reportsDir, H2_CSV));
        final Map<String, Map<String, String[]>> mysql = readGrouped(Paths.get(reportsDir, MYSQL_CSV));
        final Map<String, Map<String, String[]>> s3 = readGrouped(Paths.get(reportsDir, S3_SELECT_CSV));
        final Map<String, Map<String, String[]>> clientSide =
                readGrouped(Paths.get(reportsDir, CLIENT_SIDE_CSV));

        final StringBuilder body = new StringBuilder();

        // Single-storage sections. Skip a section entirely when its CSV is
        // missing or empty, rather than rendering a "No data found" note.
        if (!h2.isEmpty()) {
            body.append(renderSection(H2_LABEL, H2_CSV, h2));
            body.append('\n');
        }
        if (!mysql.isEmpty()) {
            body.append(renderSection(MYSQL_LABEL, MYSQL_CSV, mysql));
            body.append('\n');
        }

        // S3 section: two approaches (S3 Select and Local Select) plus a comparison.
        // Skip each sub-tile whose CSV is missing/empty, and only render the
        // comparison table/chart when both approaches have data.
        final StringBuilder s3Inner = new StringBuilder();
        if (!s3.isEmpty()) {
            s3Inner.append(renderSubSection(S3_SELECT_LABEL, S3_SELECT_CSV, s3));
        }
        if (!clientSide.isEmpty()) {
            s3Inner.append(renderSubSection(LOCAL_SELECT_LABEL, CLIENT_SIDE_CSV, clientSide));
        }
        if (!s3.isEmpty() && !clientSide.isEmpty()) {
            s3Inner.append(renderComparison(s3, clientSide));
        }
        if (s3Inner.length() > 0) {
            body.append(renderSectionWithBody(S3_LABEL, s3Inner.toString()));
        }

        final Path out = Paths.get(outputPath);
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(out))) {
            writer.print(buildHtml(body.toString()));
        }
    }

    /*
     * Reads a report CSV into EventCount -> (Method -> cells). Returns an empty
     * map if the file is missing.
     */
    private static Map<String, Map<String, String[]>> readGrouped(final Path csvPath) throws IOException {
        final Map<String, Map<String, String[]>> byEventCount = new LinkedHashMap<>();
        if (!Files.exists(csvPath)) {
            return byEventCount;
        }
        final List<String> lines = Files.readAllLines(csvPath);
        for (int i = 1; i < lines.size(); i++) { // skip header
            final String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            final String[] cells = line.split(",", -1);
            final String eventCount = cells[0].trim();
            final String method = cells.length > IDX_METHOD ? cells[IDX_METHOD].trim() : "";
            byEventCount.computeIfAbsent(eventCount, k -> new LinkedHashMap<>()).put(method, cells);
        }
        return byEventCount;
    }

    /*
     * A full section (top-level <h2> title) whose body is the EventCount groups
     * read from a single CSV. Used for the single-storage sections (H2, MySQL).
     */
    private static String renderSection(final String heading,
                                        final String csvFileName,
                                        final Map<String, Map<String, String[]>> data) {
        return renderSectionWithBody(heading, renderGroups(csvFileName, data));
    }

    /*
     * A full section (top-level <h2> title) wrapping arbitrary inner HTML. Used
     * for the S3 section, which holds two sub-sections plus a comparison.
     */
    private static String renderSectionWithBody(final String heading, final String innerHtml) {
        return "  <section class=\"approach\">\n"
                + "    <h2 class=\"approach-title\">" + escape(heading) + "</h2>\n"
                + innerHtml
                + "  </section>\n";
    }

    /*
     * A sub-section (nested <h3> subtitle) whose body is the EventCount groups
     * read from a single CSV. Used for the S3 Select / Local Select approaches.
     */
    private static String renderSubSection(final String subheading,
                                           final String csvFileName,
                                           final Map<String, Map<String, String[]>> data) {
        return "    <div class=\"sub-approach\">\n"
                + "      <h3 class=\"sub-approach-title\">" + escape(subheading) + "</h3>\n"
                + renderGroups(csvFileName, data)
                + "    </div>\n";
    }

    /*
     * Renders the EventCount groups (one table per EventCount) for a single CSV,
     * or a "missing" note when there is no data.
     */
    private static String renderGroups(final String csvFileName,
                                       final Map<String, Map<String, String[]>> data) {
        final StringBuilder groups = new StringBuilder();
        if (data.isEmpty()) {
            groups.append("    <p class=\"missing\">No data found in ")
                  .append(escape(csvFileName))
                  .append("</p>\n");
        } else {
            for (final Map.Entry<String, Map<String, String[]>> entry : data.entrySet()) {
                groups.append("    <div class=\"group\">\n")
                      .append("      <h3>Number of Events: <strong>")
                      .append(escape(entry.getKey()))
                      .append("</strong></h3>\n")
                      .append(renderTable(entry.getValue().values()))
                      .append("    </div>\n");
            }
        }
        return groups.toString();
    }

    /*
     * Renders a table for a single EventCount group. Drops column 0 (EventCount).
     */
    private static String renderTable(final Iterable<String[]> rows) {
        final StringBuilder head = new StringBuilder();
        for (final String col : DISPLAY_COLUMNS) {
            head.append("<th>").append(escape(col)).append("</th>");
        }

        final StringBuilder body = new StringBuilder();
        for (final String[] cells : rows) {
            body.append("<tr>");
            for (int c = 1; c <= DISPLAY_COLUMNS.length; c++) { // skip EventCount at index 0
                body.append("<td>").append(escape(value(cells, c))).append("</td>");
            }
            body.append("</tr>\n");
        }

        return "      <table>\n"
                + "        <thead><tr>" + head + "</tr></thead>\n"
                + "        <tbody>\n" + body + "        </tbody>\n"
                + "      </table>\n";
    }

    /*
     * Side-by-side comparison: for each EventCount and method, show S3 Select vs
     * Client-Side Select on Avg / P50 / P90 / P99, highlighting the faster value
     * in each metric (lower is better).
     */
    private static String renderComparison(final Map<String, Map<String, String[]>> s3,
                                           final Map<String, Map<String, String[]>> clientSide) {
        final List<String> eventCounts = union(s3.keySet(), clientSide.keySet());
        final StringBuilder groups = new StringBuilder();

        for (final String ec : eventCounts) {
            final Map<String, String[]> s3m = s3.getOrDefault(ec, new LinkedHashMap<>());
            final Map<String, String[]> csm = clientSide.getOrDefault(ec, new LinkedHashMap<>());
            final List<String> methods = union(s3m.keySet(), csm.keySet());

            final StringBuilder rows = new StringBuilder();
            for (final String method : methods) {
                final String[] a = s3m.get(method);
                final String[] b = csm.get(method);

                rows.append("<tr>")
                    .append("<td>").append(escape(method)).append("</td>")
                    .append(metricPair(a, b, IDX_AVG))
                    .append(metricPair(a, b, IDX_P50))
                    .append(metricPair(a, b, IDX_P90))
                    .append(metricPair(a, b, IDX_P99))
                    .append("</tr>\n");
            }

            groups.append("    <div class=\"group\">\n")
                  .append("      <h3>Number of Events: <strong>").append(escape(ec)).append("</strong></h3>\n")
                  .append(renderComparisonChart(methods, s3m, csm))
                  .append("      <table class=\"cmp\">\n")
                  .append("        <thead>\n")
                  .append("          <tr><th rowspan=\"2\">Method</th>"
                          + "<th colspan=\"2\">Avg</th>"
                          + "<th colspan=\"2\">P50</th>"
                          + "<th colspan=\"2\">P90</th>"
                          + "<th colspan=\"2\">P99</th></tr>\n")
                  .append("          <tr><th>S3</th><th>Client</th>"
                          + "<th>S3</th><th>Client</th>"
                          + "<th>S3</th><th>Client</th>"
                          + "<th>S3</th><th>Client</th></tr>\n")
                  .append("        </thead>\n")
                  .append("        <tbody>\n").append(rows).append("        </tbody>\n")
                  .append("      </table>\n")
                  .append("    </div>\n");
        }

        if (groups.length() == 0) {
            groups.append("    <p class=\"missing\">No data to compare.</p>\n");
        }

        return "    <div class=\"sub-approach\">\n"
                + "      <h3 class=\"sub-approach-title\">S3 Select vs Client-Side Select</h3>\n"
                + groups
                + "    </div>\n";
    }

    /*
     * Renders an SVG combo chart for one EventCount group comparing S3 Select vs
     * Client-Side Select. Methods are on the x-axis. Each approach is drawn twice:
     * P99 latency as a bar (scaled to the left y-axis) and Avg latency as a line
     * (scaled to the right y-axis). The two metrics get independent axes because
     * P99 (tail) is much larger than Avg. Lower is better (latency).
     */
    private static String renderComparisonChart(final List<String> methods,
                                                 final Map<String, String[]> s3m,
                                                 final Map<String, String[]> csm) {
        if (methods.isEmpty()) {
            return "";
        }

        final int n = methods.size();

        // Bars show P99 (left axis); lines show Avg (right axis). NaN when missing.
        final double[] s3Avg = new double[n];
        final double[] csAvg = new double[n];
        final double[] s3P99 = new double[n];
        final double[] csP99 = new double[n];
        double avgMax = 0.0;
        double p99Max = 0.0;
        boolean anyValue = false;
        for (int i = 0; i < n; i++) {
            s3Avg[i] = number(s3m.get(methods.get(i)), IDX_AVG);
            csAvg[i] = number(csm.get(methods.get(i)), IDX_AVG);
            s3P99[i] = number(s3m.get(methods.get(i)), IDX_P99);
            csP99[i] = number(csm.get(methods.get(i)), IDX_P99);
            if (!Double.isNaN(s3Avg[i])) {
                avgMax = Math.max(avgMax, s3Avg[i]);
                anyValue = true;
            }
            if (!Double.isNaN(csAvg[i])) {
                avgMax = Math.max(avgMax, csAvg[i]);
                anyValue = true;
            }
            if (!Double.isNaN(s3P99[i])) {
                p99Max = Math.max(p99Max, s3P99[i]);
                anyValue = true;
            }
            if (!Double.isNaN(csP99[i])) {
                p99Max = Math.max(p99Max, csP99[i]);
                anyValue = true;
            }
        }
        if (!anyValue) {
            return "";
        }
        if (avgMax <= 0.0) {
            avgMax = 1.0;
        }
        if (p99Max <= 0.0) {
            p99Max = 1.0;
        }

        // Chart geometry.
        final int height = 340;
        final int padLeft = 90;
        final int padRight = 90;
        final int padTop = 14;
        final int padBottom = 100;
        final int plotHeight = height - padTop - padBottom;
        // Fixed spacing between methods; a small inset keeps the first group off
        // the y-axis. The plot width is sized to the content so there is no large
        // empty gap before the first group or after the last.
        final int step = 90;
        final int inset = 30;
        final double xStart = padLeft + inset;
        final int plotWidth = inset * 2 + step * (n - 1);
        final int width = padLeft + plotWidth + padRight;
        final int plotBottom = padTop + plotHeight;
        final int plotRight = padLeft + plotWidth;

        final StringBuilder svg = new StringBuilder();
        svg.append("      <div class=\"chart\">\n")
           .append("        <svg viewBox=\"0 0 ").append(width).append(' ').append(height)
           .append("\" role=\"img\" class=\"cmp-chart\" preserveAspectRatio=\"xMidYMid meet\">\n");

        // Horizontal gridlines with dual y-axis labels (5 ticks):
        // left labels use the P99 scale, right labels use the Avg scale.
        final int ticks = 5;
        for (int t = 0; t <= ticks; t++) {
            final double frac = (double) t / ticks;
            final double y = padTop + plotHeight * (1.0 - frac);
            svg.append("          <line x1=\"").append(padLeft).append("\" y1=\"").append(fmt(y))
               .append("\" x2=\"").append(plotRight).append("\" y2=\"").append(fmt(y))
               .append("\" class=\"grid\"/>\n");
            svg.append("          <text x=\"").append(padLeft - 8).append("\" y=\"").append(fmt(y + 4))
               .append("\" class=\"y-label\">").append(escape(fmt(p99Max * frac))).append("</text>\n");
            svg.append("          <text x=\"").append(plotRight + 8).append("\" y=\"").append(fmt(y + 4))
               .append("\" class=\"y-label-right\">").append(escape(fmt(avgMax * frac))).append("</text>\n");
        }

        // Axes: left (P99), bottom, and right (Avg).
        svg.append("          <line x1=\"").append(padLeft).append("\" y1=\"").append(padTop)
           .append("\" x2=\"").append(padLeft).append("\" y2=\"").append(plotBottom)
           .append("\" class=\"axis\"/>\n");
        svg.append("          <line x1=\"").append(padLeft).append("\" y1=\"").append(plotBottom)
           .append("\" x2=\"").append(plotRight).append("\" y2=\"").append(plotBottom)
           .append("\" class=\"axis\"/>\n");
        svg.append("          <line x1=\"").append(plotRight).append("\" y1=\"").append(padTop)
           .append("\" x2=\"").append(plotRight).append("\" y2=\"").append(plotBottom)
           .append("\" class=\"axis\"/>\n");

        // Y-axis titles: left = P99 latency, right = Avg latency.
        final int titleY = padTop + plotHeight / 2;
        final int leftTitleX = 18;
        svg.append("          <text x=\"").append(leftTitleX).append("\" y=\"").append(titleY)
           .append("\" class=\"axis-title\" transform=\"rotate(-90 ").append(leftTitleX).append(' ')
           .append(titleY).append(")\">P99 latency</text>\n");
        final int rightTitleX = width - 8;
        svg.append("          <text x=\"").append(rightTitleX).append("\" y=\"").append(titleY)
           .append("\" class=\"axis-title\" transform=\"rotate(90 ").append(rightTitleX).append(' ')
           .append(titleY).append(")\">Avg latency</text>\n");

        // X-axis method labels (rotated to avoid overlap).
        for (int i = 0; i < n; i++) {
            final double x = xStart + step * i;
            final double labelY = plotBottom + 14;
            svg.append("          <text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(labelY))
               .append("\" class=\"x-label\" transform=\"rotate(35 ").append(fmt(x)).append(' ')
               .append(fmt(labelY)).append(")\">").append(escape(methods.get(i))).append("</text>\n");
        }

        // P99 bars (left axis), grouped per method: S3 left, Client right.
        svg.append(renderBars(s3P99, csP99, step, xStart, padTop, plotHeight, plotBottom, p99Max));

        // Avg lines (right axis), drawn on top of the bars.
        svg.append(renderSeries(s3Avg, step, xStart, padTop, plotHeight, avgMax, S3_COLOR));
        svg.append(renderSeries(csAvg, step, xStart, padTop, plotHeight, avgMax, CLIENT_COLOR));

        svg.append("        </svg>\n");

        // Legend: color = approach; bar = P99, line = Avg.
        svg.append("        <div class=\"legend\">\n")
           .append("          <span class=\"legend-item\"><span class=\"swatch\" style=\"background:")
           .append(S3_COLOR).append("\"></span>").append(escape(S3_SELECT_LABEL)).append("</span>\n")
           .append("          <span class=\"legend-item\"><span class=\"swatch\" style=\"background:")
           .append(CLIENT_COLOR).append("\"></span>").append(escape(CLIENT_SIDE_LABEL)).append("</span>\n")
           .append("          <span class=\"legend-item\"><span class=\"glyph-bar\"></span>Bar = P99</span>\n")
           .append("          <span class=\"legend-item\"><span class=\"glyph-line\"></span>Line = Avg</span>\n")
           .append("        </div>\n")
           .append("      </div>\n");

        return svg.toString();
    }

    /*
     * Renders one series as a polyline through the defined points plus a circle
     * marker at each point. Missing values (NaN) break the line into segments.
     */
    private static String renderSeries(final double[] vals,
                                       final double step,
                                       final double xStart,
                                       final int padTop,
                                       final int plotHeight,
                                       final double max,
                                       final String color) {
        final StringBuilder out = new StringBuilder();
        final StringBuilder segment = new StringBuilder();
        final StringBuilder points = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            if (Double.isNaN(vals[i])) {
                if (segment.length() > 0) {
                    out.append("          <polyline class=\"series\" points=\"").append(segment)
                       .append("\" style=\"stroke:").append(color).append("\"/>\n");
                    segment.setLength(0);
                }
                continue;
            }
            final double x = xStart + step * i;
            final double y = padTop + plotHeight * (1.0 - vals[i] / max);
            if (segment.length() > 0) {
                segment.append(' ');
            }
            segment.append(fmt(x)).append(',').append(fmt(y));
            points.append("          <circle cx=\"").append(fmt(x)).append("\" cy=\"").append(fmt(y))
                  .append("\" r=\"4\" style=\"fill:").append(color).append("\">")
                  .append("<title>").append(escape(fmt(vals[i]))).append("</title></circle>\n");
        }
        if (segment.length() > 0) {
            out.append("          <polyline class=\"series\" points=\"").append(segment)
               .append("\" style=\"stroke:").append(color).append("\"/>\n");
        }
        out.append(points);
        return out.toString();
    }

    /*
     * Renders grouped P99 bars for one EventCount group: two bars per method
     * (S3 Select on the left, Client-Side Select on the right), scaled to the
     * left (P99) axis. Missing values (NaN) are skipped.
     */
    private static String renderBars(final double[] s3P99,
                                     final double[] csP99,
                                     final double step,
                                     final double xStart,
                                     final int padTop,
                                     final int plotHeight,
                                     final int plotBottom,
                                     final double max) {
        final double barWidth = 15.0;
        final double gap = 2.0;
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < s3P99.length; i++) {
            final double center = xStart + step * i;
            out.append(bar(center - barWidth - gap / 2.0, barWidth, s3P99[i],
                    padTop, plotHeight, plotBottom, max, S3_COLOR));
            out.append(bar(center + gap / 2.0, barWidth, csP99[i],
                    padTop, plotHeight, plotBottom, max, CLIENT_COLOR));
        }
        return out.toString();
    }

    /*
     * Renders a single P99 bar. Semi-transparent so the Avg lines drawn on top
     * stay readable. Returns "" for a missing (NaN) value.
     */
    private static String bar(final double x,
                              final double barWidth,
                              final double value,
                              final int padTop,
                              final int plotHeight,
                              final int plotBottom,
                              final double max,
                              final String color) {
        if (Double.isNaN(value)) {
            return "";
        }
        final double y = padTop + plotHeight * (1.0 - value / max);
        final double h = plotBottom - y;
        return "          <rect x=\"" + fmt(x) + "\" y=\"" + fmt(y)
                + "\" width=\"" + fmt(barWidth) + "\" height=\"" + fmt(h)
                + "\" fill=\"" + color + "\" fill-opacity=\"0.35\">"
                + "<title>" + escape(fmt(value)) + "</title></rect>\n";
    }

    private static String fmt(final double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ---- helpers ---------------------------------------------------------

    private static String renderTestCases() {
        final StringBuilder out = new StringBuilder();
        for (final String[] tc : TEST_CASES) {
            out.append("      <li><code>").append(escape(tc[0])).append("</code> &mdash; ")
               .append(escape(tc[1])).append("</li>\n");
        }
        return out.toString();
    }

    /*
     * Renders the S3 and Client-Side cells for one metric column, highlighting
     * whichever value is lower (faster). Returns two <td> cells.
     */
    private static String metricPair(final String[] a, final String[] b, final int idx) {
        final double s3 = number(a, idx);
        final double cs = number(b, idx);

        String s3Class = "";
        String csClass = "";
        if (!Double.isNaN(s3) && !Double.isNaN(cs)) {
            if (s3 < cs) {
                s3Class = " class=\"win\"";
            } else if (cs < s3) {
                csClass = " class=\"win\"";
            }
        }

        return "<td" + s3Class + ">" + escape(value(a, idx)) + "</td>"
                + "<td" + csClass + ">" + escape(value(b, idx)) + "</td>";
    }

    private static List<String> union(final Iterable<String> first, final Iterable<String> second) {
        final List<String> out = new ArrayList<>();
        for (final String s : first) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        for (final String s : second) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static String value(final String[] cells, final int idx) {
        return cells != null && idx < cells.length ? cells[idx].trim() : "";
    }

    private static double number(final String[] cells, final int idx) {
        final String raw = value(cells, idx);
        if (raw.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String buildHtml(final String body) {
        return "<!DOCTYPE html>\n"
            + "<html lang=\"en\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
            + "<title>" + escape(TITLE) + "</title>\n"
            + "<style>\n"
            + "  :root {\n"
            + "    --bg: #f5f7fa; --card: #ffffff; --border: #e2e8f0; --text: #1a202c;\n"
            + "    --muted: #64748b; --accent: #2563eb; --accent-soft: #eff6ff;\n"
            + "    --head: #1e293b; --row-alt: #f8fafc;\n"
            + "    --shadow: 0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.04);\n"
            + "  }\n"
            + "  * { box-sizing: border-box; }\n"
            + "  body { margin: 0; padding: 2.5rem 1.5rem 4rem;\n"
            + "    font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif;\n"
            + "    background: var(--bg); color: var(--text); line-height: 1.5; }\n"
            + "  .container { max-width: 1000px; margin: 0 auto; }\n"
            + "  header.page { text-align: center; margin-bottom: 1.5rem; }\n"
            + "  header.page h1 { font-size: 2rem; font-weight: 700; margin: 0 0 0.5rem; letter-spacing: -0.02em; }\n"
            + "  section.description { background: var(--card); border: 1px solid var(--border);\n"
            + "    border-radius: 10px; box-shadow: var(--shadow); padding: 1.25rem 1.5rem; margin-bottom: 2.5rem; }\n"
            + "  section.description p { margin: 0; color: var(--text); font-size: 0.95rem; }\n"
            + "  section.description strong { color: var(--accent); }\n"
            + "  h3.desc-title { font-size: 1rem; font-weight: 700; margin: 1.1rem 0 0.5rem; color: var(--head); }\n"
            + "  ul.test-cases { margin: 0; padding-left: 1.25rem; }\n"
            + "  ul.test-cases li { margin: 0.3rem 0; font-size: 0.9rem; color: var(--text); }\n"
            + "  ul.test-cases code { background: var(--accent-soft); color: var(--accent);\n"
            + "    font-weight: 600; padding: 0.1rem 0.4rem; border-radius: 5px; font-size: 0.85rem; }\n"
            + "  section.approach { margin-bottom: 3rem; }\n"
            + "  h2.approach-title { font-size: 1.4rem; font-weight: 700; margin: 0 0 1.25rem;\n"
            + "    padding-bottom: 0.5rem; border-bottom: 3px solid var(--accent); display: inline-block; }\n"
            + "  .sub-approach { margin: 0 0 2rem; }\n"
            + "  h3.sub-approach-title { font-size: 1.1rem; font-weight: 700; margin: 0 0 1rem;\n"
            + "    color: var(--head); padding-bottom: 0.3rem; border-bottom: 2px solid var(--border);\n"
            + "    display: inline-block; }\n"
            + "  p.missing { color: #b91c1c; font-size: 0.9rem; }\n"
            + "  .group { background: var(--card); border: 1px solid var(--border); border-radius: 10px;\n"
            + "    box-shadow: var(--shadow); margin-bottom: 1.5rem; overflow: hidden; }\n"
            + "  .group h3 { font-size: 1.05rem; font-weight: 600; margin: 0; padding: 0.9rem 1.25rem;\n"
            + "    background: var(--accent-soft); color: var(--head); border-bottom: 1px solid var(--border); }\n"
            + "  table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }\n"
            + "  thead th { background: var(--head); color: #fff; font-weight: 600; text-align: right;\n"
            + "    padding: 0.6rem 0.9rem; white-space: nowrap; }\n"
            + "  thead th:first-child { text-align: left; }\n"
            + "  tbody td { padding: 0.55rem 0.9rem; text-align: right; border-bottom: 1px solid var(--border);\n"
            + "    font-variant-numeric: tabular-nums; }\n"
            + "  tbody td:first-child { text-align: left; font-weight: 500; }\n"
            + "  tbody tr:nth-child(even) { background: var(--row-alt); }\n"
            + "  tbody tr:last-child td { border-bottom: none; }\n"
            + "  tbody tr:hover { background: var(--accent-soft); }\n"
            + "  table.cmp thead th { text-align: center; border-left: 1px solid rgba(255,255,255,0.15); }\n"
            + "  table.cmp thead th:first-child { text-align: left; border-left: none; }\n"
            + "  table.cmp tbody td { text-align: center; }\n"
            + "  table.cmp tbody td:first-child { text-align: left; }\n"
            + "  td.win { font-weight: 700; }\n"
            + "  .chart { padding: 1rem 1.25rem 0.5rem; border-bottom: 1px solid var(--border); }\n"
            + "  .cmp-chart { width: 100%; height: auto; display: block; }\n"
            + "  .cmp-chart .grid { stroke: var(--border); stroke-width: 1; }\n"
            + "  .cmp-chart .axis { stroke: #000; stroke-width: 1.5; }\n"
            + "  .cmp-chart .series { fill: none; stroke-width: 2.5;\n"
            + "    stroke-linejoin: round; stroke-linecap: round; }\n"
            + "  .cmp-chart .y-label { fill: #000; font-size: 11px; text-anchor: end;\n"
            + "    font-variant-numeric: tabular-nums; }\n"
            + "  .cmp-chart .y-label-right { fill: #000; font-size: 11px; text-anchor: start;\n"
            + "    font-variant-numeric: tabular-nums; }\n"
            + "  .cmp-chart .x-label { fill: #000; font-size: 11px; text-anchor: start; }\n"
            + "  .cmp-chart .axis-title { fill: #000; font-size: 12px; font-weight: 700; text-anchor: middle; }\n"
            + "  .legend { display: flex; gap: 1.5rem; justify-content: center;\n"
            + "    padding: 0.25rem 0 0.75rem; font-size: 0.85rem; color: var(--text); }\n"
            + "  .legend-item { display: inline-flex; align-items: center; gap: 0.4rem; }\n"
            + "  .legend .swatch { width: 14px; height: 14px; border-radius: 3px; display: inline-block; }\n"
            + "  .legend .glyph-bar { width: 13px; height: 13px; border-radius: 2px; display: inline-block;\n"
            + "    background: repeating-linear-gradient(45deg, #94a3b8, #94a3b8 3px, #cbd5e1 3px, #cbd5e1 6px); }\n"
            + "  .legend .glyph-line { width: 16px; height: 0; border-top: 3px solid #94a3b8; display: inline-block; }\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class=\"container\">\n"
            + "  <header class=\"page\">\n"
            + "    <h1>" + escape(TITLE) + "</h1>\n"
            + "  </header>\n\n"
            + "  <section class=\"description\">\n"
            + "    <p>" + escape(DESCRIPTION) + "</p>\n"
            + "    <h3 class=\"desc-title\">Test Cases</h3>\n"
            + "    <ul class=\"test-cases\">\n"
            + renderTestCases()
            + "    </ul>\n"
            + "  </section>\n\n"
            + body + "\n"
            + "</div>\n"
            + "</body>\n"
            + "</html>\n";
    }

    private static String escape(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}

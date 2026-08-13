package com.salesforce.cantor.integration;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Reads the per-storage CSV reports from the reports folder and creates a
 * single HTML report.
 *
 * To regenerate manually, run the following command from the cantor-integration directory:
 *   mvn compile exec:java@html-report
 */
public class HTMLReporter {

    private static final String TITLE = "Cantor Performance Metric";
    private static final String DESCRIPTION =
            "This report shows the performance of Cantor on H2, MySQL, and S3. "
                    + "For S3, we compare S3 Select vs Client-side Select.";

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

    private static final String H2_CSV = "CantorOnH2.csv";
    private static final String MYSQL_CSV = "CantorOnMySQL.csv";
    private static final String S3_SELECT_CSV = "CantorOnS3.csv";
    private static final String CLIENT_SIDE_CSV = "CantorOnS3withSelector.csv";

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

        // Single-storage sections.
        body.append(renderSection(H2_LABEL, H2_CSV, h2));
        body.append('\n');
        body.append(renderSection(MYSQL_LABEL, MYSQL_CSV, mysql));
        body.append('\n');

        // S3 section: two approaches (S3 Select and Local Select) plus a comparison.
        final StringBuilder s3Inner = new StringBuilder();
        s3Inner.append(renderSubSection(S3_SELECT_LABEL, S3_SELECT_CSV, s3));
        s3Inner.append(renderSubSection(LOCAL_SELECT_LABEL, CLIENT_SIDE_CSV, clientSide));
        s3Inner.append(renderComparison(s3, clientSide));
        body.append(renderSectionWithBody(S3_LABEL, s3Inner.toString()));

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

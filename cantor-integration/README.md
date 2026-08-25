# cantor-integration

The `cantor-integration` module contains everything needed to run the integration
test harness. 

## Files

- `BenchmarkStats`: sorts the per-method timing samples from one backend/event-count run and computes the summary metrics (count, sum, avg, min, max, and P50/P90/P95/P99 percentiles)
- `CSVReporter` : writes a list of `BenchmarkStats` to its corresponding CSV file
- `HTMLReporter` : reads the CSV files in `reports/` and renders them all into a single HTML report
- `IntegrationTestRunner` : the main entry point; connects to the running Cantor server, runs the tests, saves the stats to CSV, and regenerates the HTML report
- `ServerManager` : opens and holds the gRPC client connection to the Cantor server
- `TimingListener` : a TestNG `ITestListener` that records each test method's duration and pass/fail/skip status

## Running Integration Tests

Integration tests spin up a real Cantor server (backed by H2, MySQL, or S3) in
Docker, run the test suite against it, and tear everything down at the end.

Run from the repository root:

```bash
./integration-test.sh --type <TYPE>
```

where `<TYPE>` is one of:

| Type            | Storage |
|-----------------|---------|
| `CantorOnH2`    | H2      |
| `CantorOnMySQL` | MySQL   |
| `CantorOnS3`    | S3      |

If `--type` is omitted, it defaults to `CantorOnH2`.

### Choosing the Select strategy (CantorOnS3 only)

For `CantorOnS3`, use `--select` to choose between
`s3` for S3 Select (server-side) and `local` for Local Select (client-side).
It defaults to `s3` and is ignored for H2 and MySQL.

```bash
./integration-test.sh --type CantorOnS3 --select s3
./integration-test.sh --type CantorOnS3 --select local
```

### Available flags

| Flag           | Description                                                                          |
|----------------|--------------------------------------------------------------------------------------|
| `-t, --type`   | Storage backend: `CantorOnH2`, `CantorOnMySQL`, or `CantorOnS3` (default `CantorOnH2`) |
| `-s, --select` | For CantorOnS3 only; `s3` (S3Select) or `local` (LocalSelect); default `s3`          |
| `-c, --config` | Path to a `cantor-server.conf` file (default `env/dockers/cantor/cantor-server.conf`) |
| `-h, --help`   | Show the full list of options and exit                                               |

## Reports

Each run writes results to `cantor-integration/reports/`:

- `<TYPE>.csv` - per-run metrics (count, sum, avg, min, max, P50, P90, P95, P99)
- `cantor-performance-metric.html` - a combined report

The HTML report is regenerated on every run. To regenerate it manually from the
existing CSVs:

```bash
cd cantor-integration
mvn compile exec:java@html-report
```
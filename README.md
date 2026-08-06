# Cantor

[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)

Documentation can be found here: https://opensource.salesforce.com/cantor/

### Running Integration Tests

Integration tests spin up a real Cantor server (backed by H2, MySQL, or S3) in Docker, run the test suite against it, and tear everything down automatically.

```bash
./integration-test.sh --type <TYPE>
```
where `<TYPE>` is one of:

| Type | Storage Type                                             |
|------|----------------------------------------------------------|
| `CantorOnH2` | H2                                                       |
| `CantorOnMySQL` | MySQL      |
| `CantorOnS3` | S3                                                       |

If `--type` is omitted, it defaults to `CantorOnH2`.

Run `./integration-test.sh --help` for the full list of options
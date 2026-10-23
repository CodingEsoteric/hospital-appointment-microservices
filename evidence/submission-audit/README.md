# Submission audit build and test output

## Successful full reactor run

Command: `mvn -o test`, run from the repository root with the IntelliJ IDEA 2026.2.2 Maven 3.9.16 distribution and JDK 24.0.2 (Maven compiler release remains 21). The full, successful Maven output is [full-reactor-rerun-output.txt](full-reactor-rerun-output.txt).

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Patient Service | 4 | 0 | 0 | 0 |
| Availability Service | 6 | 0 | 0 | 0 |
| Notification Service | 7 | 0 | 0 | 0 |
| Appointment Service | 29 | 0 | 0 | 0 |

The full reactor reported `BUILD SUCCESS` and finished at `2026-10-23T15:48:03+03:00`. These tests use the service-specific in-memory or temporary SQLite paths configured in test code; they do not target the repository's default database files.

## Earlier run and observed flake

The first full reactor attempt is retained in [full-reactor-test-output.txt](full-reactor-test-output.txt). It reached Notification Service but failed `NotificationHttpTests.concurrentDuplicateSubmissionsCreateOnePersistedEvent`: 19 responses had one `acceptedAt`, while one response had `2026-10-17T12:00:00Z`. The process exited before Appointment Service ran. A subsequent Notification-only run passed 7/7, and the full reactor rerun above passed 46/46. No service source change was made between these attempts. The intermittent mismatch was not reproduced, so it remains a test/concurrency flake to investigate rather than a resolved defect.

The successful capture was written with command-shell redirection so it remains readable. The initial PowerShell-redirection capture contains mixed encodings; the factual failure details above were read from its Maven Surefire report during the run.

## Reproduction

In PowerShell, use the repository's IntelliJ-configured JDK and Maven distribution:

```powershell
$env:JAVA_HOME='C:\Users\HP\.jdks\openjdk-24.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -o test
```

The project workspace is configured for offline Maven. This command uses that Maven/JDK setup and was run from the repository root. Challenge-specific outputs were left untouched.

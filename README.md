# Hospital Appointment Microservices

A Java 21, Spring Boot 3.4.5, Maven application with four independently runnable services. Services communicate through HTTP APIs and own separate SQLite databases. Appointment booking uses Availability Service reservation operations, a database-backed idempotency key, and bounded Notification Service retries. Notifications are simulated records; the system does not deliver email or SMS.

## Services

| Service | Port | Database owner | Main responsibility |
|---|---:|---|---|
| Appointment Service | 8081 | `appointment-service.db` | Booking orchestration, persisted idempotency, notification status |
| Patient Service | 8082 | `patient-service.db` | Patient records and eligibility |
| Availability Service | 8083 | `availability-service.db` | Doctors, slots, capacity-safe reservations and release |
| Notification Service | 8084 | `notification-service.db` | Persisted, deduplicated simulated notification acceptance |

Each database is local to its owning service. Services exchange IDs and data through REST and do not read one another's SQLite files. The SQLite files are created/updated at startup and are ignored by Git.

## Prerequisites and build

- JDK 21 or later (the Maven compiler release is 21)
- Maven 3.9 or later

From the repository root:

```powershell
mvn test
```

Run one service's tests with `mvn -pl services/<service-name> test`. Appointment's integration tests use Availability Service as a test dependency, so Maven also builds that module when needed. Tests use temporary or in-memory databases rather than the default development database files.

## Start the services

Open four terminals in the repository root. Start the services Appointment calls first, then start Appointment Service:

```powershell
mvn -pl services/patient-service spring-boot:run
mvn -pl services/availability-service spring-boot:run
mvn -pl services/notification-service spring-boot:run
mvn -pl services/appointment-service spring-boot:run
```

Each service can also be started independently. Appointment booking requires Patient and Availability Service. Notification Service can be disabled for booking-status purposes by setting `NOTIFICATION_SEND_PATH` to an empty string; this reports `UNCONFIGURED`.

Health checks are `GET /health` on each port. Springdoc provides `/swagger-ui/index.html` and `/v3/api-docs` for each service.

## Configuration

All values and defaults below come from the four `src/main/resources/application.yml` files. `SERVER_PORT` is read by every service. Database URLs are SQLite JDBC URLs; the defaults are relative to the process working directory.

| Variable | Service | Default |
|---|---|---|
| `SERVER_PORT` | all | service port shown above |
| `PATIENT_DATABASE_URL` | Patient | `jdbc:sqlite:./patient-service.db` |
| `AVAILABILITY_DATABASE_URL` | Availability | `jdbc:sqlite:./availability-service.db` |
| `APPOINTMENT_DATABASE_URL` | Appointment | `jdbc:sqlite:./appointment-service.db` |
| `NOTIFICATION_DATABASE_URL` | Notification | `jdbc:sqlite:./notification-service.db` |
| `PATIENT_SERVICE_URL` | Appointment | `http://localhost:8082` |
| `AVAILABILITY_SERVICE_URL` | Appointment | `http://localhost:8083` |
| `NOTIFICATION_SERVICE_URL` | Appointment | `http://localhost:8084` |
| `NOTIFICATION_SEND_PATH` | Appointment | `/api/notifications` |
| `DOWNSTREAM_CONNECT_TIMEOUT` | Appointment | `2s` |
| `DOWNSTREAM_RESPONSE_TIMEOUT` | Appointment | `3s` |
| `NOTIFICATION_MAX_ATTEMPTS` | Appointment | `3` |
| `NOTIFICATION_RETRY_INITIAL_DELAY` | Appointment | `100ms` |
| `NOTIFICATION_RETRY_MAX_DELAY` | Appointment | `2s` |
| `NOTIFICATION_RETRY_JITTER_CAP` | Appointment | `100ms` |

Appointment, Availability, and Notification configure SQLite's `busy_timeout` to 5000 ms. Hibernate `ddl-auto:update` creates/updates service tables. Appointment, Availability, and Notification also create their required unique indexes at startup. Patient Service relies on Hibernate schema initialization. To isolate a local run, set the relevant database variable to a different SQLite path, for example:

```powershell
$env:APPOINTMENT_DATABASE_URL='jdbc:sqlite:C:/hospital-data/appointment-dev.db'
```

See [API contracts](docs/api-contracts.md), [business rules](docs/business-rules.md), and [report outline](docs/report-outline.md). Challenge evidence and reproduction notes are under [`evidence/`](evidence/).

## Current scope and limitations

Appointment Service currently implements booking creation only. There is no appointment retrieval, cancellation endpoint, appointment status field, or cancellation lifecycle/state machine. Notification status (`UNCONFIGURED`, `SENT`, `FAILED`, or persisted `PENDING` when status persistence fails) is separate from appointment lifecycle. Notification events are durable local acceptance records, not external delivery. A lost response can lead to repeated HTTP submission; database deduplication returns the same event but does not provide exactly-once email/SMS delivery. The system does not implement distributed transactions or automatic crash-window reconciliation.

## Assignment 2 — Containerization and Deployment

This repository extends the Hospital Appointment Microservices system developed for Assignment 1. Both assignments share the same codebase. Assignment 2 adds Docker containerization, Docker Compose orchestration, Kubernetes deployment, and automated deployment validation.

### 1. Technology Stack

- Java 21
- Spring Boot 3.4.5
- Maven multi-module project
- SQLite databases, one per microservice
- Docker and Docker Compose
- Kubernetes with Minikube for local deployment
- PowerShell scripts for deployment testing

### 2. Dockerfiles and Container Architecture

Each microservice has its own Dockerfile and is built as a separate container image.

| Microservice | Port | Database volume |
|---|---:|---|
| Appointment Service | 8081 | `appointment-data` |
| Patient Service | 8082 | `patient-data` |
| Availability Service | 8083 | `availability-data` |
| Notification Service | 8084 | `notification-data` |

Dockerfiles are located at `services/<service-name>/Dockerfile`. The root `docker-compose.yml` defines the four services, their ports, environment variables, database volumes, and service dependencies.

Appointment Service communicates with the other services through Docker Compose service names. For example, Patient Service is accessed at `http://patient-service:8082` from within the Compose network.

Each service stores its SQLite database under `/data`, backed by a separate named volume. This preserves database files across container recreation and restart.

`.env.example` is a sample configuration file; the Compose file does not load it. Compose values are currently declared directly in each service's `environment` section.

### 3. Run the Application with Docker Compose

**Prerequisites:** Docker Desktop with Docker Compose, Java 21, and Maven for local builds and tests.

From the repository root, run:

```powershell
mvn clean package
docker compose up --build
```

To start the services in the background:

```powershell
docker compose up --build -d
```

Check the running containers and configured health checks:

```powershell
docker compose ps
```

Review service logs:

```powershell
docker compose logs
docker compose logs appointment-service
```

Check application health endpoints:

- Appointment: `http://localhost:8081/health`
- Patient: `http://localhost:8082/health`
- Availability: `http://localhost:8083/health`
- Notification: `http://localhost:8084/health`

Stop the application:

```powershell
docker compose down
```

The named database volumes are retained when using `docker compose down`. **Do not use `docker compose down -v` unless you intend to delete the persistent database volumes and their data.**

### 4. Automated Docker Tests

The following scripts validate the Docker Compose deployment:

| Script | Purpose |
|---|---|
| `scripts/test-docker-compose.ps1` | Checks service health, creates test data, books an appointment, validates notification processing, tests idempotent replay, and verifies HTTP 409 for reuse of an idempotency key with a different request. |
| `scripts/test-docker-persistence.ps1` | Creates a booking, restarts the Compose services, and verifies that the booking and reservation identifiers remain unchanged after replay. |

Run the scripts from the repository root in PowerShell:

```powershell
.\scripts\test-docker-compose.ps1
```

```powershell
.\scripts\test-docker-persistence.ps1
```

The Compose end-to-end script saves its checks and successful booking response under `docs/evidence/docker-test/`. Screenshot evidence page 4 captures the persistence script passing after replay across a Compose restart, with matching appointment and reservation IDs; no separate text log is saved.

### 5. Kubernetes Deployment with Minikube

The `k8s/` directory contains Kubernetes manifests for all four microservices:

- `k8s/patient-service.yaml`
- `k8s/availability-service.yaml`
- `k8s/notification-service.yaml`
- `k8s/appointment-service.yaml`

The manifests define Deployments and Services, with persistent volume claims for the service databases. Appointment Service is exposed using a NodePort Service; the other services use internal ClusterIP Services.

Ensure that Minikube is running. The Dockerfiles copy Maven-built executable JARs, so package first. Compose builds images using names that match the four manifests; load those exact image tags into Minikube before applying the manifests:

```powershell
mvn clean package
docker compose build
minikube image load hospital-appointment-microservices-patient-service:latest
minikube image load hospital-appointment-microservices-availability-service:latest
minikube image load hospital-appointment-microservices-notification-service:latest
minikube image load hospital-appointment-microservices-appointment-service:latest
```

Validate and deploy the manifests:

```powershell
kubectl apply --dry-run=client -f .\k8s\
kubectl apply -f .\k8s\
```

Check deployment and pod status:

```powershell
kubectl get deployments
kubectl get services
kubectl get pods
kubectl get pvc
```

Wait for the deployments to become available:

```powershell
kubectl rollout status deployment/patient-service --timeout=120s
kubectl rollout status deployment/availability-service --timeout=120s
kubectl rollout status deployment/notification-service --timeout=120s
kubectl rollout status deployment/appointment-service --timeout=120s
```

### 6. Automated Kubernetes Tests

Run the Kubernetes end-to-end test script:

```powershell
.\scripts\test-kubernetes.ps1
```

The script starts temporary local port forwards, checks the four application health endpoints, creates test data, submits an appointment booking, validates notification processing, verifies idempotent replay, and checks that reuse of the same idempotency key with a different payload returns HTTP 409.

The current script writes its 13-pass, 0-fail summary to `docs/evidence/kubernetes-test/test-results.txt`; temporary port-forward logs are saved under `docs/evidence/test-logs/`. Screenshot evidence page 6 captures an earlier nine-check run, not the current result file.

### 7. Scaling and Self-Healing

Kubernetes self-healing can be tested by deleting a pod and observing its replacement:

```powershell
kubectl get pods
kubectl delete pod <pod-name>
kubectl get pods -w
```

Replace `<pod-name>` with the name of an existing pod. The Deployment controller should create a replacement to maintain the desired replica count.

To scale Patient Service to two replicas:

```powershell
kubectl scale deployment/patient-service --replicas=2
kubectl get pods
```

To restore the original single-replica configuration:

```powershell
kubectl scale deployment/patient-service --replicas=1
kubectl rollout status deployment/patient-service
```

Patient Service replica scaling was verified at 2/2 and then restored to 1/1; the command output is saved in `docs/evidence/kubernetes-test/scaling-results.txt`, and screenshot evidence page 8 shows two Running Patient Service pods. Screenshot evidence page 7 shows a deleted Patient Service Pod replaced by a Running Pod, demonstrating self-healing. Scaling is a local replica-management demonstration, not a production-capacity or high-availability benchmark. Each service has its own SQLite database and PVC; scaling a service does not demonstrate production-safe concurrent SQLite access.

### 8. Troubleshooting and Validation

Each service POM configures the Spring Boot `repackage` goal with the `exec` classifier. This keeps the ordinary JAR available for Maven module dependencies and creates a separate executable `*-exec.jar` for each Dockerfile. Screenshot evidence page 9 shows `mvn clean package` completing successfully. It verifies the successful build, not the original packaging failure; original error logs are not included.

Build the executable JARs required by the Dockerfiles with:

```powershell
mvn clean package
```

The saved Docker Compose result contains 10 passing checks and the saved Kubernetes result contains 13 passed with 0 failed. Persistence, self-healing, and scaling are also documented in the screenshot evidence; the screenshot Kubernetes run on page 6 is an earlier nine-check version. No original Maven packaging error log is included.

### 9. Evidence and Supporting Documentation

- **Assignment 1 evidence:** `evidence/` — concurrency, idempotency, resilience, notification persistence, and submission audit results.
- **Assignment 2 evidence:** `docs/evidence/docker-test/` — Docker test results and successful booking response; `docs/evidence/kubernetes-test/` — current Kubernetes test summary and replica scaling output; `docs/evidence/test-logs/` — port-forward logs. `docs/evidence/Assignment2_Screenshot_Evidence.docx` contains captured evidence on pages 1–9: Compose status/booking/idempotency/persistence (1–4), Kubernetes resources and an earlier end-to-end run (5–6), self-healing (7), scaling (8), and successful Maven packaging (9).
- **Architecture and design:** `docs/architecture.mmd`, `docs/sequence-booking.mmd`, and `docs/data-ownership.md`.
- **API contracts:** `docs/api-contracts.md`.
- **Business rules:** `docs/business-rules.md`.

Refer to these files for implementation details and evidence supporting the two assignments.

### 10. Deployment Limitations

- Notification processing is simulated; the system does not send actual email or SMS messages.
- Each service has its own SQLite file and PVC. Scaling a service makes replicas use that service's SQLite data on the PVC; this does not establish production-safe concurrent database access.
- The Kubernetes scaling exercise demonstrates replica management; it does not establish production-grade horizontal scalability.
- The Compose health checks use TCP port availability rather than application-level readiness checks. Appointment Service does not currently define a Compose health check.
- Kubernetes availability and persistence depend on the local Minikube environment and its storage configuration.

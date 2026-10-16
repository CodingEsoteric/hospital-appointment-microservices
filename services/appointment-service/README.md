# appointment-service

Spring Boot service scaffold. Default port: 8081.

- Health: `GET http://localhost:8081/health`
- Swagger UI: `http://localhost:8081/swagger-ui.html` (path may redirect depending on springdoc version)
- This is a health-only skeleton. Implement the service API, database-per-service, business rules and tests before claiming completion.

Run from repository root after installing Java 21 and Maven:
`mvn -pl services/appointment-service spring-boot:run`

# notification-service

Spring Boot notification service. Default port: 8084.

- Health: `GET http://localhost:8084/health`
- Submit appointment notification: `POST http://localhost:8084/api/notifications`
- Swagger UI: `http://localhost:8084/swagger-ui.html` (path may redirect depending on springdoc version)

Request JSON requires `appointmentId`, `patientId`, `slotId`, `reservationId`, and `createdAt` (ISO-8601 timestamp). A valid request returns HTTP `202 Accepted` with a `notificationId`, the appointment ID, `status: ACCEPTED`, and an acceptance timestamp. Repeated submissions for one appointment return the same event.

This is a local simulation: accepted events are held in memory for the lifetime of the process. No email or SMS is sent, and event history is lost on restart. Appointment Service uses this endpoint by default; set `NOTIFICATION_SEND_PATH` to an empty value to report notifications as `UNCONFIGURED`, or override it with another path.

Run from repository root after installing Java 21 and Maven:
`mvn -pl services/notification-service spring-boot:run`

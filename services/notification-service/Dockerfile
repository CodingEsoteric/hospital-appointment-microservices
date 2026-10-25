
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/notification-service-0.1.0-SNAPSHOT-exec.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/appointment-service-0.1.0-SNAPSHOT-exec.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

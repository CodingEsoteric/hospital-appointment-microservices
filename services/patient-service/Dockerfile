
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/patient-service-0.1.0-SNAPSHOT-exec.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]

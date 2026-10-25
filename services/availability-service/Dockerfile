
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/availability-service-0.1.0-SNAPSHOT-exec.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]

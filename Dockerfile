# Build stage: resolve dependencies in their own layer so source edits do not
# re-download the world, then package the executable jar.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .

COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage: JRE only, unprivileged user, no build tools in the image.
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S seatflow && adduser -S seatflow -G seatflow
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar
RUN chown -R seatflow:seatflow /app

USER seatflow
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

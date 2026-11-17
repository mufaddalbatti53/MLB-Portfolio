FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S spring -G spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/*.jar /app/app.jar

USER spring
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xms128m", "-Xmx350m", "-jar", "/app/app.jar"]

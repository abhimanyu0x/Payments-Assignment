# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml lombok.config ./
COPY platform platform
COPY app app
COPY mock-psp mock-psp
COPY webhook-receiver webhook-receiver
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package
FROM eclipse-temurin:21-jre AS app
WORKDIR /opt/dodo
COPY --from=build /source/app/target/app-1.0.0.jar app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","app.jar"]
FROM eclipse-temurin:21-jre AS mock-psp
WORKDIR /opt/dodo
COPY --from=build /source/mock-psp/target/mock-psp-1.0.0.jar app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","app.jar"]
FROM eclipse-temurin:21-jre AS webhook-receiver
WORKDIR /opt/dodo
COPY --from=build /source/webhook-receiver/target/webhook-receiver-1.0.0.jar app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","app.jar"]

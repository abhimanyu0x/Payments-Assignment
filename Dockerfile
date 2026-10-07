FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml ./
COPY app/pom.xml app/pom.xml
COPY mock-psp/pom.xml mock-psp/pom.xml
COPY app/src app/src
COPY mock-psp/src mock-psp/src
RUN mvn -B -DskipTests package
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

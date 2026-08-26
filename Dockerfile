# Build and run fhir-hub. Multi-stage so the runtime image carries no build tooling.
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# Dependencies first: this layer is cached until the pom actually changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre-alpine

RUN adduser -D -s /bin/ash containers
WORKDIR /usr/src/app

COPY --from=build /build/target/fhir-hub-*.jar app.jar
RUN chown -R containers /usr/src/app

USER containers
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

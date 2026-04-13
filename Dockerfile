# build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY . .

RUN ./gradlew build -x test # Skip tests for build process.

# run stage
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /server

COPY --from=builder /build/build/libs/server-0.0.1-SNAPSHOT.jar server.jar

CMD ["java", "-jar", "server.jar"]

EXPOSE 8080
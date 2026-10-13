# Build stage: runs once on the build host's own platform; the jar is platform-independent
FROM --platform=$BUILDPLATFORM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
# bootJar only, so build/libs holds just the executable jar (no -plain.jar)
RUN gradle bootJar -x test --no-daemon

# Run stage: one per target platform. CI builds linux/amd64; this stage only copies the jar, so linux/arm64 needs no emulation
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8070

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]

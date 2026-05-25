# syntax=docker/dockerfile:1.7

# x86_64 기본 배포 기준 멀티스테이지 빌드
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

RUN sed -i 's/\r//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r --gid 10001 appuser \
    && useradd -r --uid 10001 --gid appuser --home-dir /app --shell /usr/sbin/nologin appuser

WORKDIR /app

COPY --from=build --chown=appuser:appuser /app/build/libs/*.jar app.jar

EXPOSE 8080

USER appuser:appuser

ENTRYPOINT ["java", "-Djava.io.tmpdir=/tmp", "-jar", "app.jar"]

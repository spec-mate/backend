# 빌드 (Gradle 캐시 최적화)
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Gradle wrapper 및 빌드 설정 파일만 먼저 복사
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# 의존성만 먼저 다운로드 (캐시 활용)
RUN ./gradlew dependencies --no-daemon || true

# 이후 소스 복사
COPY . .

# JAR 빌드
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

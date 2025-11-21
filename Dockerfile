# 1단계: 빌드 스테이지
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# gradle wrapper & 설정 파일만 복사 → 캐시 최적화
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

RUN ./gradlew dependencies --no-daemon || true

# 소스 복사
COPY . .

# JAR 빌드
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 스테이지
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

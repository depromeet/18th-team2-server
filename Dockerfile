# === 1단계: 빌드 ===
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

# Gradle 래퍼 + 빌드 설정 먼저 복사 (의존성 캐시 레이어)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스코드 + 서브모듈 시크릿 복사 (소스 변경 시에만 재빌드)
COPY src ./src
COPY config ./config

# 테스트 제외하고 JAR 빌드
RUN ./gradlew bootJar -x test --no-daemon

# === 2단계: 실행 ===
FROM eclipse-temurin:25-jre

WORKDIR /app

# healthcheck용 curl 설치
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# 빌드된 JAR만 복사 (빌드 도구 제외하여 이미지 경량화)
COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

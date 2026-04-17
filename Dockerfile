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

# non-root 유저 생성
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 빌드된 JAR 복사
COPY --from=builder /app/build/libs/app.jar app.jar
RUN chown appuser:appuser app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]

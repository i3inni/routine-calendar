# ── 1단계: 빌드 (JDK + Gradle 래퍼로 실행 가능한 jar 생성) ──
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 래퍼/빌드 스크립트 먼저 복사 → 의존성 캐시 레이어 활용
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

# 소스 복사 후 boot jar 빌드 (테스트는 빌드 단계에서 제외 → 배포 속도)
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# ── 2단계: 실행 (가벼운 JRE 이미지) ──
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Railway가 주입하는 PORT를 application.yml(server.port=${PORT})이 받는다.
ENTRYPOINT ["java", "-jar", "app.jar"]

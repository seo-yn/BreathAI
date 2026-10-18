# 1단계: jar 빌드 (테스트는 DB가 필요해 여기서는 돌리지 않는다)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성만 먼저 받아 두면 소스만 바뀐 경우 이 단계는 캐시를 쓴다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon

# 2단계: 실행 이미지 (JRE + jar만)
FROM eclipse-temurin:17-jre
RUN useradd --system app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health > /dev/null || exit 1

# 메모리가 부족하면 멈춘 채로 두지 않고 종료해서 재시작 정책으로 다시 뜨게 한다
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

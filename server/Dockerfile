# MES 서버 이미지. docker compose --profile app up 이 server 폴더를 빌드 위치로 써서 만든다.
# 1단계: JDK로 실행 jar를 만든다. 의존성을 먼저 받아 두면 소스만 바뀌었을 때 그 층을 다시 쓴다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

# 2단계: JRE만 있는 작은 이미지에서 실행한다.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

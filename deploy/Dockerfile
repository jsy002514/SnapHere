# SnapHere 운영 이미지 — 런타임 전용 (빌드는 GitHub Actions 에서 끝낸다)
#
# 배치: ~/snaphere/Dockerfile
# 빌드 컨텍스트: ~/snaphere   (docker-compose.yml 의 build.context: .)
#
# 이 안에서 Gradle 을 돌리지 않는 이유:
#   Lightsail 2GB 에서 Gradle 데몬 + 컴파일은 OOM 으로 앱까지 같이 죽는다.
#   그래서 Actions(램 16GB)가 bootJar 로 app.jar 를 만들어 scp 로 올리고,
#   이 Dockerfile 은 그 jar 를 담기만 한다. 이미지 빌드가 몇 초에 끝난다.

# JRE 21 — backend/build.gradle.kts 의 툴체인(JavaLanguageVersion 21)과 맞춘다.
# alpine(musl) 대신 데비안 계열(noble = Ubuntu 24.04)을 쓴다. Firebase Admin 과
# AWS SDK 가 얽힌 환경에서 musl 관련 변수를 없애는 쪽이 안전하고, 이미지가
# 80MB 정도 커지는 대신 컨테이너 실행 메모리에는 영향이 없다.
FROM eclipse-temurin:21.0.11_10-jre-noble

# curl 은 docker-compose.yml 의 app 헬스체크가 쓴다.
#   test: ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health || exit 1"]
# 이 RUN 을 지우면 헬스체크가 항상 실패하고, depends_on: condition: service_healthy
# 로 묶인 것들이 영원히 안 뜬다. (nginx 는 depends_on: app 이라 뜨긴 하지만
# deploy.yml 의 "Wait for health" 가 3분 뒤 실패로 끝난다.)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Seoul

# root 로 돌리지 않는다. 컨테이너가 뚫렸을 때 피해 범위를 줄인다.
RUN useradd --system --create-home --shell /usr/sbin/nologin snaphere
WORKDIR /app

# Actions 가 scp 로 올린 "실행 가능한" jar. deploy.yml 의 "Pick the runnable jar"
# 단계가 -plain.jar 를 걸러낸 결과물이다.
# 이 파일이 없으면 이미지 빌드가 여기서 실패한다 — 조용히 옛 jar 로 뜨는 것보다 낫다.
COPY --chown=snaphere:snaphere app.jar /app/app.jar

USER snaphere
EXPOSE 8080

# JVM 옵션(-Xmx700m 등)은 docker-compose.yml 의 JAVA_TOOL_OPTIONS 로 넘어온다.
# 여기서 중복으로 주면 나중에 어느 쪽이 이겼는지 추적하기 어려워진다.
#
# exec 형식(대괄호)이라 java 가 PID 1 이 된다. 그래서 docker compose stop 의
# SIGTERM 이 셸을 거치지 않고 JVM 에 직접 닿고, Spring 의 graceful shutdown 이 동작한다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

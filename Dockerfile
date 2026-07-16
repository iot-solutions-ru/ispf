# All-in-one ISPF server (embedded web console).
# Build context must include a prebuilt bootJar (see .github/workflows/release.yml).
FROM eclipse-temurin:25-jre-jammy

LABEL org.opencontainers.image.title="ISPF"
LABEL org.opencontainers.image.description="Open-source industrial IoT / SCADA — all-in-one server + web console"
LABEL org.opencontainers.image.source="https://github.com/Michaael/IoT-Solutions-Platform"
LABEL org.opencontainers.image.url="https://github.com/Michaael/IoT-Solutions-Platform"
LABEL org.opencontainers.image.licenses="AGPL-3.0-or-later"
LABEL org.opencontainers.image.vendor="ISPF"

WORKDIR /opt/ispf

RUN mkdir -p /opt/ispf/data \
  && useradd --system --uid 10001 --home-dir /opt/ispf --shell /usr/sbin/nologin ispf \
  && chown -R ispf:ispf /opt/ispf

COPY --chown=ispf:ispf ispf-server.jar /opt/ispf/ispf-server.jar

USER ispf

# local profile → embedded H2 at ${ISPF_DATA_DIR}/ispf-local.mv.db (no Postgres).
ENV ISPF_DATA_DIR=/opt/ispf/data \
    SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

VOLUME ["/opt/ispf/data"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/ispf/ispf-server.jar"]

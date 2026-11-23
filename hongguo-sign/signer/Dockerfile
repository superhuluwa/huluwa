FROM python:3.11-slim-bookworm

ARG FRIDA_VERSION=16.4.10
RUN apt-get update && apt-get install -y --no-install-recommends \
        adb curl xz-utils ca-certificates tini && \
    rm -rf /var/lib/apt/lists/* && \
    pip install --no-cache-dir "frida==${FRIDA_VERSION}"
RUN mkdir -p /opt/frida && \
    curl --fail --location --retry 3 \
        "https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/frida-server-${FRIDA_VERSION}-android-x86_64.xz" \
        -o /tmp/frida-server.xz && \
    xz -dc /tmp/frida-server.xz > /opt/frida/frida-server && \
    chmod 755 /opt/frida/frida-server && rm /tmp/frida-server.xz

WORKDIR /app
COPY sign_server.py entrypoint.sh docker-healthcheck.sh /app/
RUN chmod 755 /app/entrypoint.sh /app/docker-healthcheck.sh
ENV PYTHONUNBUFFERED=1 PYTHONIOENCODING=utf-8 PORT=8901
EXPOSE 8901
HEALTHCHECK --interval=30s --timeout=45s --start-period=180s --retries=5 \
    CMD ["/app/docker-healthcheck.sh"]
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "/app/entrypoint.sh"]

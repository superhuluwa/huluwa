FROM busybox:1.37.0-musl AS toolbox
FROM python:3.11-slim-bookworm AS signer

ARG FRIDA_VERSION=16.4.10
RUN apt-get update && apt-get install -y --no-install-recommends \
        adb curl xz-utils ca-certificates tini && \
    rm -rf /var/lib/apt/lists/* && \
    pip install --no-cache-dir "frida==${FRIDA_VERSION}"
RUN mkdir -p /opt/frida /apk /run /proc /dev && \
    curl --fail --location --retry 3 \
        "https://github.com/frida/frida/releases/download/${FRIDA_VERSION}/frida-server-${FRIDA_VERSION}-android-x86_64.xz" \
        -o /tmp/frida-server.xz && \
    xz -dc /tmp/frida-server.xz > /opt/frida/frida-server && \
    chmod 755 /opt/frida/frida-server && rm /tmp/frida-server.xz
COPY signer/ /app/
COPY single/launch.py /app/launch.py
# 将 APK 放入镜像，运行时只需挂载持久化的 Android 数据卷。
COPY apk/base.apk /apk/base.apk
RUN chmod 755 /app/entrypoint.sh /app/docker-healthcheck.sh

FROM redroid/redroid:14.0.0_64only-latest@sha256:0a611199ba2e0b5d60af39b3327a517f6407231f4352114ed3bd3cbfe2be69aa
COPY --from=toolbox /bin/busybox /busybox
# Android 继续使用 Bionic；Python、ADB 和 Frida 客户端在独立的 glibc 根目录运行。
COPY --from=signer / /signer-rootfs/
COPY single/entrypoint.sh single/start-signer.sh single/watchdog.sh /hongguo/
COPY single/hongguo-signer.rc /system/etc/init/hongguo-signer.rc
ENV PORT=8901 BOOT_TIMEOUT=300 APP_WARMUP=30 APP_START_MODE=provider \
    SIGN_READY_TIMEOUT=120 STARTUP_TIMEOUT=600 RECOVERY_TIMEOUT=300
EXPOSE 8901
HEALTHCHECK --interval=30s --timeout=50s --start-period=600s --retries=3 \
    CMD ["/busybox", "chroot", "/signer-rootfs", "/app/docker-healthcheck.sh"]
ENTRYPOINT ["/busybox", "sh", "/hongguo/entrypoint.sh"]
CMD ["androidboot.redroid_width=720", "androidboot.redroid_height=1280", \
     "androidboot.redroid_dpi=320", "androidboot.redroid_fps=15", \
     "androidboot.redroid_gpu_mode=guest", "androidboot.use_memfd=true", "ro.secure=0"]

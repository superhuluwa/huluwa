FROM maven:3.9.9-eclipse-temurin-21 AS toolchain

ARG ANDROID_COMMANDLINE_TOOLS=13114758
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/workspace/gradle \
    PYTHONUNBUFFERED=1 \
    LANG=C.UTF-8
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 git unzip ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /opt/android-sdk/cmdline-tools \
    && curl --fail --location --retry 4 \
       "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_COMMANDLINE_TOOLS}_latest.zip" -o /tmp/android-tools.zip \
    && unzip -q /tmp/android-tools.zip -d /opt/android-sdk/cmdline-tools \
    && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest \
    && rm /tmp/android-tools.zip
ENV PATH="/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/build-tools/36.0.0:${PATH}"
# SDK licenses are accepted once while provisioning this dedicated build environment.
RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platforms;android-36" "build-tools;36.0.0" "build-tools;35.0.0" \
    && groupadd --gid 10001 huluwa \
    && useradd --uid 10001 --gid 10001 --no-create-home huluwa \
    && mkdir -p /workspace /build-data/tasks /signing /credentials /opt/builder \
    && chown -R 10001:10001 /workspace /build-data
ENV ANDROID_USER_HOME=/workspace/android-user
RUN usermod --home /workspace huluwa \
    && sdkmanager "platform-tools"
# Keep the runtime on Debian; the first stage only provisions the pinned JDK/SDK.
FROM debian:bookworm-slim
ENV JAVA_HOME=/opt/java/openjdk \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_USER_HOME=/workspace/android-user \
    GRADLE_USER_HOME=/workspace/gradle \
    HULUWA_ANDROID_PLATFORM=debian \
    PYTHONUNBUFFERED=1 \
    LANG=C.UTF-8
ENV PATH="/opt/java/openjdk/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/build-tools/36.0.0:${PATH}"
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 git unzip ca-certificates curl bash libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 huluwa \
    && useradd --uid 10001 --gid 10001 --home-dir /workspace --no-create-home huluwa \
    && mkdir -p /workspace /build-data/tasks /signing /credentials /opt/builder \
    && chown -R 10001:10001 /workspace /build-data
COPY --from=toolchain /opt/java/openjdk /opt/java/openjdk
COPY --from=toolchain /opt/android-sdk /opt/android-sdk
COPY agent.py worker.py process_tree.py android.init.gradle git-askpass.py /opt/builder/
RUN sed -i 's/\r$//' /opt/builder/*.py \
    && chmod 755 /opt/builder/git-askpass.py
USER 10001:10001
WORKDIR /workspace
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD python3 -c "import json,time; s=json.load(open('/build-data/worker.json')); assert time.time()-s['heartbeat'] < 20"
ENTRYPOINT ["python3", "/opt/builder/worker.py"]

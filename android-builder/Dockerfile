FROM maven:3.9.9-eclipse-temurin-21

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
COPY worker.py android.init.gradle git-askpass.py /opt/builder/
RUN chmod 755 /opt/builder/git-askpass.py
USER 10001:10001
WORKDIR /workspace
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=3 \
    CMD python3 -c "import json,time; s=json.load(open('/build-data/worker.json')); assert time.time()-s['heartbeat'] < 20"
ENTRYPOINT ["python3", "/opt/builder/worker.py"]

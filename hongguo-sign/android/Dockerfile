FROM busybox:1.37.0-musl AS toolbox
FROM redroid/redroid:14.0.0-latest@sha256:a3e654caad74faed03cd5ead057a989e06f507778cda678e5d3b9772e0092f1c

COPY --from=toolbox /bin/busybox /busybox
COPY entrypoint.sh /native-entrypoint.sh
ENTRYPOINT ["/busybox", "sh", "/native-entrypoint.sh"]

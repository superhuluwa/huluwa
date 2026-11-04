FROM busybox:1.37.0-musl AS toolbox
FROM redroid/redroid:14.0.0_64only-latest@sha256:0a611199ba2e0b5d60af39b3327a517f6407231f4352114ed3bd3cbfe2be69aa

COPY --from=toolbox /bin/busybox /busybox
COPY entrypoint.sh /native-entrypoint.sh
ENTRYPOINT ["/busybox", "sh", "/native-entrypoint.sh"]

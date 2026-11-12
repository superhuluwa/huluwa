# The named openhongguo context is the clean, pinned upstream checkout.
ARG GO_VERSION=1.27.3
FROM golang:${GO_VERSION}-alpine AS build
ARG UPSTREAM_COMMIT=015a388112ee148a7d2dfb7ecfafe171bbab957f
WORKDIR /src/signer
COPY go.mod main.go ./
COPY internal/ ./internal/
COPY tools/register-device.go /src/register-device.go
COPY --from=openhongguo / /src/openhongguo/
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w -X main.upstreamRevision=${UPSTREAM_COMMIT}" -o /out/hg-signer .
RUN cd /src/openhongguo && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/hg-register /src/register-device.go

FROM alpine:3.20
RUN mkdir -p /config /usr/share/licenses/hongguo-signer && chown 65532:65532 /config
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /out/hg-signer /out/hg-register /usr/local/bin/
COPY LICENSE.openhongguo LICENSE.Go NOTICE.md /usr/share/licenses/hongguo-signer/
USER 65532:65532
VOLUME ["/config"]
EXPOSE 8901
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 CMD wget -q -O /dev/null http://127.0.0.1:8901/health || exit 1
ENTRYPOINT ["/usr/local/bin/hg-signer"]
CMD ["--listen", "0.0.0.0:8901", "--profile", "/config/anonymous.json"]

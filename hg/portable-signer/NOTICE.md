# Third-party notices

The unmodified Go files in `internal/portable`, except `raw.go` and
`playback.go`/`playback_test.go`, are from
[beilunyang/openhongguo](https://github.com/beilunyang/openhongguo), commit
`42b73f201309423f012d483bfc0ce579dcc0937c`.
Copyright 2026 beilunyang. Licensed under Apache License 2.0; see
[LICENSE.openhongguo](LICENSE.openhongguo).
`upstream-sources.json` records the original paths and SHA256 hashes.

`raw.go` adapts the upstream signing entry point to preserve the caller's exact
URL, query order and request ticket, returning the six original header names.
`playback.go` and `playback_test.go` carry the playback-only Argus/Ladon
implementation and golden vectors from openhongguo PR #7 (commit
`bed14595d35b02e0d550270621812ca7386c4108`), licensed under Apache 2.0.
The HTTP adapter and device-profile normalizer are project integration code.
`hg-register` uses the upstream public SDK to register one anonymous installation.
It does not load or run an Android application.

The binaries also include the Go runtime. See [LICENSE.Go](LICENSE.Go).

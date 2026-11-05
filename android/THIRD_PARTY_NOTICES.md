# Third-party notices

## openhongguo

`app/src/main/java/com/huluwa/android/hongguo/HongguoKey.kt` adapts the key-material
parsing algorithm from
[beilunyang/openhongguo — internal/drm/spade.go](https://github.com/beilunyang/openhongguo/blob/main/internal/drm/spade.go).

Copyright 2026 beilunyang.

Licensed under the Apache License, Version 2.0. The complete license is included
in [app/src/main/assets/licenses/openhongguo-LICENSE.txt](app/src/main/assets/licenses/openhongguo-LICENSE.txt).

Changes: translated the Go implementation to Kotlin, added Android Base64
handling, and restricted the returned content key to AES-128. The MP4 reader and
Media3 data source are implemented separately in this project.

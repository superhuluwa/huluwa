package sign

import (
    "encoding/base64"
    "errors"
    "net/url"
    "strconv"
    "time"
)

// RawURL signs the caller's exact URL. It never changes the path, query order or
// _rticket. The upstream SDK's unsupported F13 branch is avoided by choosing an
// equally fresh timestamp in the preceding seconds, rather than editing the URL.
func RawURL(rawURL, deviceID, versionName string, now time.Time) (map[string]string, error) {
    parsed, err := url.Parse(rawURL)
    if err != nil || parsed.Hostname() == "" || (parsed.Scheme != "https" && parsed.Scheme != "http") || parsed.Fragment != "" {
        return nil, errors.New("invalid HTTP(S) URL")
    }
    if deviceID == "" || versionName == "" { return nil, errors.New("device profile is incomplete") }
    query := parsed.RawQuery
    querySM3 := sm3Sum([]byte(query))
    bodyMD5 := make([]byte, 16)
    current := uint32(now.Unix())
    khronos := current
    found := false
    for age := uint32(0); age < 16; age++ {
        khronos = current - age
        if branchOf(querySM3[:], bodyMD5, le32(khronos)) != 1 {
            found = true
            break
        }
    }
    if !found { return nil, errors.New("no supported signing branch for this timestamp") }
    entropy := drawEntropy(now.UnixMilli())
    entropy.NowSecs = int64(khronos)
    return map[string]string{
        "X-Gorgon": xGorgon(query, nil, false, khronos, entropy.GorgonRandom),
        "X-Khronos": strconv.FormatUint(uint64(khronos), 10),
        "X-Argus": base64.StdEncoding.EncodeToString(le32(khronos)),
        "X-Ladon": base64.StdEncoding.EncodeToString(be32(khronos)),
        "X-Medusa": assembleMedusa(rawURL, nil, false, khronos, deviceID, versionName, entropy),
        "X-Helios": assembleHelios(khronos, entropy.HeliosRandom),
    }, nil
}

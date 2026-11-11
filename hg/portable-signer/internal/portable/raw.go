package sign

import (
	"encoding/base64"
	"encoding/hex"
	"errors"
	"net/url"
	"strconv"
	"strings"
	"time"
)

// RawPlaybackURL applies PR #7's Argus/Ladon envelope to the caller's exact
// query and body. Signing is local: it never sends or consumes a playback request.
func RawPlaybackURL(rawURL, deviceID, versionName string, body []byte, now time.Time) (map[string]string, error) {
	return rawURLHeaders(rawURL, deviceID, versionName, body, now, true)
}

// RawURL keeps the original GET-only entry point for existing callers.
func RawURL(rawURL, deviceID, versionName string, now time.Time) (map[string]string, error) {
	return RawURLWithBody(rawURL, deviceID, versionName, nil, now)
}

// RawURLWithBody preserves the URL, query order, ticket and exact body bytes.
func RawURLWithBody(rawURL, deviceID, versionName string, body []byte, now time.Time) (map[string]string, error) {
	return rawURLHeaders(rawURL, deviceID, versionName, body, now, false)
}

func rawURLHeaders(rawURL, deviceID, versionName string, body []byte, now time.Time, playback bool) (map[string]string, error) {
	parsed, err := url.Parse(rawURL)
	if err != nil || parsed.Hostname() == "" || (parsed.Scheme != "https" && parsed.Scheme != "http") || parsed.User != nil || parsed.Fragment != "" {
		return nil, errors.New("invalid HTTP(S) URL")
	}
	if deviceID == "" || versionName == "" {
		return nil, errors.New("device profile is incomplete")
	}
	query := parsed.RawQuery
	bodyMD5 := [16]byte{}
	if body != nil {
		bodyMD5 = md5Raw(body)
	}
	khronos := uint32(now.Unix())
	if playback {
		// Keep the request's ts and X-Khronos identical when the caller
		// already supplied the timestamp in its immutable signed query.
		if ts, err := strconv.ParseUint(queryValue(query, "ts"), 10, 32); err == nil {
			khronos = uint32(ts)
		}
	}
	if !playback {
		// Only Medusa needs an F13 branch. Avoid its unsupported branch by
		// choosing a recent timestamp without changing the caller's URL.
		querySM3 := sm3Sum([]byte(query))
		found := false
		for age := uint32(0); age < 16; age++ {
			khronos = uint32(now.Unix()) - age
			if branchOf(querySM3[:], bodyMD5[:], le32(khronos)) != 1 {
				found = true
				break
			}
		}
		if !found {
			return nil, errors.New("no supported signing branch for this timestamp")
		}
	}
	entropy := drawEntropy(now.UnixMilli())
	entropy.NowSecs = int64(khronos)
	headers := map[string]string{
		"X-Gorgon":  xGorgon(query, body, body != nil, khronos, entropy.GorgonRandom),
		"X-Khronos": strconv.FormatUint(uint64(khronos), 10),
		"X-Helios":  assembleHelios(khronos, entropy.HeliosRandom),
	}
	if playback {
		argusMD5 := bodyMD5
		if len(body) == 0 {
			argusMD5 = [16]byte{}
		}
		aid := playbackAID(query)
		headers["X-Argus"] = argusHeader(query, argusMD5, khronos, randUint32(), aid, deviceID, queryValue(query, "iid"))
		headers["X-Ladon"] = ladonHeader(khronos, randUint32(), aid)
	} else {
		headers["X-Argus"] = base64.StdEncoding.EncodeToString(le32(khronos))
		headers["X-Ladon"] = base64.StdEncoding.EncodeToString(be32(khronos))
		headers["X-Medusa"] = assembleMedusa(rawURL, body, body != nil, khronos, deviceID, versionName, entropy)
	}
	if body != nil {
		headers["X-SS-STUB"] = strings.ToUpper(hex.EncodeToString(bodyMD5[:]))
	}
	return headers, nil
}

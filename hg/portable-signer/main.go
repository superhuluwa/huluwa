package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	portable "huluwa/hongguo-signer/internal/portable"
)

const engine = "portable-go"

var upstreamRevision = "015a388112ee148a7d2dfb7ecfafe171bbab957f"

const playbackRevision = "bed14595d35b02e0d550270621812ca7386c4108"

var signHeaderNames = []string{"X-Argus", "X-Gorgon", "X-Helios", "X-Khronos", "X-Ladon", "X-Medusa"}

type profile struct {
	Params    map[string]string `json:"params"`
	UserAgent string            `json:"user_agent"`
}

type service struct {
	mutex            sync.RWMutex
	recoverMutex     sync.Mutex
	profile          profile
	profilePath      string
	registerProgram  string
	registerTimeout  time.Duration
	recoveryCooldown time.Duration
	lastRecovery     time.Time
	started          time.Time
	signatures       atomic.Uint64
	failures         atomic.Uint64
	recoveries       atomic.Uint64
}

func readProfile(path string) (profile, error) {
	var p profile
	data, err := os.ReadFile(path)
	if err != nil {
		return p, err
	}
	if len(data) > 65536 {
		return p, fmt.Errorf("profile exceeds size limit")
	}
	if err := json.Unmarshal(data, &p); err != nil {
		return p, err
	}
	if len(p.Params) == 0 {
		p, err = normalizeSDKProfile(data)
		if err != nil {
			return p, err
		}
	}
	for _, name := range []string{"device_id", "iid", "cdid", "version_name"} {
		if p.Params[name] == "" {
			return p, fmt.Errorf("profile is missing %s", name)
		}
	}
	if strings.ContainsAny(p.UserAgent, "\r\n") || len(p.UserAgent) > 600 {
		return p, fmt.Errorf("invalid profile User-Agent")
	}
	return p, nil
}

// SDK files contain device tokens and cookies. Only the public device/application
// fields needed by the existing caller are normalized and exposed through /info.
func normalizeSDKProfile(data []byte) (profile, error) {
	var raw struct {
		DeviceID  string `json:"device_id_str"`
		InstallID string `json:"install_id_str"`
		CDID      string `json:"cdid"`
		OpenUDID  string `json:"openudid"`
		Device    struct {
			Brand      string `json:"brand"`
			Model      string `json:"model"`
			OSVersion  string `json:"os_version"`
			OSAPI      int    `json:"os_api"`
			Build      string `json:"build"`
			Resolution string `json:"resolution"`
			DPI        int    `json:"dpi"`
			Pad        bool   `json:"pad"`
			Channel    string `json:"channel"`
		} `json:"device"`
	}
	if err := json.Unmarshal(data, &raw); err != nil {
		return profile{}, err
	}
	d := raw.Device
	if d.Brand == "" {
		d.Brand = "Xiaomi"
	}
	if d.Model == "" {
		d.Model = "23127PN0CC"
	}
	if d.OSVersion == "" {
		d.OSVersion = "14"
	}
	if d.OSAPI == 0 {
		d.OSAPI = 34
	}
	if d.Build == "" {
		d.Build = "UKQ1.230804.001"
	}
	if d.Resolution == "" {
		d.Resolution = "1200*2670"
	}
	if d.DPI == 0 {
		d.DPI = 460
	}
	if d.Channel == "" {
		d.Channel = "xiaomi_8662_64"
	}
	kind := "phone"
	if d.Pad {
		kind = "pad"
	}
	return profile{
		Params: map[string]string{
			"device_id": raw.DeviceID, "iid": raw.InstallID, "cdid": raw.CDID, "openudid": raw.OpenUDID,
			"device_type": d.Model, "device_brand": d.Brand, "channel": d.Channel,
			"aid": "8662", "app_name": "novelread", "version_code": "73932", "version_name": "7.3.9.32",
			"manifest_version_code": "73932", "update_version_code": "73932", "os": "android", "ssmix": "a",
			"language": "zh", "os_api": strconv.Itoa(d.OSAPI), "os_version": d.OSVersion,
			"resolution": d.Resolution, "dpi": strconv.Itoa(d.DPI), "ac": "wifi", "host_abi": "arm64-v8a",
			"device_platform": "android", "dragon_device_type": kind, "user_is_login": "0",
		},
		UserAgent: fmt.Sprintf("com.phoenix.read/73932 (Linux; U; Android %s; zh_CN; %s; Build/%s; Cronet/TTNetVersion:8d40f833 2026-03-03 QuicVersion:21ac1950 2025-11-18)", d.OSVersion, d.Model, d.Build),
	}, nil
}

func (s *service) currentProfile() profile {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	return s.profile
}

func (s *service) registerDevice(ctx context.Context, replace bool) error {
	args := []string{"--profile", s.profilePath}
	if replace {
		args = append(args, "--replace")
	}
	registration, cancel := context.WithTimeout(ctx, s.registerTimeout)
	defer cancel()
	command := exec.CommandContext(registration, s.registerProgram, args...)
	command.Stdout = io.Discard
	command.Stderr = io.Discard
	if err := command.Run(); err != nil {
		if errors.Is(registration.Err(), context.DeadlineExceeded) {
			return fmt.Errorf("anonymous device registration timed out")
		}
		return fmt.Errorf("anonymous device registration failed")
	}
	p, err := readProfile(s.profilePath)
	if err != nil {
		return fmt.Errorf("registered device profile is invalid: %w", err)
	}
	s.mutex.Lock()
	s.profile = p
	s.mutex.Unlock()
	return nil
}

// recoverDevice replaces the anonymous installation. The old profile is kept as a
// timestamped backup by hg-register, so an operator can still inspect or roll back.
// Concurrent recovery requests are coalesced during the cooldown window.
func (s *service) recoverDevice(ctx context.Context) (bool, time.Time, error) {
	s.recoverMutex.Lock()
	defer s.recoverMutex.Unlock()
	now := time.Now().UTC()
	if !s.lastRecovery.IsZero() && now.Sub(s.lastRecovery) < s.recoveryCooldown {
		return false, s.lastRecovery, nil
	}
	if err := s.registerDevice(ctx, true); err != nil {
		return false, time.Time{}, err
	}
	s.lastRecovery = now
	s.recoveries.Add(1)
	return true, now, nil
}

func send(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(value); err != nil {
		log.Printf("response write failed: %T", err)
	}
}

func (s *service) sign(rawURL string, body []byte) (map[string]string, error) {
	p := s.currentProfile()
	parsed, err := url.Parse(rawURL)
	if err != nil || parsed.Host == "" || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.User != nil || parsed.Fragment != "" {
		return nil, fmt.Errorf("invalid HTTP(S) URL")
	}
	values, err := url.ParseQuery(parsed.RawQuery)
	if err != nil {
		return nil, fmt.Errorf("invalid URL query")
	}
	if did := values.Get("device_id"); did != "" && did != p.Params["device_id"] {
		return nil, fmt.Errorf("URL device_id does not match the signing profile")
	}
	if iid := values.Get("iid"); iid != "" && iid != p.Params["iid"] {
		return nil, fmt.Errorf("URL iid does not match the signing profile")
	}
	version := values.Get("version_name")
	if version == "" {
		version = p.Params["version_name"]
	}
	// Only the player API changed its signing contract in PR #7. Legacy
	// video_tab GETs keep their six headers for existing clients.
	if isPlaybackPath(parsed.Path) {
		return portable.RawPlaybackURL(rawURL, p.Params["device_id"], version, body, time.Now())
	}
	return portable.RawURLWithBody(rawURL, p.Params["device_id"], version, body, time.Now())
}

func isPlaybackPath(path string) bool {
	return strings.HasPrefix(path, "/novel/player/video_model/") ||
		strings.HasPrefix(path, "/novel/player/multi_video_model/")
}

func (s *service) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/health":
		p := s.currentProfile()
		target := "https://api5-normal-sinfonlineb.fqnovel.com/reading/bookapi/search/tab/v?" + url.Values{
			"device_id": {p.Params["device_id"]},
			"iid":       {p.Params["iid"]},
			"aid":       {"8662"},
		}.Encode()
		_, err := s.sign(target, nil)
		if err != nil {
			send(w, 503, map[string]any{"ok": false, "engine": engine})
			return
		}
		send(w, 200, map[string]any{
			"ok": true, "engine": engine, "headers": signHeaderNames, "ts": time.Now().Unix(),
			"uptimeSeconds": int64(time.Since(s.started).Seconds()),
		})
	case r.Method == http.MethodGet && r.URL.Path == "/info":
		p := s.currentProfile()
		send(w, 200, map[string]any{
			"pkg": "com.phoenix.read", "target_pkg": "com.phoenix.read", "model": p.Params["device_type"],
			"engine": engine, "upstreamRevision": upstreamRevision, "androidRequired": false,
			"deviceProfile": p, "supportsRecovery": true, "recoveryMode": "rotate-device",
			"registration": "openhongguo-sdk", "playbackSigning": "argus-ladon-551",
			"playbackRevision": playbackRevision, "supportsBodySigning": true,
			"uptimeSeconds": int64(time.Since(s.started).Seconds()),
			"signatures":    s.signatures.Load(), "failures": s.failures.Load(), "recoveries": s.recoveries.Load(),
			"endpoints": []string{"GET /health", "GET /info", "POST /sign", "POST /recover"},
		})
	case r.Method == http.MethodPost && r.URL.Path == "/sign":
		r.Body = http.MaxBytesReader(w, r.Body, 65536)
		defer r.Body.Close()
		var input struct {
			URL  string  `json:"url"`
			Body *string `json:"body"`
		}
		decoder := json.NewDecoder(r.Body)
		if err := decoder.Decode(&input); err != nil || input.URL == "" || len(input.URL) > 16384 {
			send(w, 400, map[string]string{"error": "a valid URL is required"})
			return
		}
		if err := decoder.Decode(new(any)); err != io.EOF {
			send(w, 400, map[string]string{"error": "invalid JSON body"})
			return
		}
		var body []byte
		if input.Body != nil {
			body = []byte(*input.Body)
		}
		headers, err := s.sign(input.URL, body)
		if err != nil {
			s.failures.Add(1)
			send(w, 400, map[string]string{"error": err.Error()})
			return
		}
		s.signatures.Add(1)
		send(w, 200, headers)
	case r.Method == http.MethodPost && r.URL.Path == "/recover":
		r.Body = http.MaxBytesReader(w, r.Body, 4096)
		defer r.Body.Close()
		var input any
		if err := json.NewDecoder(r.Body).Decode(&input); err != nil && !errors.Is(err, io.EOF) {
			send(w, 400, map[string]string{"error": "invalid JSON body"})
			return
		}
		ctx, cancel := context.WithTimeout(r.Context(), s.registerTimeout+5*time.Second)
		defer cancel()
		rotated, recoveredAt, err := s.recoverDevice(ctx)
		if err != nil {
			log.Printf("device recovery failed: %v", err)
			send(w, 503, map[string]any{"ok": false, "engine": engine, "error": "anonymous device recovery failed"})
			return
		}
		p := s.currentProfile()
		send(w, 200, map[string]any{
			"ok": true, "rotated": rotated, "engine": engine, "restartRequired": false,
			"deviceId": p.Params["device_id"], "recoveredAt": recoveredAt.Unix(),
		})
	default:
		send(w, 404, map[string]string{"error": "not found"})
	}
}

func main() {
	listen := flag.String("listen", "0.0.0.0:8901", "HTTP listen address")
	profilePath := flag.String("profile", "/config/anonymous.json", "registered device profile JSON")
	registerMissing := flag.Bool("register-if-missing", true, "register a device through the SDK if no profile exists")
	registerProgram := flag.String("register-program", "/usr/local/bin/hg-register", "independent SDK registration program")
	registerTimeout := flag.Duration("register-timeout", 60*time.Second, "maximum time for one anonymous device registration")
	recoveryCooldown := flag.Duration("recovery-cooldown", 30*time.Second, "minimum interval between anonymous device rotations")
	version := flag.Bool("version", false, "show engine and upstream revision")
	flag.Parse()
	if *version {
		fmt.Printf("%s %s\n", engine, upstreamRevision)
		return
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	p, err := readProfile(*profilePath)
	if errors.Is(err, os.ErrNotExist) && *registerMissing {
		log.Print("registering one anonymous SDK device; Android/Frida are not used")
		registration, cancel := context.WithTimeout(ctx, *registerTimeout)
		command := exec.CommandContext(registration, *registerProgram, "--profile", *profilePath)
		command.Stdout = io.Discard
		command.Stderr = io.Discard
		if runErr := command.Run(); runErr != nil {
			cancel()
			log.Fatal("SDK device registration failed; the profile was not replaced")
		}
		cancel()
		p, err = readProfile(*profilePath)
	}
	if err != nil {
		log.Fatal("cannot load the registered device profile")
	}
	app := &service{
		profile: p, profilePath: *profilePath, registerProgram: *registerProgram,
		registerTimeout: *registerTimeout, recoveryCooldown: *recoveryCooldown, started: time.Now(),
	}
	server := &http.Server{
		Addr: *listen, Handler: app, ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second,
		WriteTimeout: 75 * time.Second, IdleTimeout: 30 * time.Second, MaxHeaderBytes: 16384,
	}
	failed := make(chan error, 1)
	go func() { failed <- server.ListenAndServe() }()
	log.Printf("hongguo signer listening on %s; engine=%s; Android/Frida are not required", *listen, engine)
	select {
	case err := <-failed:
		if !errors.Is(err, http.ErrServerClosed) {
			log.Fatal(err)
		}
	case <-ctx.Done():
		shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := server.Shutdown(shutdown); err != nil {
			log.Print("HTTP shutdown did not complete before the deadline")
		}
	}
}

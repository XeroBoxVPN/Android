package licensing

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

// EnterpriseProvider is the one place this client talks to your licensing server.
//
// The Android client reaches the same service through your Java SDK; Go cannot call
// that SDK, so this talks HTTP instead. Both sides must agree on the status
// vocabulary in status.go — that shared contract is what keeps the clients in sync.
//
// It compiles and runs with no server configured: Verify then reports
// StatusNotActivated, which with Enforce=false leaves the build usable.
type EnterpriseProvider struct {
	// BaseURL of the licensing service, e.g. https://licensing.example.com.
	// Empty disables the provider.
	BaseURL string

	// DeviceID identifies this installation for seat binding. Defaults to
	// DefaultDeviceID when empty.
	DeviceID string

	// HTTPClient is optional; a client with VerifyTimeout is used when nil.
	HTTPClient *http.Client
}

// NewEnterpriseProvider builds a provider for baseURL, reading the URL from
// SINGBOX_LICENSE_URL when baseURL is empty.
func NewEnterpriseProvider(baseURL string) *EnterpriseProvider {
	if baseURL == "" {
		baseURL = os.Getenv("SINGBOX_LICENSE_URL")
	}
	return &EnterpriseProvider{
		BaseURL:  strings.TrimSuffix(baseURL, "/"),
		DeviceID: DefaultDeviceID(),
	}
}

type licenseRequest struct {
	DeviceID   string `json:"device_id"`
	LicenseKey string `json:"license_key,omitempty"`
	Platform   string `json:"platform"`
}

// licenseResponse is the wire format expected from the licensing server.
//
// Adjust these tags to match your server, or translate in the methods below.
type licenseResponse struct {
	Status    string `json:"status"`
	LicenseID string `json:"license_id"`
	ExpiresAt string `json:"expires_at"`
	Message   string `json:"message"`
}

func (p *EnterpriseProvider) client() *http.Client {
	if p.HTTPClient != nil {
		return p.HTTPClient
	}
	return &http.Client{Timeout: VerifyTimeout}
}

func (p *EnterpriseProvider) deviceID() string {
	if p.DeviceID != "" {
		return p.DeviceID
	}
	return DefaultDeviceID()
}

// Verify implements Provider.
func (p *EnterpriseProvider) Verify(ctx context.Context) (Result, error) {
	if p.BaseURL == "" {
		return Of(StatusNotActivated, "licensing server not configured"), nil
	}
	// -------------------------------------------------------------------------
	// CALL SITE 1 — adjust the path and payload to your server's verify endpoint.
	// -------------------------------------------------------------------------
	return p.post(ctx, "/v1/license/verify", licenseRequest{
		DeviceID: p.deviceID(),
		Platform: runtime.GOOS,
	})
}

// Activate implements Provider.
func (p *EnterpriseProvider) Activate(ctx context.Context, licenseKey string) (Result, error) {
	if strings.TrimSpace(licenseKey) == "" {
		return Of(StatusNotActivated, "empty license key"), nil
	}
	if p.BaseURL == "" {
		return Of(StatusNotActivated, "licensing server not configured"), nil
	}
	// -------------------------------------------------------------------------
	// CALL SITE 2 — adjust to your server's activation endpoint.
	// -------------------------------------------------------------------------
	return p.post(ctx, "/v1/license/activate", licenseRequest{
		DeviceID:   p.deviceID(),
		LicenseKey: licenseKey,
		Platform:   runtime.GOOS,
	})
}

// Deactivate implements Provider.
func (p *EnterpriseProvider) Deactivate(ctx context.Context) error {
	if p.BaseURL == "" {
		return nil
	}
	// -------------------------------------------------------------------------
	// CALL SITE 3 — adjust to your server's seat-release endpoint.
	// -------------------------------------------------------------------------
	_, err := p.post(ctx, "/v1/license/deactivate", licenseRequest{
		DeviceID: p.deviceID(),
		Platform: runtime.GOOS,
	})
	return err
}

func (p *EnterpriseProvider) post(ctx context.Context, path string, payload licenseRequest) (Result, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return Of(StatusUnknown, err.Error()), err
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, p.BaseURL+path, bytes.NewReader(body))
	if err != nil {
		return Of(StatusUnknown, err.Error()), err
	}
	request.Header.Set("Content-Type", "application/json")

	response, err := p.client().Do(request)
	if err != nil {
		// Transport failure is inconclusive, not a denial: the Manager may apply
		// offline grace to StatusUnknown.
		return Of(StatusUnknown, err.Error()), err
	}
	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return Of(StatusUnknown, "licensing server returned "+response.Status), nil
	}

	var decoded licenseResponse
	if err = json.NewDecoder(response.Body).Decode(&decoded); err != nil {
		return Of(StatusUnknown, "malformed licensing response"), err
	}
	return decoded.toResult(), nil
}

func (r licenseResponse) toResult() Result {
	status := Status(strings.ToUpper(strings.TrimSpace(r.Status)))
	switch status {
	case StatusValid, StatusExpired, StatusRevoked, StatusNotActivated, StatusDeviceMismatch:
	default:
		status = StatusUnknown
	}
	result := Result{
		Status:    status,
		LicenseID: r.LicenseID,
		Message:   r.Message,
		CheckedAt: time.Now(),
	}
	if r.ExpiresAt != "" {
		if expiry, err := time.Parse(time.RFC3339, r.ExpiresAt); err == nil {
			result.ExpiresAt = expiry
		}
	}
	// A server that reports valid but with a past expiry is treated as expired,
	// matching the cache check in the Manager and on Android.
	if result.Status == StatusValid && result.Expired() {
		result.Status = StatusExpired
	}
	return result
}

// DefaultDeviceID returns a stable per-installation identifier for seat binding.
//
// It prefers the OS machine ID and falls back to the hostname. Unlike Android's
// ANDROID_ID there is no single portable source, so override DeviceID if your
// deployment has a better one (an MDM asset tag, for example).
func DefaultDeviceID() string {
	for _, path := range []string{"/etc/machine-id", "/var/lib/dbus/machine-id"} {
		if data, err := os.ReadFile(path); err == nil {
			if id := strings.TrimSpace(string(data)); id != "" {
				return id
			}
		}
	}
	if hostname, err := os.Hostname(); err == nil && hostname != "" {
		return hostname
	}
	return "unknown-" + runtime.GOOS + "-" + filepath.Base(os.Args[0])
}

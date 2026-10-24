package licensing

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"time"

	E "github.com/sagernet/sing/common/exceptions"
)

// Manager wraps a Provider with an on-disk cache.
//
// Mirrors LicenseManager.java, including the offline-grace rule: an inconclusive
// verification falls back to a still-fresh cached valid result rather than locking
// out a client whose network is flaky.
//
// Safe for concurrent use.
type Manager struct {
	provider  Provider
	cachePath string
	mutex     sync.Mutex
}

// NewManager returns a Manager storing its cache at cachePath. An empty cachePath
// disables caching, which also disables offline grace.
func NewManager(provider Provider, cachePath string) *Manager {
	return &Manager{provider: provider, cachePath: cachePath}
}

// Cached returns the last stored result. It reports StatusNotActivated when nothing
// is cached, and StatusExpired when a cached valid licence has since expired.
func (m *Manager) Cached() Result {
	if m.cachePath == "" {
		return Of(StatusNotActivated, "no cache configured")
	}
	m.mutex.Lock()
	data, err := os.ReadFile(m.cachePath)
	m.mutex.Unlock()
	if err != nil {
		return Of(StatusNotActivated, "no cached license")
	}
	var result Result
	if err = json.Unmarshal(data, &result); err != nil {
		return Of(StatusUnknown, "corrupt license cache")
	}
	if result.Status == StatusValid && result.Expired() {
		result.Status = StatusExpired
		result.Message = "license expired"
	}
	return result
}

// CacheFresh reports whether the cached result is recent enough to skip a fresh
// verification.
func (m *Manager) CacheFresh() bool {
	cached := m.Cached()
	if cached.CheckedAt.IsZero() {
		return false
	}
	age := time.Since(cached.CheckedAt)
	return age >= 0 && age < CacheTTL
}

// Verify performs a full verification and caches the outcome.
//
// On an inconclusive result it falls back to a still-fresh cached valid licence when
// AllowOfflineGrace is set.
func (m *Manager) Verify(ctx context.Context) Result {
	result, err := m.provider.Verify(ctx)
	if err != nil && result.Status == "" {
		result = Of(StatusUnknown, err.Error())
	}
	if result.CheckedAt.IsZero() {
		result.CheckedAt = time.Now()
	}

	if result.Status == StatusUnknown && AllowOfflineGrace {
		if cached := m.Cached(); cached.Valid() && m.CacheFresh() {
			// Keep the cached result; do not overwrite it with the failed attempt.
			return cached
		}
	}

	m.store(result)
	return result
}

// Activate binds this device to licenseKey and caches the outcome.
func (m *Manager) Activate(ctx context.Context, licenseKey string) Result {
	result, err := m.provider.Activate(ctx, licenseKey)
	if err != nil && result.Status == "" {
		result = Of(StatusUnknown, err.Error())
	}
	if result.CheckedAt.IsZero() {
		result.CheckedAt = time.Now()
	}
	m.store(result)
	return result
}

// Deactivate releases the seat and clears the cache.
func (m *Manager) Deactivate(ctx context.Context) error {
	err := m.provider.Deactivate(ctx)
	m.clear()
	return err
}

// Gate is the connect gate: nil means the service may start.
//
// It returns nil regardless of licence state while Enforce is false, so the build
// stays usable during integration; the caller is expected to log the result.
func (m *Manager) Gate(ctx context.Context) (Result, error) {
	result := m.Verify(ctx)
	if result.Valid() || !Enforce {
		return result, nil
	}
	return result, E.New("license check failed: ", result.DenialMessage())
}

func (m *Manager) store(result Result) {
	if m.cachePath == "" {
		return
	}
	data, err := json.Marshal(result)
	if err != nil {
		return
	}
	m.mutex.Lock()
	defer m.mutex.Unlock()
	if err = os.MkdirAll(filepath.Dir(m.cachePath), 0o755); err != nil {
		return
	}
	// 0600: the cache carries a licence identifier.
	_ = os.WriteFile(m.cachePath, data, 0o600)
}

func (m *Manager) clear() {
	if m.cachePath == "" {
		return
	}
	m.mutex.Lock()
	defer m.mutex.Unlock()
	_ = os.Remove(m.cachePath)
}

// DefaultCachePath returns the cache location for a working directory.
func DefaultCachePath(workingDir string) string {
	if workingDir == "" {
		workingDir = "."
	}
	return filepath.Join(workingDir, "license.cache")
}

package licensing

import "time"

// Result is the outcome of a licence check. It is serialised to the cache file as-is,
// so the JSON tags are part of the on-disk format.
//
// Mirrors LicenseResult.java.
type Result struct {
	Status    Status `json:"status"`
	LicenseID string `json:"license_id,omitempty"`
	// ExpiresAt is the licence's own expiry, zero when not applicable.
	ExpiresAt time.Time `json:"expires_at"`
	// CheckedAt is when this result was produced; drives the cache TTL.
	CheckedAt time.Time `json:"checked_at"`
	Message   string    `json:"message,omitempty"`
}

// Of builds a Result for a failure state carrying no licence payload.
func Of(status Status, message string) Result {
	return Result{
		Status:    status,
		Message:   message,
		CheckedAt: time.Now(),
	}
}

// Valid reports whether this result permits a connection.
func (r Result) Valid() bool {
	return r.Status.PermitsConnection()
}

// Expired reports whether the licence's own expiry has passed. A result may be
// cached as valid and still be expired by the time it is read back.
func (r Result) Expired() bool {
	return !r.ExpiresAt.IsZero() && time.Now().After(r.ExpiresAt)
}

// DenialMessage is the reason to surface when Valid is false. It prefers the
// server-supplied message and falls back to the status's generic text.
func (r Result) DenialMessage() string {
	if r.Message != "" {
		return r.Message
	}
	return r.Status.Message()
}

package licensing

import "time"

// Build-time switches. These mirror LicenseConfig.java; keep the two in step or the
// clients will disagree about whether an unlicensed device may connect.
var (
	// Enforce is the master switch for the connect gate.
	//
	// While false the gate always allows the service to start, so the build stays
	// usable before the provider is wired to your licensing server. The check still
	// runs and is still logged — only the block is skipped.
	//
	// Set to true before shipping.
	Enforce = false

	// AllowOfflineGrace lets a device whose licence could not be verified connect
	// anyway, provided the last successful check is still within CacheTTL. Set false
	// to require a reachable licensing server on every start.
	AllowOfflineGrace = true

	// CacheTTL is how long a cached valid result is trusted before a fresh
	// verification is required.
	CacheTTL = 12 * time.Hour

	// VerifyTimeout bounds a single verification attempt.
	VerifyTimeout = 10 * time.Second
)

// Package licensing provides the client-side licence gate.
//
// The status vocabulary and the gate semantics mirror the Android client's Java
// implementation (io.nekohasekai.sfa.licensing) so both clients behave identically.
// The transport differs by necessity: Android calls the vendor's Java SDK, this
// package talks to the licensing server directly.
package licensing

// Status is the outcome of a licence verification.
//
// This set must stay in step with LicenseStatus.java on the Android side.
type Status string

const (
	// StatusValid means the licence is valid and the client may connect.
	StatusValid Status = "VALID"

	// StatusExpired means the licence is well-formed but past its expiry date.
	StatusExpired Status = "EXPIRED"

	// StatusRevoked means the licence was explicitly revoked by the server.
	StatusRevoked Status = "REVOKED"

	// StatusNotActivated means no licence has been installed on this device.
	StatusNotActivated Status = "NOT_ACTIVATED"

	// StatusDeviceMismatch means the licence is bound to a different device.
	StatusDeviceMismatch Status = "DEVICE_MISMATCH"

	// StatusUnknown means verification could not complete: offline, server error,
	// or a malformed payload. This is the only status eligible for offline grace.
	StatusUnknown Status = "UNKNOWN"
)

// String lets a Status be passed straight to the sing logger, whose formatter
// panics on named string types that are not Stringers.
func (s Status) String() string {
	return string(s)
}

// PermitsConnection reports whether this status allows the service to start.
func (s Status) PermitsConnection() bool {
	return s == StatusValid
}

// Message returns the operator-facing explanation for a non-connecting status.
// It mirrors LicenseGate.denialMessage on Android.
func (s Status) Message() string {
	switch s {
	case StatusValid:
		return ""
	case StatusExpired:
		return "license has expired; renew it to reconnect"
	case StatusRevoked:
		return "license has been revoked; contact your administrator"
	case StatusNotActivated:
		return "no license found; activate this device to continue"
	case StatusDeviceMismatch:
		return "license is registered to another device"
	default:
		return "license could not be verified; check connectivity"
	}
}

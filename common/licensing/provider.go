package licensing

import "context"

// Provider is the seam between this client and your licensing backend.
//
// Implement it once (see enterprise.go) and the rest of the codebase stays
// independent of that backend's API. Mirrors LicenseProvider.java.
//
// Implementations must not panic and must not return an error for an ordinary
// "not licensed" answer: report that through Status. An error means the check
// itself could not be carried out, which the Manager translates into
// StatusUnknown and therefore into offline grace.
type Provider interface {
	// Verify checks the current licence state, contacting the licensing server when
	// the implementation requires it. It must honour ctx cancellation.
	Verify(ctx context.Context) (Result, error)

	// Activate binds this device to licenseKey.
	Activate(ctx context.Context, licenseKey string) (Result, error)

	// Deactivate releases this device's seat.
	Deactivate(ctx context.Context) error
}

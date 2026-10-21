package io.nekohasekai.sfa.licensing;

/** Outcome of a license verification. */
public enum LicenseStatus {
  /** License is valid; the client may connect. */
  VALID,

  /** License is well-formed but past its expiry date. */
  EXPIRED,

  /** License was explicitly revoked by the licensing server. */
  REVOKED,

  /** No license has been installed on this device yet. */
  NOT_ACTIVATED,

  /** License exists but is bound to a different device. */
  DEVICE_MISMATCH,

  /** Verification could not complete (offline, server error, malformed payload). */
  UNKNOWN;

  /** Whether this status permits the VPN to start. */
  public boolean permitsConnection() {
    return this == VALID;
  }
}

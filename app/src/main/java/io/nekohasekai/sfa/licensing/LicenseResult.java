package io.nekohasekai.sfa.licensing;

/** Immutable result of a license check, safe to cache and pass across threads. */
public final class LicenseResult {

  private final LicenseStatus status;
  private final String licenseId;
  private final long expiresAtMillis;
  private final String message;
  private final long checkedAtMillis;

  public LicenseResult(
      LicenseStatus status,
      String licenseId,
      long expiresAtMillis,
      String message,
      long checkedAtMillis) {
    this.status = status == null ? LicenseStatus.UNKNOWN : status;
    this.licenseId = licenseId;
    this.expiresAtMillis = expiresAtMillis;
    this.message = message;
    this.checkedAtMillis = checkedAtMillis;
  }

  /** Convenience factory for failure states carrying no license payload. */
  public static LicenseResult of(LicenseStatus status, String message) {
    return new LicenseResult(status, null, 0L, message, System.currentTimeMillis());
  }

  public LicenseStatus getStatus() {
    return status;
  }

  /** Opaque license identifier, may be null when not activated. */
  public String getLicenseId() {
    return licenseId;
  }

  /** Epoch millis at which the license expires, or 0 when not applicable. */
  public long getExpiresAtMillis() {
    return expiresAtMillis;
  }

  /** Human-readable detail for logs and UI, may be null. */
  public String getMessage() {
    return message;
  }

  /** Epoch millis at which this result was produced. */
  public long getCheckedAtMillis() {
    return checkedAtMillis;
  }

  public boolean isValid() {
    return status.permitsConnection();
  }

  @Override
  public String toString() {
    return "LicenseResult{status="
        + status
        + ", licenseId="
        + licenseId
        + ", message="
        + message
        + "}";
  }
}

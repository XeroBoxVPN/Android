package io.nekohasekai.sfa.licensing;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * The single place where your enterprise licensing library is called.
 *
 * <p>This class compiles as-is without the vendor SDK on the classpath so the project keeps
 * building before the library is dropped in. Wire your SDK into the three marked call sites and
 * delete the fallbacks.
 *
 * <p>Drop the library into {@code app/libs/} (see {@code app/build.gradle.kts}) or declare it as a
 * Maven coordinate, then replace the bodies below.
 */
public final class EnterpriseLicenseProvider implements LicenseProvider {

  private static final String TAG = "EnterpriseLicense";

  /**
   * When true, a verification that cannot reach the licensing server falls back to the last cached
   * VALID result instead of failing closed. Enterprise deployments with offline clients usually want
   * this; set false to require connectivity on every start.
   */
  private final boolean allowOfflineGrace;

  public EnterpriseLicenseProvider() {
    this(true);
  }

  public EnterpriseLicenseProvider(boolean allowOfflineGrace) {
    this.allowOfflineGrace = allowOfflineGrace;
  }

  public boolean isOfflineGraceAllowed() {
    return allowOfflineGrace;
  }

  @Override
  public LicenseResult verify(Context context) {
    try {
      // ---------------------------------------------------------------------
      // CALL SITE 1 — verification against your enterprise library.
      //
      // Example shape (replace with your SDK's real API):
      //
      //   VendorLicense lic = VendorLicensing.getInstance(context).check(deviceId(context));
      //   if (lic.isValid()) {
      //     return new LicenseResult(
      //         LicenseStatus.VALID, lic.getId(), lic.getExpiryMillis(), null,
      //         System.currentTimeMillis());
      //   }
      //   if (lic.isExpired()) {
      //     return LicenseResult.of(LicenseStatus.EXPIRED, lic.getReason());
      //   }
      //   if (lic.isRevoked()) {
      //     return LicenseResult.of(LicenseStatus.REVOKED, lic.getReason());
      //   }
      //   return LicenseResult.of(LicenseStatus.DEVICE_MISMATCH, lic.getReason());
      // ---------------------------------------------------------------------

      Log.w(TAG, "Licensing library not wired up yet; verify() returning NOT_ACTIVATED");
      return LicenseResult.of(
          LicenseStatus.NOT_ACTIVATED, "Licensing library not configured");
    } catch (RuntimeException e) {
      // Never propagate vendor SDK failures into the connect path.
      Log.e(TAG, "License verification failed", e);
      return LicenseResult.of(LicenseStatus.UNKNOWN, e.getMessage());
    }
  }

  @Override
  public LicenseResult activate(Context context, String licenseKey) {
    if (licenseKey == null || licenseKey.trim().isEmpty()) {
      return LicenseResult.of(LicenseStatus.NOT_ACTIVATED, "Empty license key");
    }
    try {
      // ---------------------------------------------------------------------
      // CALL SITE 2 — activation.
      //
      //   VendorLicense lic =
      //       VendorLicensing.getInstance(context).activate(licenseKey, deviceId(context));
      //   return new LicenseResult(
      //       lic.isValid() ? LicenseStatus.VALID : LicenseStatus.NOT_ACTIVATED,
      //       lic.getId(), lic.getExpiryMillis(), lic.getReason(), System.currentTimeMillis());
      // ---------------------------------------------------------------------

      Log.w(TAG, "Licensing library not wired up yet; activate() returning UNKNOWN");
      return LicenseResult.of(LicenseStatus.UNKNOWN, "Licensing library not configured");
    } catch (RuntimeException e) {
      Log.e(TAG, "License activation failed", e);
      return LicenseResult.of(LicenseStatus.UNKNOWN, e.getMessage());
    }
  }

  @Override
  public void deactivate(Context context) {
    try {
      // ---------------------------------------------------------------------
      // CALL SITE 3 — deactivation / seat release.
      //
      //   VendorLicensing.getInstance(context).deactivate();
      // ---------------------------------------------------------------------
      Log.w(TAG, "Licensing library not wired up yet; deactivate() is a no-op");
    } catch (RuntimeException e) {
      Log.e(TAG, "License deactivation failed", e);
    }
  }

  /**
   * Stable per-install identifier suitable for seat binding.
   *
   * <p>{@code ANDROID_ID} is per app-signing-key and per user since Android 8, and resets on factory
   * reset, which is the usual behaviour wanted for seat counting. Swap this for your SDK's own
   * fingerprint if it provides one.
   */
  public static String deviceId(Context context) {
    String androidId =
        Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    if (androidId == null || androidId.isEmpty()) {
      return "unknown-" + Build.MODEL;
    }
    return androidId;
  }
}

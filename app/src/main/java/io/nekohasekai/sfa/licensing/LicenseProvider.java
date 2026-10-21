package io.nekohasekai.sfa.licensing;

import android.content.Context;

/**
 * Adapter seam between the app and your enterprise licensing library.
 *
 * <p>Implement this once against your vendor SDK (see {@code EnterpriseLicenseProvider}) and the
 * rest of the app stays independent of that SDK's API. Swapping vendors then means editing one
 * class.
 *
 * <p>Implementations are always called from a background thread and must never block the main
 * thread. They must not throw: return {@link LicenseStatus#UNKNOWN} instead.
 */
public interface LicenseProvider {

  /**
   * Performs a verification, contacting the licensing server when the implementation requires it.
   *
   * @param context application context
   * @return never null
   */
  LicenseResult verify(Context context);

  /**
   * Activates this device with the supplied license key.
   *
   * @param context application context
   * @param licenseKey key entered by the user or provisioned by MDM
   * @return never null
   */
  LicenseResult activate(Context context, String licenseKey);

  /** Clears any locally stored activation. */
  void deactivate(Context context);
}

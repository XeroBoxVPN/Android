package io.nekohasekai.sfa.licensing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Caching facade over a {@link LicenseProvider}.
 *
 * <p>Two entry points with different costs:
 *
 * <ul>
 *   <li>{@link #cachedResult(Context)} — instant, no I/O beyond SharedPreferences. Used at startup
 *       so the UI can render immediately.
 *   <li>{@link #verifyBlocking(Context)} — authoritative, may hit the network. Must be called off
 *       the main thread; the Kotlin bridge in {@code LicenseGate} does that for you.
 * </ul>
 *
 * <p>Thread-safe. All writes to the cache are synchronized on the singleton.
 */
public final class LicenseManager {

  private static final String TAG = "LicenseManager";

  private static final String PREFS = "license_cache";
  private static final String KEY_STATUS = "status";
  private static final String KEY_LICENSE_ID = "license_id";
  private static final String KEY_EXPIRES_AT = "expires_at";
  private static final String KEY_CHECKED_AT = "checked_at";

  /** How long a cached VALID result is trusted before a fresh verification is required. */
  private static final long CACHE_TTL_MILLIS = 12L * 60L * 60L * 1000L; // 12 hours

  private static volatile LicenseManager instance;

  private final LicenseProvider provider;

  private LicenseManager(LicenseProvider provider) {
    this.provider = provider;
  }

  public static LicenseManager getInstance() {
    LicenseManager local = instance;
    if (local == null) {
      synchronized (LicenseManager.class) {
        local = instance;
        if (local == null) {
          local =
              new LicenseManager(
                  new EnterpriseLicenseProvider(LicenseConfig.ALLOW_OFFLINE_GRACE));
          instance = local;
        }
      }
    }
    return local;
  }

  /**
   * Overrides the provider, for tests or for a build flavour that ships a different licensing
   * backend. Call before first use.
   */
  public static void setProvider(LicenseProvider provider) {
    synchronized (LicenseManager.class) {
      instance = new LicenseManager(provider);
    }
  }

  /**
   * Last known result, read straight from the local cache. Returns {@link
   * LicenseStatus#NOT_ACTIVATED} when nothing has been cached, and {@link LicenseStatus#EXPIRED}
   * when the cached license's own expiry has passed.
   *
   * <p>Cheap enough for the main thread.
   */
  public LicenseResult cachedResult(Context context) {
    SharedPreferences prefs = prefs(context);
    String statusName = prefs.getString(KEY_STATUS, null);
    if (statusName == null) {
      return LicenseResult.of(LicenseStatus.NOT_ACTIVATED, "No cached license");
    }

    LicenseStatus status;
    try {
      status = LicenseStatus.valueOf(statusName);
    } catch (IllegalArgumentException e) {
      return LicenseResult.of(LicenseStatus.UNKNOWN, "Corrupt cache");
    }

    long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L);
    long checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L);
    String licenseId = prefs.getString(KEY_LICENSE_ID, null);

    // A cached VALID whose expiry has since passed is expired, regardless of the cached status.
    if (status == LicenseStatus.VALID && expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
      return new LicenseResult(
          LicenseStatus.EXPIRED, licenseId, expiresAt, "License expired", checkedAt);
    }

    return new LicenseResult(status, licenseId, expiresAt, null, checkedAt);
  }

  /** Whether the cached result is recent enough to skip a fresh verification. */
  public boolean isCacheFresh(Context context) {
    long checkedAt = prefs(context).getLong(KEY_CHECKED_AT, 0L);
    if (checkedAt <= 0L) {
      return false;
    }
    long age = System.currentTimeMillis() - checkedAt;
    return age >= 0L && age < CACHE_TTL_MILLIS;
  }

  /**
   * Full verification. Blocking — call from a background thread.
   *
   * <p>On an inconclusive result ({@link LicenseStatus#UNKNOWN}, typically offline) this falls back
   * to a still-fresh cached VALID when the provider allows offline grace, so field clients are not
   * locked out by a flaky network.
   */
  public LicenseResult verifyBlocking(Context context) {
    LicenseResult result = provider.verify(context);

    if (result.getStatus() == LicenseStatus.UNKNOWN && allowsOfflineGrace()) {
      LicenseResult cached = cachedResult(context);
      if (cached.isValid() && isCacheFresh(context)) {
        Log.i(TAG, "Verification inconclusive; honouring fresh cached license");
        return cached;
      }
    }

    store(context, result);
    return result;
  }

  /** Activates this device and caches the outcome. Blocking — call from a background thread. */
  public LicenseResult activateBlocking(Context context, String licenseKey) {
    LicenseResult result = provider.activate(context, licenseKey);
    store(context, result);
    return result;
  }

  /** Releases the seat and clears the cache. Blocking — call from a background thread. */
  public void deactivateBlocking(Context context) {
    provider.deactivate(context);
    clear(context);
  }

  private boolean allowsOfflineGrace() {
    return !(provider instanceof EnterpriseLicenseProvider)
        || ((EnterpriseLicenseProvider) provider).isOfflineGraceAllowed();
  }

  private synchronized void store(Context context, LicenseResult result) {
    prefs(context)
        .edit()
        .putString(KEY_STATUS, result.getStatus().name())
        .putString(KEY_LICENSE_ID, result.getLicenseId())
        .putLong(KEY_EXPIRES_AT, result.getExpiresAtMillis())
        .putLong(KEY_CHECKED_AT, result.getCheckedAtMillis())
        .apply();
  }

  private synchronized void clear(Context context) {
    prefs(context).edit().clear().apply();
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}

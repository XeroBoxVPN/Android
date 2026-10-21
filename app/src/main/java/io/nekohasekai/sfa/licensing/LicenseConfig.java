package io.nekohasekai.sfa.licensing;

/** Build-time switches for the licensing layer. */
public final class LicenseConfig {

  private LicenseConfig() {}

  /**
   * Master switch for the connect gate.
   *
   * <p>While this is {@code false} the gate always allows the VPN to start, so the app stays usable
   * before {@code EnterpriseLicenseProvider} is wired to your vendor SDK. The check still runs and
   * its result is still published to {@code LicenseGate.state}, so the UI can show licence status
   * during integration — only the block is skipped.
   *
   * <p><b>Set this to {@code true} before shipping.</b>
   */
  public static final boolean ENFORCE_LICENSING = false;

  /**
   * When true, a device whose licence cannot be verified because it is offline may still connect if
   * the last successful check is within {@code LicenseManager}'s cache TTL. Set false to require a
   * reachable licensing server on every connect.
   */
  public static final boolean ALLOW_OFFLINE_GRACE = true;
}

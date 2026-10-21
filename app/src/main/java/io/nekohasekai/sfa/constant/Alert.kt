package io.nekohasekai.sfa.constant

enum class Alert {
    RequestVPNPermission,
    RequestNotificationPermission,
    RequestLocationPermission,
    EmptyConfiguration,
    StartCommandServer,
    CreateService,
    StartService,

    /** A personal config's account has no data left, expired or is suspended. */
    DataExhausted,
}

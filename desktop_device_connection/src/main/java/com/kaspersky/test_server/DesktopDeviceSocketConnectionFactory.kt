package com.kaspersky.test_server

import com.kaspersky.test_server.api.Executor
import com.kaspresky.test_server.log.Logger
import com.kaspersky.test_server.DesktopDeviceSocketConnectionType.*

object DesktopDeviceSocketConnectionFactory {

    fun getSockets(
        desktopDeviceSocketConnectionType: DesktopDeviceSocketConnectionType,
        executor: Executor<String>,
        logger: Logger
    ): DesktopDeviceSocketConnection {
        return when(desktopDeviceSocketConnectionType) {
            FORWARD -> DesktopDeviceSocketConnectionForwardImpl(executor, logger)
            REVERSE -> throw RuntimeException()
        }
    }

}
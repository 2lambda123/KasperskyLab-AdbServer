package com.kaspersky.test_server

import com.kaspersky.test_server.api.ConnectionFactory
import com.kaspersky.test_server.api.ConnectionServer
import com.kaspresky.test_server.log.Logger
import com.kaspresky.test_server.log.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

internal class DeviceMirror private constructor(
    val deviceName: String,
    private val connectionServer: ConnectionServer
) {

    companion object {
        private const val CONNECTION_WAIT_MS = 500L

        fun create(
            deviceName: String,
            adbServerPort: String?,
            cmdCommandPerformer: CmdCommandPerformer,
            logger: Logger
        ): DeviceMirror {
            val desktopDeviceSocketConnection =
                DesktopDeviceSocketConnectionFactory.getSockets(
                    DesktopDeviceSocketConnectionType.FORWARD
                )
            val commandExecutor = CommandExecutorImpl(
                cmdCommandPerformer, deviceName, adbServerPort
            )
            val connectionServer = ConnectionFactory.createServer(
                desktopDeviceSocketConnection.getDesktopSocketLoad(commandExecutor),
                commandExecutor
            )
            return DeviceMirror(deviceName, connectionServer)
        }
    }

    private val logger = LoggerFactory.getLogger(tag = javaClass.simpleName)
    private val isRunning = AtomicReference<Boolean>()

    fun startConnectionToDevice() {
        logger.i("startConnectionToDevice", "connect to device=$deviceName start")
        isRunning.set(true)
        WatchdogThread().start()
    }

    fun stopConnectionToDevice() {
        logger.i("stopConnectionToDevice", "connection to device=$deviceName was stopped")
        isRunning.set(false)
        connectionServer.tryDisconnect()
    }

    private inner class WatchdogThread : Thread() {
        override fun run() {
            logger.i("WatchdogThread.run", "WatchdogThread is started from Desktop to Device=$deviceName")
            while (isRunning.get()) {
                if (!connectionServer.isConnected()) {
                    try {
                        logger.i("WatchdogThread.run", "Try to connect to Device=$deviceName...")
                        connectionServer.tryConnect()
                    } catch (exception: Exception) {
                        logger.i("WatchdogThread,run", "The attempt to connect to Device=$deviceName was with exception: $exception")
                    }
                }
                sleep(CONNECTION_WAIT_MS)
            }
        }
    }
}
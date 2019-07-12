package com.kaspersky.test_server.implementation

import com.kaspersky.test_server.api.*
import com.kaspersky.test_server.implementation.light_socket.LightSocketWrapperImpl
import com.kaspersky.test_server.implementation.transferring.ResultMessage
import com.kaspersky.test_server.implementation.transferring.SocketMessagesTransferring
import com.kaspersky.test_server.implementation.transferring.TaskMessage
import com.kaspresky.test_server.log.Logger
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class ConnectionClientImplBySocket(
    private val socketCreation: () -> Socket,
    private val logger: Logger
) : ConnectionClient {

    companion object {
        private val COMMAND_TIMEOUT_MIN = TimeUnit.MINUTES.toSeconds(3)
    }

    private val tag = javaClass.simpleName

    private var _socket: Socket? = null
    private val socket: Socket
        get() = _socket ?: throw IllegalStateException("tryConnect must be called first")


    private var _socketMessagesTransferring: SocketMessagesTransferring<ResultMessage<CommandResult>, TaskMessage>? = null
    private val socketMessagesTransferring: SocketMessagesTransferring<ResultMessage<CommandResult>, TaskMessage>
        get() = _socketMessagesTransferring ?: throw IllegalStateException("tryConnect must be called first")

    private var connectionMaker: ConnectionMaker = ConnectionMaker(logger)
    private val commandsInProgress = ConcurrentHashMap<Command, ResultWaiter<ResultMessage<CommandResult>>>()

    override fun tryConnect() {
        logger.i(tag, "tryConnect", "start")
        connectionMaker.connect(
            connectAction = { _socket = socketCreation.invoke() },
            successConnectAction = {
                logger.i(tag, "tryConnect", "start handleMessages")
                handleMessages()
            }
        )
        logger.i(tag, "tryConnect", "attempt completed")
    }

    private fun handleMessages() {
        _socketMessagesTransferring = SocketMessagesTransferring.createTransferring(
            lightSocketWrapper = LightSocketWrapperImpl(socket),
            logger = logger,
            disruptAction = { tryDisconnect() }
        )
        socketMessagesTransferring.startListening { resultMessage ->
            logger.i(tag, "handleMessages", "received resultMessage=$resultMessage")
            commandsInProgress[resultMessage.command]?.latchResult(resultMessage)
        }
    }

    override fun tryDisconnect() {
        logger.i(tag, "tryDisconnect", "start")
        connectionMaker.disconnect {
            socketMessagesTransferring.stopListening()
            socket.close()
            resetCommandsInProgress()
        }
        logger.i(tag, "tryDisconnect", "attempt completed")
    }

    private fun resetCommandsInProgress() {
        for ((adbCommand, resultWaiter) in commandsInProgress) {
            val commandResult = CommandResult(ExecutorResultStatus.FAILED, "tryDisconnect was called")
            logger.i(tag, "resetCommandsInProgress", "command=$adbCommand was failed because of disconnecting. " +
                    "result=$commandResult"
            )
            resultWaiter.latchResult(
                ResultMessage(adbCommand, commandResult)
            )
        }
        commandsInProgress.clear()
    }

    override fun isConnected(): Boolean =
        connectionMaker.isConnected()

    override fun executeCommand(command: Command): CommandResult {
        logger.i(tag, "executeAdbCommand", "started command=$command")

        val resultWaiter = ResultWaiter<ResultMessage<CommandResult>>()
        commandsInProgress[command] = resultWaiter
        socketMessagesTransferring.sendMessage(
            TaskMessage(command)
        )

        val resultMessage: ResultMessage<CommandResult>?
        try {
            resultMessage = resultWaiter.waitResult(COMMAND_TIMEOUT_MIN, TimeUnit.SECONDS)
        } catch (exception: InterruptedException) {
            val failedCommandResult = CommandResult(ExecutorResultStatus.FAILED, "Waiting thread was interrupted")
            logger.i(tag, "executeAdbCommand","command=$command failed with commandResult=$failedCommandResult")
            return failedCommandResult
        } finally {
            commandsInProgress.remove(command)
        }

        if (resultMessage == null) {
            val failedCommandResult = CommandResult(ExecutorResultStatus.FAILED, "Waiting result timeout was expired")
            logger.i(tag, "executeAdbCommand","command=$command failed with commandResult=$failedCommandResult")
            return failedCommandResult
        }
        logger.i(tag, "executeAdbCommand","command=$command completed with commandResult=${resultMessage.data}")

        return resultMessage.data
    }

}
package com.kaspersky.test_server

import com.kaspresky.test_server.log.LoggerFactory

internal fun main(args: Array<String>) {
    val argsList = args.toList()
    val emulators = argsList
        .firstOrNull { arg -> arg.contains("emulators") }
        ?.removeSurrounding(prefix = "emulators", suffix = "")
        ?.replace("=", "")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf()
    val adbServerPort = argsList
        .firstOrNull { arg -> arg.contains("adbServerPort") }
        ?.removeSurrounding(prefix = "adbServerPort", suffix = "")
        ?.replace("=", "")
        ?.trim()

    val logger = LoggerFactory.systemLogger()
    val cmdCommandPerformer = CmdCommandPerformer()
    val desktop = Desktop(
        cmdCommandPerformer = cmdCommandPerformer,
        presetEmulators = emulators,
        adbServerPort = adbServerPort,
        logger = logger
    )
    desktop.startDevicesObserving()
}
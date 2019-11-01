package com.kaspresky.test_server.log.filter_log

import java.util.*

internal interface RecordAnswer

internal class RecordInProgress : RecordAnswer

internal data class ReadyRecord(
    /**
     * last log is at the first position
     */
    val recordingStack: Deque<LogData>,
    /**
     * The count of recordingStack
     */
    val countOfRecordingStack: Int = 0,
    /**
     * last log is at the first position
     */
    val remainedStack: Deque<LogData>
) : RecordAnswer
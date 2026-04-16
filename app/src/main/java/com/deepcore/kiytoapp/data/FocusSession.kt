package com.deepcore.kiytoapp.data

import java.util.Date

data class FocusSession(
    val id: Long = 0,
    val startTime: Date,
    val duration: Long,  // in Millisekunden
    val completed: Boolean,
    val interrupted: Boolean = false
) 
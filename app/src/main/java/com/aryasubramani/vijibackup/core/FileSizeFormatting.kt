package com.aryasubramani.vijibackup.core

import android.content.Context
import android.text.format.Formatter

internal fun formatReadableFileSize(context: Context, byteCount: Long): String =
    Formatter.formatShortFileSize(context, byteCount)

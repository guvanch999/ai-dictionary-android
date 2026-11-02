package com.sozluk.ai.ui.common

import java.text.DateFormat
import java.util.Date

/** Locale-aware medium date string for a createdAt timestamp. */
fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

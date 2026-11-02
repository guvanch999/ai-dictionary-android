package com.sozluk.ai.data.db

import kotlinx.serialization.json.Json

/** Shared JSON instance for entity list (de)serialization in mappers. */
internal val dbJson = Json { ignoreUnknownKeys = true }

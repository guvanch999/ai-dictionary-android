package com.sozluk.ai.domain.model

import kotlinx.serialization.Serializable

/** A usage example in the three project languages. Serialized inside examplesJson. */
@Serializable
data class Example(
    val en: String,
    val ru: String,
    val tk: String,
)

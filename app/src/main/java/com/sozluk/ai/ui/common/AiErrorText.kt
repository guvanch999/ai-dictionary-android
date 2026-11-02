package com.sozluk.ai.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sozluk.ai.R
import com.sozluk.ai.domain.ai.AiError

/** Human-readable message for an [AiError] (spec §10: errors never crash, always readable). */
@Composable
fun aiErrorMessage(error: AiError): String = when (error) {
    is AiError.NoApiKey -> stringResource(R.string.error_no_api_key)
    is AiError.Network -> stringResource(R.string.error_network)
    is AiError.RateLimited -> stringResource(R.string.error_rate_limited)
    is AiError.InvalidKey -> stringResource(R.string.error_invalid_key)
    is AiError.InvalidResponse -> stringResource(R.string.error_invalid_response)
    is AiError.Unknown -> stringResource(R.string.error_unknown)
}

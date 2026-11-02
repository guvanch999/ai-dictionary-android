package com.sozluk.ai.data.db

import androidx.room.TypeConverter
import com.sozluk.ai.domain.model.QuizType
import com.sozluk.ai.domain.model.SessionState
import com.sozluk.ai.domain.model.SourceType
import com.sozluk.ai.domain.model.WordStatus

/** Enum <-> String converters. Stored by name (not ordinal) to survive value reordering. */
class Converters {
    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromWordStatus(value: WordStatus): String = value.name

    @TypeConverter
    fun toWordStatus(value: String): WordStatus = WordStatus.valueOf(value)

    @TypeConverter
    fun fromQuizType(value: QuizType): String = value.name

    @TypeConverter
    fun toQuizType(value: String): QuizType = QuizType.valueOf(value)

    @TypeConverter
    fun fromSessionState(value: SessionState): String = value.name

    @TypeConverter
    fun toSessionState(value: String): SessionState = SessionState.valueOf(value)
}

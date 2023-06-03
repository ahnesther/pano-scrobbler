package com.arn.scrobble.db

import androidx.room.TypeConverter

object Converters {
    @TypeConverter
    fun fromCommaSeperatedString(value: String?) =
        value?.split(", ")?.filter { it.isNotEmpty() }?.toSet()

    @TypeConverter
    fun toCommaSeperatedString(value: Set<String>?) =
        value?.joinToString()?.ifEmpty { null }
}

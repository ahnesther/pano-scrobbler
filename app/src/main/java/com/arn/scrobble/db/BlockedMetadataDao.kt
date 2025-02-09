package com.arn.scrobble.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.umass.lastfm.scrobble.ScrobbleData

@Dao
interface BlockedMetadataDao {
    @Query("SELECT * FROM $tableName ORDER BY _id DESC")
    fun all(): List<BlockedMetadata>

    @Query("SELECT * FROM $tableName ORDER BY _id DESC")
    fun allLd(): LiveData<List<BlockedMetadata>>

    @Query("SELECT count(1) FROM $tableName")
    fun count(): Int

    @Query(
        """SELECT * FROM $tableName
          WHERE artist IN ("", :artist) AND
          album IN ("", :album) AND
          albumArtist IN ("", :albumArtist) AND
          track IN ("", :track) AND
          NOT (artist == "" AND album == "" AND albumArtist == "" AND track == "")
    """
    )
    fun getBlockedEntries(
        artist: String,
        album: String,
        albumArtist: String,
        track: String,
    ): List<BlockedMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(e: List<BlockedMetadata>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(e: List<BlockedMetadata>)

    @Delete
    fun delete(e: BlockedMetadata)

    @Query("DELETE FROM $tableName")
    fun nuke()

    companion object {
        const val tableName = "blockedMetadata"

        fun BlockedMetadataDao.getBlockedEntry(
            scrobbleData: ScrobbleData,
        ): BlockedMetadata? {
            val entries = getBlockedEntries(
                scrobbleData.artist?.lowercase() ?: "",
                scrobbleData.album?.lowercase() ?: "",
                scrobbleData.albumArtist?.lowercase() ?: "",
                scrobbleData.track?.lowercase() ?: "",
            )

            if (entries.isEmpty()) return null
            if (entries.size == 1) return entries[0]

            // conflict resolution for multiple entries
            val entriesToBlanks = mutableListOf<Pair<BlockedMetadata, Int>>()
            entries.forEach {
                val blanks =
                    arrayOf(it.artist, it.album, it.albumArtist, it.track).count { it.isBlank() }
                entriesToBlanks += it to blanks
            }

            entriesToBlanks.sortBy { it.second }

            val lowestBlanks = entriesToBlanks[0].second
            val maxIdx = entriesToBlanks.indexOfLast { it.second == lowestBlanks }
            val bestEntries = entriesToBlanks.map { it.first }.subList(0, maxIdx + 1)
            val bestEntry = bestEntries.first()
            val skip = bestEntries.any { it.skip }
            val mute = !skip && bestEntries.any { it.mute }
            return bestEntry.copy(skip = skip, mute = mute)
        }


        fun BlockedMetadataDao.insertLowerCase(
            entries: List<BlockedMetadata>,
            ignore: Boolean = false
        ) {
            val lowercaseEntries = entries.map { entry ->
                entry.copy(
                    artist = entry.artist.lowercase(),
                    album = entry.album.lowercase(),
                    albumArtist = entry.albumArtist.lowercase(),
                    track = entry.track.lowercase()
                )
            }
            if (ignore)
                insertIgnore(lowercaseEntries)
            else
                insert(lowercaseEntries)
        }

    }
}
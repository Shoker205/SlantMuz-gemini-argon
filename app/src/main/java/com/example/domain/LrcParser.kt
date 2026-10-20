package com.example.domain

/**
 * LRC synchronized lyrics parser and active timecode locator.
 * Supports [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], and multi-tag lines.
 */
object LrcParser {

    private val timeTagRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[\.:](\d{1,3}))?\]""")

    fun parseLrc(
        rawLrc: String,
        trackId: String = "",
        title: String = "",
        artist: String = ""
    ): SyncedLyrics {
        if (rawLrc.isBlank()) {
            return SyncedLyrics(
                trackId = trackId,
                title = title,
                artist = artist,
                isInstrumental = true,
                isSynced = false
            )
        }

        val parsedLines = mutableListOf<LyricsLine>()
        val plainBuilder = StringBuilder()

        rawLrc.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            val matches = timeTagRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricText = trimmed.replace(timeTagRegex, "").trim()
                for (match in matches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionRaw = match.groupValues.getOrNull(3).orEmpty()
                    val millis = when (fractionRaw.length) {
                        0 -> 0L
                        1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                        2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                        else -> fractionRaw.take(3).toLongOrNull() ?: 0L
                    }
                    val totalMs = minutes * 60_000L + seconds * 1_000L + millis
                    if (lyricText.isNotBlank() || parsedLines.isNotEmpty()) {
                        parsedLines.add(
                            LyricsLine(
                                timestampMs = totalMs,
                                text = lyricText.ifBlank { "♪ • • ♪" }
                            )
                        )
                    }
                }
                if (lyricText.isNotBlank()) {
                    plainBuilder.appendLine(lyricText)
                }
            } else if (!trimmed.startsWith("[ti:") &&
                !trimmed.startsWith("[ar:") &&
                !trimmed.startsWith("[al:") &&
                !trimmed.startsWith("[by:") &&
                !trimmed.startsWith("[offset:")
            ) {
                plainBuilder.appendLine(trimmed)
            }
        }

        val sorted = parsedLines.sortedBy { it.timestampMs }
        val plainText = plainBuilder.toString().trim()
        val isInstrumental = sorted.isEmpty() &&
            (plainText.isBlank() || plainText.equals("Instrumental", ignoreCase = true))

        return SyncedLyrics(
            trackId = trackId,
            title = title,
            artist = artist,
            isInstrumental = isInstrumental,
            isSynced = sorted.isNotEmpty(),
            lines = sorted,
            plainLyrics = plainText
        )
    }

    /**
     * Returns the index of the active lyric line for a given playback position in milliseconds,
     * or -1 if playback hasn't reached the first line yet.
     */
    fun findActiveLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs + 120L) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

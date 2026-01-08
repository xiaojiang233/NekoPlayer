package top.xiaojiang233.nekoplayer.utils

import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.util.regex.Pattern

data class LyricLine(
    val time: Long,
    val text: String,
    val translation: String? = null // Translation text (if exists)
)

object LyricsParser {
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")

    fun parse(lrcContent: String): List<LyricLine> {
        val tempLyrics = mutableListOf<Pair<Long, String>>()
        val reader = BufferedReader(StringReader(lrcContent))
        var line: String? = reader.readLine()

        while (line != null) {
            // Find all timestamps in this line
            val timestamps = mutableListOf<Long>()
            val matcher = TIME_TAG_PATTERN.matcher(line)
            var lastEnd = 0

            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLong() ?: 0
                val seconds = matcher.group(2)?.toLong() ?: 0
                val millisStr = matcher.group(3) ?: "00"
                val millis = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()

                val time = minutes * 60000 + seconds * 1000 + millis
                timestamps.add(time)
                lastEnd = matcher.end()
            }

            // Get the text after all timestamps
            if (timestamps.isNotEmpty() && lastEnd < line.length) {
                val text = line.substring(lastEnd).trim()
                if (text.isNotEmpty()) {
                    // Add the same text for each timestamp
                    timestamps.forEach { time ->
                        tempLyrics.add(Pair(time, text))
                    }
                }
            }

            line = reader.readLine()
        }

        // Merge lines with the same timestamp
        val mergedLyrics = mutableListOf<LyricLine>()
        val groupedByTime = tempLyrics.groupBy { it.first }

        groupedByTime.forEach { (time, lines) ->
            when (lines.size) {
                1 -> {
                    // Single line - no translation
                    mergedLyrics.add(LyricLine(time, lines[0].second, null))
                }
                2 -> {
                    // Two lines with same time - treat second as translation
                    mergedLyrics.add(LyricLine(time, lines[0].second, lines[1].second))
                }
                else -> {
                    // More than 2 lines - use first as main, join rest as translation
                    val main = lines[0].second
                    val trans = lines.drop(1).joinToString("\n") { it.second }
                    mergedLyrics.add(LyricLine(time, main, trans))
                }
            }
        }

        return mergedLyrics.sortedBy { it.time }
    }

    fun parseFile(file: File): List<LyricLine> {
        if (!file.exists()) return emptyList()
        return parse(file.readText())
    }
}


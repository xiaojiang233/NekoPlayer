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
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})[\\.:](\\d{2,3})\\]")
    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]*>")

    fun parse(lrcContent: String?): List<LyricLine> {
        if (lrcContent.isNullOrBlank()) return emptyList()

        val tempLyrics = mutableListOf<Pair<Long, String>>()
        val reader = BufferedReader(StringReader(lrcContent))
        var line: String? = reader.readLine()

        while (line != null) {
            // Strip HTML tags first
            val cleanLine = HTML_TAG_PATTERN.matcher(line).replaceAll("").trim()

            if (cleanLine.isBlank()) {
                line = reader.readLine()
                continue
            }

            // Find all timestamps in this line
            val timestamps = mutableListOf<Long>()
            val matcher = TIME_TAG_PATTERN.matcher(cleanLine)
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
            if (timestamps.isNotEmpty()) {
                val text = if (lastEnd < cleanLine.length) cleanLine.substring(lastEnd).trim() else ""
                // Even if text is empty, we might want to show it (as a pause/interlude)
                // But usually we only care about content
                if (text.isNotEmpty()) {
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

        groupedByTime.keys.sorted().forEach { time ->
            val lines = groupedByTime[time]!!
            // Filter out exact duplicate text for the same timestamp
            val uniqueLines = lines.map { it.second }.distinct()

            when (uniqueLines.size) {
                1 -> {
                    // Single unique line - no translation
                    mergedLyrics.add(LyricLine(time, uniqueLines[0], null))
                }
                else -> {
                    // Multiple unique lines - treat first as main, joined rest as translation
                    val main = uniqueLines[0]
                    val trans = uniqueLines.drop(1).joinToString("\n")
                    mergedLyrics.add(LyricLine(time, main, trans))
                }
            }
        }

        return mergedLyrics
    }

    fun parseFile(file: File): List<LyricLine> {
        if (!file.exists()) return emptyList()
        return parse(file.readText())
    }
}

package top.xiaojiang233.nekoplayer.utils

import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.util.regex.Pattern

data class LyricLine(
    val time: Long,
    val text: String
)

object LyricsParser {
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]")

    fun parse(lrcContent: String): List<LyricLine> {
        val lyrics = mutableListOf<LyricLine>()
        val reader = BufferedReader(StringReader(lrcContent))
        var line: String? = reader.readLine()
        while (line != null) {
            val matcher = TIME_TAG_PATTERN.matcher(line)
            if (matcher.find()) {
                val minutes = matcher.group(1)?.toLong() ?: 0
                val seconds = matcher.group(2)?.toLong() ?: 0
                val millisStr = matcher.group(3) ?: "00"
                val millis = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()

                val time = minutes * 60000 + seconds * 1000 + millis
                val text = line.substring(matcher.end()).trim()

                if (text.isNotEmpty()) {
                    lyrics.add(LyricLine(time, text))
                }
            }
            line = reader.readLine()
        }
        return lyrics.sortedBy { it.time }
    }

    fun parseFile(file: File): List<LyricLine> {
        if (!file.exists()) return emptyList()
        return parse(file.readText())
    }
}


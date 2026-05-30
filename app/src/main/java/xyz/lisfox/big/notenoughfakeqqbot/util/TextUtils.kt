package xyz.lisfox.big.notenoughfakeqqbot.util

import xyz.lisfox.big.notenoughfakeqqbot.App

/**
 * 消息内容段落：支持图文混编及多媒体占位
 */
sealed class ContentSegment {
    data class Text(val text: String) : ContentSegment()
    data class Image(val url: String) : ContentSegment()
    data class Audio(val url: String, val duration: Int? = null) : ContentSegment()
    data class Video(val url: String) : ContentSegment()
    data class File(val url: String, val name: String) : ContentSegment()
}

/**
 * 解码 HTML 实体
 */
private fun decodeEntities(input: String): String {
    return input
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}

/**
 * 处理 <at> 标签，提取 name 属性优先，否则用 id
 */
private fun processAtTags(input: String): String {
    return Regex("""<at\b([^>]*)/?>(?:</at>)?""", RegexOption.IGNORE_CASE)
        .replace(input) { match ->
            val attrs = match.groupValues[1]
            val nameMatch = Regex("""\bname\s*=\s*['"]([^'"]*?)['"]""", RegexOption.IGNORE_CASE).find(attrs)
            val name = nameMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            if (name != null) return@replace "@$name"
            val idMatch = Regex("""\bid\s*=\s*['"]([^'"]*?)['"]""", RegexOption.IGNORE_CASE).find(attrs)
            val id = idMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: ""
            "@$id"
        }
}

/**
 * 处理 <emoji> 标签，提取 name 属性作为文本显示
 */
private fun processEmojiTags(input: String): String {
    return Regex("""<emoji\b([^>]*)/?>(?:</emoji>)?""", RegexOption.IGNORE_CASE)
        .replace(input) { match ->
            val attrs = match.groupValues[1]
            val nameMatch = Regex("""\bname\s*=\s*['"]([^'"]*?)['"]""", RegexOption.IGNORE_CASE).find(attrs)
            val name = nameMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            name ?: "[表情]"
        }
}

/**
 * 从标签属性中提取指定属性值
 */
private fun extractAttr(attrs: String, attrName: String): String? {
    val regex = Regex("""\b$attrName\s*=\s*['"]([^'"]*?)['"]""", RegexOption.IGNORE_CASE)
    return regex.find(attrs)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}

/**
 * 匹配所有需要特殊处理的媒体标签
 * 返回 (标签类型, 匹配结果) 列表，按位置排序
 */
private data class MediaMatch(
    val type: String,       // "img", "audio", "video", "file"
    val range: IntRange,
    val attrs: String,
)

private fun findMediaTags(decoded: String): List<MediaMatch> {
    val results = mutableListOf<MediaMatch>()

    // <img src="...">
    Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE).findAll(decoded).forEach {
        results.add(MediaMatch("img", it.range, it.groupValues[1]))
    }
    // <audio src="...">
    Regex("""<audio\b([^>]*)/?>(?:</audio>)?""", RegexOption.IGNORE_CASE).findAll(decoded).forEach {
        results.add(MediaMatch("audio", it.range, it.groupValues[1]))
    }
    // <video src="...">
    Regex("""<video\b([^>]*)/?>(?:</video>)?""", RegexOption.IGNORE_CASE).findAll(decoded).forEach {
        results.add(MediaMatch("video", it.range, it.groupValues[1]))
    }
    // <file src="..." name="...">
    Regex("""<file\b([^>]*)/?>(?:</file>)?""", RegexOption.IGNORE_CASE).findAll(decoded).forEach {
        results.add(MediaMatch("file", it.range, it.groupValues[1]))
    }

    return results.sortedBy { it.range.first }
}

/**
 * 将消息 content 解析为图文混编的段落列表
 * 支持：文本、图片、表情、语音、视频、文件
 */
fun parseContentSegments(content: String?): List<ContentSegment> {
    if (content.isNullOrBlank()) return emptyList()

    val decoded = decodeEntities(content)
    val segments = mutableListOf<ContentSegment>()
    val mediaMatches = findMediaTags(decoded)

    var lastEnd = 0

    for (media in mediaMatches) {
        // 媒体标签前面的文本部分
        if (media.range.first > lastEnd) {
            val textPart = decoded.substring(lastEnd, media.range.first)
            val processed = processTextPart(textPart)
            if (processed.isNotBlank()) {
                segments.add(ContentSegment.Text(processed))
            }
        }

        when (media.type) {
            "img" -> {
                val src = extractAttr(media.attrs, "src")
                if (src != null) {
                    val cachedUrl = try {
                        App.instance.imageCacheManager.getImagePath(src)
                    } catch (_: Exception) {
                        src
                    }
                    segments.add(ContentSegment.Image(cachedUrl))
                }
            }
            "audio" -> {
                val src = extractAttr(media.attrs, "src") ?: ""
                val duration = extractAttr(media.attrs, "duration")?.toIntOrNull()
                segments.add(ContentSegment.Audio(src, duration))
            }
            "video" -> {
                val src = extractAttr(media.attrs, "src") ?: ""
                segments.add(ContentSegment.Video(src))
            }
            "file" -> {
                val src = extractAttr(media.attrs, "src") ?: ""
                val name = extractAttr(media.attrs, "name") ?: "文件"
                segments.add(ContentSegment.File(src, name))
            }
        }

        lastEnd = media.range.last + 1
    }

    // 最后剩余的文本
    if (lastEnd < decoded.length) {
        val textPart = decoded.substring(lastEnd)
        val processed = processTextPart(textPart)
        if (processed.isNotBlank()) {
            segments.add(ContentSegment.Text(processed))
        }
    }

    // 如果解析后完全为空（不应该发生），返回原始内容的纯文本
    if (segments.isEmpty() && decoded.isNotBlank()) {
        val fallback = processTextPart(decoded)
        if (fallback.isNotBlank()) {
            segments.add(ContentSegment.Text(fallback))
        }
    }

    return segments
}

/**
 * 处理文本部分：解析 @标签、emoji标签，去除其他 HTML 标签
 */
private fun processTextPart(text: String): String {
    var result = processAtTags(text)
    result = processEmojiTags(result)
    // 去除其他未识别的 HTML 标签
    result = Regex("""<[^>]+>""").replace(result, "")
    return result.trim()
}

/**
 * 从消息 content 中提取纯文本（用于会话列表预览等场景）
 */
fun extractPlainText(content: String?): String {
    if (content.isNullOrBlank()) return ""
    var result = decodeEntities(content)
    // 处理 <at> 标签
    result = processAtTags(result)
    // 处理 <emoji> 标签
    result = processEmojiTags(result)
    // 处理 <img> 标签
    result = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE).replace(result, "[图片]")
    // 处理 <audio> 标签
    result = Regex("""<audio\b[^>]*/?>(?:</audio>)?""", RegexOption.IGNORE_CASE).replace(result, "[语音]")
    // 处理 <video> 标签
    result = Regex("""<video\b[^>]*/?>(?:</video>)?""", RegexOption.IGNORE_CASE).replace(result, "[视频]")
    // 处理 <file> 标签
    result = Regex("""<file\b[^>]*/?>(?:</file>)?""", RegexOption.IGNORE_CASE).replace(result, "[文件]")
    // 去除其他 HTML 标签
    result = Regex("""<[^>]+>""").replace(result, "")
    return result.trim()
}

/**
 * 从消息 content 中提取图片 URL 列表
 */
fun extractImageUrls(content: String?): List<String> {
    if (content.isNullOrBlank()) return emptyList()
    val decoded = decodeEntities(content)
    val regex = Regex("""<img\b[^>]*\bsrc\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
    return regex.findAll(decoded).map { it.groupValues[1] }.toList()
}

/**
 * 短 ID 显示
 */
fun shortId(id: String): String {
    if (id.length <= 12) return id
    return "${id.take(6)}...${id.takeLast(4)}"
}

/**
 * 格式化时间戳为显示文本
 */
fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = java.util.Date(timestamp)
    val cal = java.util.Calendar.getInstance().apply { time = date }
    val nowCal = java.util.Calendar.getInstance()

    val sameDay = cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            cal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)

    val timeStr = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))

    return when {
        sameDay -> timeStr
        (System.currentTimeMillis() - timestamp) < 2 * 24 * 3600 * 1000L -> "昨天 $timeStr"
        (System.currentTimeMillis() - timestamp) < 7 * 24 * 3600 * 1000L -> {
            val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            "周${weekdays[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]} $timeStr"
        }
        else -> String.format("%d/%d/%d", cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.YEAR))
    }
}

/**
 * 格式化会话列表的时间（更简短）
 */
fun formatConversationTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = java.util.Date(timestamp)
    val cal = java.util.Calendar.getInstance().apply { time = date }
    val nowCal = java.util.Calendar.getInstance()

    val sameDay = cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            cal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)

    val timeStr = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))

    return when {
        sameDay -> timeStr
        (System.currentTimeMillis() - timestamp) < 2 * 24 * 3600 * 1000L -> "昨天"
        (System.currentTimeMillis() - timestamp) < 7 * 24 * 3600 * 1000L -> {
            val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            "周${weekdays[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]}"
        }
        else -> String.format("%d/%d", cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }
}

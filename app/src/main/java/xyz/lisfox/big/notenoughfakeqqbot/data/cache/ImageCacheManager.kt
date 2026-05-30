package xyz.lisfox.big.notenoughfakeqqbot.data.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 图片本地缓存管理器
 * 将网络图片下载到 app 内部存储，避免腾讯 URL 过期（通常4小时）后无法加载
 */
class ImageCacheManager(private val context: Context) {
    companion object {
        private const val TAG = "ImageCache"
        private const val CACHE_DIR = "image_cache"
        private const val MAX_CONCURRENT = 4
    }

    private val cacheDir: File by lazy {
        File(context.filesDir, CACHE_DIR).also { it.mkdirs() }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT)

    /**
     * 根据 URL 获取本地缓存文件路径，如果已缓存则返回 file:// URI，否则返回 null
     */
    fun getCachedPath(url: String): String? {
        if (url.isBlank() || url.startsWith("file://")) return url
        // 不缓存 data URL
        if (url.startsWith("data:")) return null
        val file = getCacheFile(url)
        return if (file.exists() && file.length() > 0) {
            file.toURI().toString()
        } else {
            null
        }
    }

    /**
     * 获取图片的本地路径：已缓存返回本地路径，未缓存返回原始 URL
     */
    fun getImagePath(url: String): String {
        return getCachedPath(url) ?: url
    }

    /**
     * 异步缓存单张图片（如果尚未缓存）
     */
    fun cacheAsync(url: String) {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("file://")) return
        val file = getCacheFile(url)
        if (file.exists() && file.length() > 0) return

        scope.launch {
            semaphore.acquire()
            try {
                downloadToFile(url, file)
            } catch (e: Exception) {
                Log.w(TAG, "Cache failed for ${url.take(80)}: ${e.message}")
            } finally {
                semaphore.release()
            }
        }
    }

    /**
     * 批量缓存图片 URL
     */
    fun cacheAll(urls: List<String>) {
        urls.forEach { cacheAsync(it) }
    }

    /**
     * 同步下载图片（用于需要等待结果的场景）
     */
    suspend fun cacheSync(url: String): String? {
        if (url.isBlank() || url.startsWith("data:") || url.startsWith("file://")) return url
        val file = getCacheFile(url)
        if (file.exists() && file.length() > 0) return file.toURI().toString()

        return withContext(Dispatchers.IO) {
            try {
                downloadToFile(url, file)
                file.toURI().toString()
            } catch (e: Exception) {
                Log.w(TAG, "Sync cache failed: ${e.message}")
                null
            }
        }
    }

    /**
     * 清除所有缓存
     */
    fun clearCache() {
        scope.launch {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "Cache cleared")
        }
    }

    /**
     * 获取缓存大小（字节）
     */
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0
    }

    private fun getCacheFile(url: String): File {
        val hash = md5(url)
        val ext = guessExtension(url)
        return File(cacheDir, "$hash.$ext")
    }

    private fun downloadToFile(url: String, file: File) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP ${response.code}")
        }
        val body = response.body ?: throw Exception("Empty body")
        val tempFile = File(file.parent, "${file.name}.tmp")
        try {
            tempFile.outputStream().use { out ->
                body.byteStream().use { input ->
                    input.copyTo(out, bufferSize = 8192)
                }
            }
            tempFile.renameTo(file)
        } finally {
            if (tempFile.exists()) tempFile.delete()
            response.close()
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun guessExtension(url: String): String {
        val path = url.substringBefore("?").substringBefore("#")
        return when {
            path.endsWith(".png", ignoreCase = true) -> "png"
            path.endsWith(".gif", ignoreCase = true) -> "gif"
            path.endsWith(".webp", ignoreCase = true) -> "webp"
            path.endsWith(".bmp", ignoreCase = true) -> "bmp"
            else -> "jpg"
        }
    }
}

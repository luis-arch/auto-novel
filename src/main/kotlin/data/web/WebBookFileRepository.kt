package data.web

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import kotlin.io.path.*

@Serializable
enum class BookFileLang(val value: String) {
    @SerialName("jp")
    JP("jp"),

    @SerialName("zh-baidu")
    ZH_BAIDU("zh-baidu"),

    @SerialName("mix-baidu")
    MIX_BAIDU("mix-baidu"),

    @SerialName("zh-youdao")
    ZH_YOUDAO("zh-youdao"),

    @SerialName("mix-youdao")
    MIX_YOUDAO("mix-youdao")
}

@Serializable
enum class BookFileType(val value: String) {
    @SerialName("epub")
    EPUB("epub"),

    @SerialName("txt")
    TXT("txt")
}

class WebBookFileRepository(
    private val root: Path,
) {
    private fun buildFilePath(fileName: String) =
        root / fileName

    suspend fun makeFile(
        fileName: String,
        lang: BookFileLang,
        type: BookFileType,
        metadata: BookMetadata,
        episodes: Map<String, BookEpisode>,
    ) {
        val filePath = buildFilePath(fileName)
        when (type) {
            BookFileType.EPUB -> makeEpubFile(filePath, lang, metadata, episodes)
            BookFileType.TXT -> makeTxtFile(filePath, lang, metadata, episodes)
        }
    }

    fun getCreationTime(fileName: String): Instant? {
        val filePath = buildFilePath(fileName)
        return if (filePath.exists()) {
            filePath.readAttributes<BasicFileAttributes>()
                .creationTime()
                .toInstant()
        } else null
    }
}
package infra

import appModule
import infra.model.NovelFileLang
import infra.web.*
import infra.wenku.WenkuNovelFileRepository
import infra.wenku.WenkuNovelMetadataRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import java.io.File

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(KoinExtension(module = appModule, mode = KoinLifecycleMode.Root))

    private val es by inject<ElasticSearchDataSource>(ElasticSearchDataSource::class.java)
    private val mongo by inject<MongoDataSource>(MongoDataSource::class.java)

    private val repoE by inject<WebNovelChapterRepository>(WebNovelChapterRepository::class.java)
    private val repoTMH by inject<WebNovelTocMergeHistoryRepository>(WebNovelTocMergeHistoryRepository::class.java)

    private val repoWBM by inject<WenkuNovelMetadataRepository>(WenkuNovelMetadataRepository::class.java)

    init {
        describe("test") {
        }

        describe("kmongo issue 415") {
//            println(setValue(BookEpisode::youdaoParagraphs.pos(0), "test").toBsonDocument())
//            println(setValue(BookEpisode::baiduParagraphs.pos(0), "test").toBsonDocument())
//            println(Updates.set("paragraphsZh.0", "test").toBsonDocument())
        }

        describe("script") {
            it("sitemap") {
                val col = mongo.webNovelMetadataCollection
                val list = col.find().toList()
                File("sitemap.txt").printWriter().use { out ->
                    list.forEach {
                        out.println("https://books.fishhawk.top/novel/${it.providerId}/${it.novelId}")
                    }
                }
            }
        }
    }
}
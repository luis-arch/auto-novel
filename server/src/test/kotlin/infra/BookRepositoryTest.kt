package infra

import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.ktsearch.index
import infra.common.SakuraJobRepository
import infra.model.*
import infra.user.UserRepository
import infra.web.DataSourceWebNovelProvider
import infra.wenku.WenkuNovelMetadataRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.koin.KoinExtension
import io.kotest.koin.KoinLifecycleMode
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.Document
import org.bson.types.ObjectId
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject
import org.koin.test.KoinTest
import org.koin.test.inject
import org.litote.kmongo.coroutine.projection
import org.litote.kmongo.eq
import org.litote.kmongo.id.toId
import java.io.File


val appModule = module {
    // Data Source
    single {
        val mongodbUrl = System.getenv("MONGODB_URL") ?: "mongodb://192.168.9.202:27017"
        DataSourceMongo(mongodbUrl)
    }
    single {
        val url = System.getenv("ELASTIC_SEARCH_DB_URL") ?: "192.168.9.202"
        DataSourceElasticSearch(url)
    }
    single {
        val url = System.getenv("REDIS_URL") ?: "192.168.9.202:6379"
        createRedisDataSource(url)
    }
    single {
        val httpProxy: String? = System.getenv("HTTPS_PROXY")
        val pixivPhpsessid: String? = System.getenv("PIXIV_COOKIE_PHPSESSID")
        DataSourceWebNovelProvider(
            httpsProxy = httpProxy,
            pixivPhpsessid = pixivPhpsessid,
        )
    }

    singleOf(::UserRepository)
    singleOf(::SakuraJobRepository)
    singleOf(::WenkuNovelMetadataRepository)
}

class BookRepositoryTest : DescribeSpec(), KoinTest {
    override fun extensions() = listOf(
        KoinExtension(
            module = appModule,
            mode = KoinLifecycleMode.Root,
        )
    )

    private val provider by inject<DataSourceWebNovelProvider>(DataSourceWebNovelProvider::class.java)
    private val es by inject<DataSourceElasticSearch>(DataSourceElasticSearch::class.java)
    private val mongo by inject<DataSourceMongo>(DataSourceMongo::class.java)

    private val sjRepo by inject<SakuraJobRepository>()
    private val userRepo by inject<UserRepository>()
    private val wenkuRepo by inject<WenkuNovelMetadataRepository>()

    init {
        describe("test") {
            suspend fun test(id: String) {
                mongo
                    .userFavoredWenkuCollection
                    .find(UserFavoredWenkuNovelModel::novelId eq ObjectId(id).toId())
                    .toList()
                    .apply { println(size) }
                    .forEach {
                        println("${it.userId}")
                    }
                wenkuRepo.delete(id)
            }
            test("65a2701ef9f4eb64961d7881")
        }
        describe("build es index") {
            @Serializable
            data class WNMP(
                val providerId: String,
                val bookId: String,
                val titleJp: String,
                val titleZh: String? = null,
                val authors: List<WebNovelAuthor>,
                val type: WebNovelType = WebNovelType.连载中,
                val attentions: List<WebNovelAttention> = emptyList(),
                val keywords: List<String> = emptyList(),
                val visited: Long = 0,
                val gpt: Long = 0,
                val sakura: Long = 0,
                val toc: List<WebNovelTocItem>,
                @Contextual val updateAt: Instant,
            )

            suspend fun buildIndex(skip: Int, limit: Int): Int {
                val list = mongo
                    .webNovelMetadataCollection
                    .withDocumentClass<WNMP>()
                    .find()
                    .projection(
                        WebNovelMetadata::providerId,
                        WebNovelMetadata::novelId,
                        WebNovelMetadata::titleJp,
                        WebNovelMetadata::titleZh,
                        WebNovelMetadata::authors,
                        WebNovelMetadata::type,
                        WebNovelMetadata::attentions,
                        WebNovelMetadata::keywords,
                        WebNovelMetadata::gpt,
                        WebNovelMetadata::sakura,
                        WebNovelMetadata::visited,
                        WebNovelMetadata::toc,
                        WebNovelMetadata::updateAt,
                    )
                    .skip(skip)
                    .limit(limit)
                    .toList()
                es.client.bulk(target = DataSourceElasticSearch.webNovelIndexName) {
                    list.forEach { it ->
                        index(
                            doc = WebNovelMetadataEsModel(
                                providerId = it.providerId,
                                novelId = it.bookId,
                                authors = it.authors.map { it.name },
                                titleJp = it.titleJp,
                                titleZh = it.titleZh,
                                type = it.type,
                                attentions = it.attentions,
                                keywords = it.keywords,
                                hasGpt = it.gpt > 0,
                                hasSakura = it.sakura > 0,
                                visited = it.visited.toInt(),
                                tocSize = it.toc.count { it.chapterId != null },
                                updateAt = it.updateAt.epochSeconds,
                            ),
                            id = "${it.providerId}.${it.bookId}",
                        )
                    }
                }
                return list.size
            }

            var batch = 0
            val batchSize = 500
            while (true) {
                println("batch${batch} start")
                val actualBatchSize = buildIndex(
                    batch * batchSize,
                    batchSize + 50,
                )
                println("batch${batch} finish ${actualBatchSize}")

                if (actualBatchSize == batchSize + 50) {
                    batch += 1
                } else {
                    break
                }
            }
        }

        describe("build wenku es index") {
            @Serializable
            data class WNMP(
                @Contextual @SerialName("_id") val id: ObjectId,
                val title: String,
                val titleZh: String,
                val cover: String,
                val authors: List<String>,
                val artists: List<String>,
                val keywords: List<String>,
                val r18: Boolean = false,
                @Contextual val updateAt: Instant,
            )

            suspend fun buildIndex(skip: Int, limit: Int): Int {
                val list = mongo
                    .wenkuNovelMetadataCollection
                    .withDocumentClass<WNMP>()
                    .find()
                    .projection(
                        WenkuNovelMetadata::id,
                        WenkuNovelMetadata::title,
                        WenkuNovelMetadata::titleZh,
                        WenkuNovelMetadata::cover,
                        WenkuNovelMetadata::authors,
                        WenkuNovelMetadata::artists,
                        WenkuNovelMetadata::keywords,
                        WenkuNovelMetadata::r18,
                        WenkuNovelMetadata::updateAt,
                    )
                    .skip(skip)
                    .limit(limit)
                    .toList()
                es.client.bulk(target = DataSourceElasticSearch.wenkuNovelIndexName) {
                    list.forEach {
                        index(
                            doc = WenkuNovelMetadataEsModel(
                                id = it.id.toHexString(),
                                title = it.title,
                                titleZh = it.titleZh,
                                cover = it.cover,
                                authors = it.authors,
                                artists = it.artists,
                                keywords = it.keywords,
                                r18 = it.r18,
                                updateAt = it.updateAt.epochSeconds,
                            ),
                            id = it.id.toHexString(),
                        )
                    }
                }
                return list.size
            }

            var batch = 0
            val batchSize = 500
            while (true) {
                println("batch${batch} start")
                val actualBatchSize = buildIndex(
                    batch * batchSize,
                    batchSize + 50,
                )
                println("batch${batch} finish ${actualBatchSize}")

                if (actualBatchSize == batchSize + 50) {
                    batch += 1
                } else {
                    break
                }
            }
        }


        describe("kmongo issue 415") {
//            println(setValue(BookEpisode::youdaoParagraphs.pos(0), "test").toBsonDocument())
//            println(setValue(BookEpisode::baiduParagraphs.pos(0), "test").toBsonDocument())
//            println(Updates.set("paragraphsZh.0", "test").toBsonDocument())
        }

        describe("generate sitemap") {
            File("sitemap.txt").printWriter().use { out ->
                mongo
                    .webNovelMetadataCollection
                    .withDocumentClass<Document>()
                    .find()
                    .projection(
                        WebNovelMetadata::providerId,
                        WebNovelMetadata::novelId,
                    )
                    .toList()
                    .forEach {
                        val pid = it.getString("providerId")
                        val nid = it.getString("bookId")
                        out.println("https://books.fishhawk.top/novel/${pid}/${nid}")
                    }

                mongo
                    .wenkuNovelMetadataCollection
                    .projection(WenkuNovelMetadata::id)
                    .toList()
                    .forEach {
                        val nid = it.toHexString()
                        out.println("https://books.fishhawk.top/wenku/${nid}")
                    }
            }
        }
    }
}

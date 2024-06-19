package api

import api.plugins.*
import infra.article.ArticleRepository
import infra.comment.CommentRepository
import infra.comment.Comment
import infra.common.Page
import infra.user.UserOutline
import infra.user.UserRole
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Resource("/comment")
private class CommentRes {
    @Resource("")
    class List(
        val parent: CommentRes,
        val site: String,
        val parentId: String? = null,
        val page: Int,
        val pageSize: Int,
    )

    @Resource("/{id}")
    class Id(val parent: CommentRes, val id: String) {
        @Resource("/hidden")
        class Hidden(val parent: Id)
    }
}

fun Route.routeComment() {
    val service by inject<CommentApi>()

    authenticateDb(optional = true) {
        get<CommentRes.List> { loc ->
            call.tryRespond {
                val user = call.authenticatedUserOrNull()
                service.listComment(
                    user = user,
                    postId = loc.site,
                    parentId = loc.parentId,
                    page = loc.page,
                    pageSize = loc.pageSize,
                    replyPageSize = 10,
                    reverse = loc.parentId == null,
                )
            }
        }
    }

    authenticateDb {
        rateLimit(RateLimitNames.CreateComment) {
            post<CommentRes> {
                @Serializable
                class Body(
                    val site: String,
                    val parent: String? = null,
                    val content: String,
                )

                val user = call.authenticatedUser()
                val body = call.receive<Body>()
                call.tryRespond {
                    service.createComment(
                        user = user,
                        site = body.site,
                        parent = body.parent,
                        content = body.content,
                    )
                }
            }
        }

        put<CommentRes.Id.Hidden> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateCommentHidden(user = user, id = loc.parent.id, hidden = true)
            }
        }
        delete<CommentRes.Id.Hidden> { loc ->
            val user = call.authenticatedUser()
            call.tryRespond {
                service.updateCommentHidden(user = user, id = loc.parent.id, hidden = false)
            }
        }
    }
}

class CommentApi(
    private val commentRepo: CommentRepository,
    private val articleRepo: ArticleRepository,
) {
    @Serializable
    data class CommentDto(
        val id: String,
        val user: UserOutline,
        val content: String,
        val hidden: Boolean,
        val createAt: Long,
        val numReplies: Int,
        val replies: List<CommentDto>,
    )

    private fun Comment.asDto(
        replies: List<CommentDto>,
        ignoreHidden: Boolean,
    ) =
        CommentDto(
            id = id,
            user = user,
            content = if (ignoreHidden || !hidden) content else "",
            hidden = hidden,
            createAt = createAt.epochSeconds,
            numReplies = numReplies,
            replies = replies,
        )

    suspend fun listComment(
        user: AuthenticatedUser?,
        postId: String,
        parentId: String?,
        page: Int,
        pageSize: Int,
        reverse: Boolean,
        replyPageSize: Int,
    ): Page<CommentDto> {
        validatePageNumber(page)
        validatePageSize(pageSize)
        validatePageSize(replyPageSize, max = 20)

        val ignoreHidden = user != null && user.role atLeast UserRole.Maintainer

        return commentRepo
            .listComment(
                site = postId,
                parent = parentId,
                page = page,
                pageSize = pageSize,
                reverse = reverse,
            )
            .map {
                val replies = if (parentId == null && it.numReplies > 0) {
                    commentRepo.listComment(
                        site = postId,
                        parent = it.id,
                        page = 0,
                        pageSize = replyPageSize,
                    ).items.map {
                        it.asDto(emptyList(), ignoreHidden)
                    }
                } else {
                    emptyList()
                }
                it.asDto(replies, ignoreHidden)
            }
    }

    @Suppress("unused")
    suspend fun deleteComment(
        user: AuthenticatedUser,
        id: String,
    ) {
        user.shouldBeAtLeast(UserRole.Admin)
        val isDeleted = commentRepo.deleteComment(id)
        if (!isDeleted) throwNotFound("评论不存在")
    }

    suspend fun createComment(
        user: AuthenticatedUser,
        site: String,
        parent: String?,
        content: String,
    ) {
        if (content.isBlank()) {
            throwBadRequest("回复内容不能为空")
        }
        if (
            parent != null &&
            !commentRepo.increaseNumReplies(parent)
        ) {
            throwNotFound("回复的评论不存在")
        }

        if (site.startsWith("article-")) {
            articleRepo.increaseNumComments(
                site.removePrefix("article-")
            )
        }
        commentRepo.createComment(
            site = site,
            parent = parent,
            user = user.id,
            content = content,
        )
    }

    private fun throwCommentNotFound(): Nothing =
        throwNotFound("评论不存在")

    suspend fun updateCommentHidden(
        user: AuthenticatedUser,
        id: String,
        hidden: Boolean,
    ) {
        if (!(user.role atLeast UserRole.Admin) && !commentRepo.isCommentCreateBy(id = id, userId = user.id)) {
            throwUnauthorized("只有评论作者才有权限隐藏")
        }
        val isUpdated = commentRepo.updateCommentHidden(
            id = id,
            hidden = hidden,
        )
        if (!isUpdated) throwCommentNotFound()
    }
}

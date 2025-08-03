package infra.user

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
enum class UserRole {
    @SerialName("admin")
    Admin,

    @SerialName("maintainer")
    Maintainer,

    @SerialName("trusted")
    Trusted,

    @SerialName("normal")
    Normal,

    @SerialName("member")
    Member,

    @SerialName("restricted")
    Restricted,

    @SerialName("banned")
    Banned;

    private fun authLevel() = when (this) {
        Admin, Maintainer -> 3
        Trusted, Normal, Member -> 2
        Restricted -> 1
        Banned -> 0
    }

    infix fun atLeast(other: UserRole): Boolean =
        authLevel() >= other.authLevel()
}

@Serializable
data class UserOutline(
    val username: String,
    val role: UserRole,
)

@Serializable
data class UserFavored(
    val id: String,
    val title: String,
)

@Serializable
data class UserFavoredList(
    val favoredWeb: List<UserFavored>,
    val favoredWenku: List<UserFavored>,
)

@Serializable
data class User(
    val id: String,
    val email: String,
    val username: String,
    val role: UserRole,
    @Contextual val createdAt: Instant,
)

// MongoDB
@Serializable
data class UserDbModel(
    @Contextual @SerialName("_id") val id: ObjectId,
    val email: String,
    val username: String,
    val salt: String,
    val password: String,
    val role: UserRole,
    @Contextual val createdAt: Instant,
    //
    val favoredWeb: List<UserFavored>,
    val favoredWenku: List<UserFavored>,
    //
    val readHistoryPaused: Boolean = false,
)

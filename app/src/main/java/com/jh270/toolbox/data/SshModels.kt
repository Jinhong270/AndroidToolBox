package com.jh270.toolbox.data

enum class AuthType {
    PASSWORD,
    PRIVATE_KEY
}

enum class RemotePlatform {
    UNIX,
    WINDOWS,
    UNKNOWN
}

enum class FileType {
    DIRECTORY,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    ARCHIVE,
    EXECUTABLE,
    BINARY,
    UNKNOWN
}

data class SshConfig(
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val privateKey: String = "",
    val passphrase: String = "",
)

data class SshProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val config: SshConfig,
)

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val owner: String = "root",
    val group: String = "root",
    val modifiedTime: Long,
    val fileType: FileType = FileType.UNKNOWN,
)

data class ArchiveEntryItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

data class ChecksumResult(
    val fileName: String,
    val filePath: String,
    val md5: String,
    val sha256: String,
)

data class FilePreview(
    val name: String,
    val path: String,
    val size: Long,
    val permissions: String,
    val modifiedTime: Long,
    val content: String? = null,
    val imageData: ByteArray? = null,
    val fileType: FileType,
    val errorMessage: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FilePreview

        if (name != other.name) return false
        if (path != other.path) return false
        if (size != other.size) return false
        if (permissions != other.permissions) return false
        if (modifiedTime != other.modifiedTime) return false
        if (content != other.content) return false
        if (imageData != null) {
            if (other.imageData == null) return false
            if (!imageData.contentEquals(other.imageData)) return false
        } else if (other.imageData != null) return false
        if (fileType != other.fileType) return false

        return errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = (31 * result) + path.hashCode()
        result = (31 * result) + size.hashCode()
        result = (31 * result) + permissions.hashCode()
        result = (31 * result) + modifiedTime.hashCode()
        result = (31 * result) + (content?.hashCode() ?: 0)
        result = (31 * result) + (imageData?.contentHashCode() ?: 0)
        result = (31 * result) + fileType.hashCode()
        result = (31 * result) + errorMessage.hashCode()
        return result
    }
}

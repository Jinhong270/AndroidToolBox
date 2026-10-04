package com.jh270.toolbox.data

enum class AuthType {
    PASSWORD,
    PRIVATE_KEY
}

data class SshConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val privateKey: String = ""
)

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val permissions: String,
    val modifiedTime: Long
)

data class FilePreview(
    val name: String,
    val path: String,
    val size: Long,
    val permissions: String,
    val modifiedTime: Long,
    val content: String?,
    val isText: Boolean,
    val isBinary: Boolean,
    val errorMessage: String? = null
)

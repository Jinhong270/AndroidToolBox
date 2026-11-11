package com.jh270.toolbox.data

import kotlin.io.encoding.Base64

enum class HiddenFileKind {
    NONE,
    SYSTEM,
    USER
}

object FileVisibility {
    private val systemNames = setOf(".DS_Store", ".Spotlight-V100", ".Trashes", ".fseventsd", ".TemporaryItems", ".DocumentRevisions-V100", ".Trash")

    fun classify(name: String, platform: RemotePlatform = RemotePlatform.UNIX, windowsAttributes: Int? = null): HiddenFileKind {
        if (name == "." || name == "..") return HiddenFileKind.NONE
        if (platform == RemotePlatform.WINDOWS && windowsAttributes != null) {
            return when {
                windowsAttributes and 4 != 0 -> HiddenFileKind.SYSTEM
                windowsAttributes and 2 != 0 -> HiddenFileKind.USER
                else -> HiddenFileKind.NONE
            }
        }
        if (name in systemNames || name.matches(Regex("\\.Trash-\\d+"))) return HiddenFileKind.SYSTEM
        return if (name.startsWith('.')) HiddenFileKind.USER else HiddenFileKind.NONE
    }

    fun isVisible(file: RemoteFile, hideSystemFiles: Boolean, hideUserFiles: Boolean): Boolean {
        if (file.name == "..") return true
        return when (file.hiddenKind) {
            HiddenFileKind.SYSTEM -> !hideSystemFiles
            HiddenFileKind.USER -> !hideUserFiles
            HiddenFileKind.NONE -> true
        }
    }

    fun parseWindowsAttributes(output: String): Map<String, Int> = buildMap {
        output.lineSequence().forEach { line ->
            val fields = line.trim().split('\t')
            if (fields.size == 2) {
                val attributes = fields[1].toIntOrNull()
                val name = runCatching { Base64.decode(fields[0]).decodeToString(throwOnInvalidSequence = true) }.getOrNull()
                if (attributes != null && name != null && name.isNotEmpty()) put(name, attributes)
            }
        }
    }

    fun windowsAttributesCommand(path: String): String {
        val literalPath = "'" + RemotePath.toWindowsPath(path).replace("'", "''") + "'"
        val script = "Get-ChildItem -LiteralPath $literalPath -Force -ErrorAction Stop | ForEach-Object { " +
            "[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(\$_.Name)) + [char]9 + [int]\$_.Attributes }"
        return "powershell.exe -NoProfile -NonInteractive -EncodedCommand " + Base64.encode(script.toByteArray(Charsets.UTF_16LE))
    }
}

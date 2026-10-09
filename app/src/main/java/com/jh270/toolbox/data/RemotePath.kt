package com.jh270.toolbox.data

object RemotePath {

    private val WINDOWS_DRIVE_ROOT = Regex("^/[a-zA-Z]:$")
    private val DRIVE_LETTER = Regex("^[a-zA-Z]:")
    private val WINDOWS_SFTP = Regex("^/[a-zA-Z]:($|/)")

    fun normalize(path: String): String {
        var p = path.trim().replace('\\', '/')
        if (DRIVE_LETTER.containsMatchIn(p) && !p.startsWith("/")) {
            p = "/$p"
        }
        p = p.replace(Regex("/{2,}"), "/")
        if (p.length > 1 && p.endsWith("/")) {
            p = p.trimEnd('/')
        }
        if (p.isEmpty()) return "/"
        return p
    }

    fun join(parent: String, name: String): String {
        val base = normalize(parent).trimEnd('/')
        return if (base.isEmpty()) "/$name" else "$base/$name"
    }

    fun parent(path: String): String {
        val p = normalize(path).trimEnd('/')
        if (p.isEmpty() || p == "/") return "/"
        if (WINDOWS_DRIVE_ROOT.matches(p)) return "/"
        val idx = p.lastIndexOf('/')
        if (idx <= 0) return "/"
        return p.substring(0, idx)
    }

    fun isRoot(path: String): Boolean {
        val p = normalize(path)
        return p == "/" || WINDOWS_DRIVE_ROOT.matches(p)
    }

    fun looksLikeWindows(path: String): Boolean {
        return WINDOWS_SFTP.containsMatchIn(normalize(path))
    }

    fun toWindowsPath(path: String): String {
        val p = normalize(path)
        if (!looksLikeWindows(p)) return p
        val body = if (p.startsWith("/") && p.length >= 3 && p[2] == ':') p.substring(1) else p
        val win = body.replace('/', '\\')
        return if (Regex("^[A-Za-z]:$").matches(win)) "$win\\" else win
    }

    fun toDisplayPath(path: String, platform: RemotePlatform): String {
        if (platform != RemotePlatform.WINDOWS) return normalize(path)
        val p = normalize(path)
        if (p == "/") return "\\"
        if (looksLikeWindows(p)) return toWindowsPath(p)
        return p
    }

    fun fromDisplayPath(input: String, platform: RemotePlatform): String {
        if (platform != RemotePlatform.WINDOWS) return normalize(input)
        val raw = input.trim()
        if (raw == "\\" || raw == "/") return "/"
        return normalize(raw)
    }

    fun safeRelative(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter { it != "." && it.isNotEmpty() }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    fun resolveChild(parent: String, relative: String): String {
        val safe = safeRelative(relative) ?: return ""
        var current = normalize(parent)
        for (part in safe.split('/')) {
            current = join(current, part)
        }
        return current
    }
}

object RemotePlatformDetector {
    fun fromSignals(banner: String, homePath: String, probeOutput: String): RemotePlatform {
        val bannerText = banner.lowercase()
        val probe = probeOutput.lowercase()
        if (bannerText.contains("windows") || RemotePath.looksLikeWindows(homePath)) {
            return RemotePlatform.WINDOWS
        }
        if (probe.contains("windows_nt") || probe.contains("microsoft windows") || probe.contains("platform=windows")) {
            return RemotePlatform.WINDOWS
        }
        if (probe.contains("mingw") || probe.contains("msys") || probe.contains("cygwin")) {
            return RemotePlatform.WINDOWS
        }
        if (
            probe.contains("linux") ||
            probe.contains("darwin") ||
            probe.contains("bsd") ||
            probe.contains("sunos") ||
            probe.contains("aix") ||
            probe.contains("platform=unix")
        ) {
            return RemotePlatform.UNIX
        }
        return RemotePlatform.UNKNOWN
    }
}

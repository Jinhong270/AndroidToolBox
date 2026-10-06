package com.jh270.toolbox.data

object RemotePath {

    private val WINDOWS_DRIVE_ROOT = Regex("^/[a-zA-Z]:$")
    private val DRIVE_LETTER = Regex("^[a-zA-Z]:")

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

    fun toWindowsPath(path: String): String {
        val p = normalize(path)
        return if (Regex("^/[a-zA-Z]:/").containsMatchIn(p)) p.substring(1) else p
    }
}

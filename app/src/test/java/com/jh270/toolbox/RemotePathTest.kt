package com.jh270.toolbox

import com.jh270.toolbox.data.RemotePath
import com.jh270.toolbox.data.RemotePlatform
import com.jh270.toolbox.data.RemotePlatformDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePathTest {

    @Test
    fun normalizeConvertsBackslashes() {
        assertEquals("/C:/Users/x", RemotePath.normalize("C:\\Users\\x"))
        assertEquals("/home/user", RemotePath.normalize("/home/user"))
    }

    @Test
    fun normalizePrependsSlashToDriveLetter() {
        assertEquals("/C:/Users/x", RemotePath.normalize("C:/Users/x"))
        assertEquals("/D:", RemotePath.normalize("D:"))
    }

    @Test
    fun normalizeCollapsesSlashAndKeepsUnixPath() {
        assertEquals("/home/user", RemotePath.normalize("//home///user//"))
        assertEquals("/", RemotePath.normalize("   "))
    }

    @Test
    fun joinHandlesRootAndDriveRoot() {
        assertEquals("/name", RemotePath.join("/", "name"))
        assertEquals("/C:/Users/name", RemotePath.join("/C:/Users", "name"))
        assertEquals("/C:/name", RemotePath.join("/C:", "name"))
    }

    @Test
    fun parentForLinuxPaths() {
        assertEquals("/home", RemotePath.parent("/home/user"))
        assertEquals("/", RemotePath.parent("/home"))
        assertEquals("/", RemotePath.parent("/"))
    }

    @Test
    fun parentForWindowsDrivePaths() {
        assertEquals("/C:/Users", RemotePath.parent("/C:/Users/name"))
        assertEquals("/C:", RemotePath.parent("/C:/Users"))
        assertEquals("/", RemotePath.parent("/C:"))
    }

    @Test
    fun isRootDetection() {
        assertTrue(RemotePath.isRoot("/"))
        assertTrue(RemotePath.isRoot("/C:"))
        assertFalse(RemotePath.isRoot("/C:/Users"))
        assertFalse(RemotePath.isRoot("/home"))
    }

    @Test
    fun toWindowsPathUsesBackslashes() {
        assertEquals("C:\\Users\\x", RemotePath.toWindowsPath("/C:/Users/x"))
        assertEquals("C:\\", RemotePath.toWindowsPath("/C:"))
        assertEquals("/home/user", RemotePath.toWindowsPath("/home/user"))
    }

    @Test
    fun displayRoundTripKeepsWindowsSeparators() {
        assertEquals("C:\\Users\\x", RemotePath.toDisplayPath("/C:/Users/x", RemotePlatform.WINDOWS))
        assertEquals("\\", RemotePath.toDisplayPath("/", RemotePlatform.WINDOWS))
        assertEquals("/home/user", RemotePath.toDisplayPath("/home/user", RemotePlatform.UNIX))
        assertEquals("/C:/Users/x", RemotePath.fromDisplayPath("C:\\Users\\x", RemotePlatform.WINDOWS))
        assertEquals("/C:/Users/x", RemotePath.fromDisplayPath("C:/Users/x", RemotePlatform.WINDOWS))
        assertEquals("/", RemotePath.fromDisplayPath("\\", RemotePlatform.WINDOWS))
    }

    @Test
    fun safeRelativeRejectsTraversal() {
        assertEquals("a/b", RemotePath.safeRelative("a\\b"))
        assertNull(RemotePath.safeRelative("../etc/passwd"))
        assertNull(RemotePath.safeRelative("a/../../b"))
        assertEquals("/C:/Users/a/b", RemotePath.resolveChild("/C:/Users", "a/b"))
        assertEquals("", RemotePath.resolveChild("/tmp", "../secret"))
    }

    @Test
    fun platformSignalsPreferWindowsBannerAndDriveHome() {
        assertEquals(
            RemotePlatform.WINDOWS,
            RemotePlatformDetector.fromSignals("SSH-2.0-OpenSSH_for_Windows_9.5", "/home/user", "")
        )
        assertEquals(
            RemotePlatform.WINDOWS,
            RemotePlatformDetector.fromSignals("SSH-2.0-OpenSSH_9.6", "/C:/Users/x", "")
        )
        assertEquals(
            RemotePlatform.UNIX,
            RemotePlatformDetector.fromSignals("SSH-2.0-OpenSSH_9.6p1 Ubuntu", "/root", "Linux")
        )
        assertEquals(
            RemotePlatform.WINDOWS,
            RemotePlatformDetector.fromSignals("SSH-2.0-OpenSSH_9.5", "/", "Microsoft Windows [Version 10.0.22631]")
        )
        assertEquals(
            RemotePlatform.UNKNOWN,
            RemotePlatformDetector.fromSignals("SSH-2.0-OpenSSH_9.6", "/root", "uname: not found")
        )
    }
}

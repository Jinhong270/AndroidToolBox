package com.jh270.toolbox

import com.jh270.toolbox.data.RemotePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun toWindowsPathStripsLeadingSlash() {
        assertEquals("C:/Users/x", RemotePath.toWindowsPath("/C:/Users/x"))
        assertEquals("/home/user", RemotePath.toWindowsPath("/home/user"))
    }
}

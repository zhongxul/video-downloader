package com.example.videodownloader.core.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileUriResolverTest {
    @Test
    fun resolveFilePathFromUriString_keepsHashInFileName() {
        val resolved = resolveFilePathFromUriString(
            "file:///storage/emulated/0/DCIM/VideoDownloader/%E6%A8%8A%E6%8C%AF%E4%B8%9C%20%E6%9C%80%E7%88%B1%E7%9C%8B%E7%9A%84%E7%8E%AF%E8%8A%82%23%E6%A8%8A%E6%8C%AF%E4%B8%9C.mp4",
        )

        assertEquals(
            "/storage/emulated/0/DCIM/VideoDownloader/樊振东 最爱看的环节#樊振东.mp4",
            resolved,
        )
    }

    @Test
    fun resolveFilePathFromUriString_returnsNullForContentUri() {
        val resolved = resolveFilePathFromUriString("content://downloads/all_downloads/123")

        assertNull(resolved)
    }
}

package com.unknokable.videosohranenki

import android.media.MediaDataSource
import java.io.File
import java.io.RandomAccessFile

class LocalFileMediaDataSource(file: File) : MediaDataSource() {
    private val raf = RandomAccessFile(file, "r")
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= raf.length()) return -1
        raf.seek(position)
        return raf.read(buffer, offset, size)
    }
    override fun getSize(): Long = raf.length()
    override fun close() = raf.close()
}

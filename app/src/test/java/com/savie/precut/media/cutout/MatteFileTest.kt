package com.savie.precut.media.cutout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class MatteFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun picture(seed: Int) = ByteArray(W * H) { i -> if ((i + seed) % 7 < 3) 0 else (i * seed % 256).toByte() }

    private fun write(file: File, vararg times: Long): List<ByteArray> {
        val pictures = times.indices.map { picture(it + 1) }
        MatteFile.Writer(file, W, H).use { writer ->
            times.forEachIndexed { i, t -> writer.add(t, pictures[i]) }
            writer.finish()
        }
        return pictures
    }

    @Test
    fun roundTrip_readsAnyPictureDirectly() {
        val file = temp.newFile()
        val pictures = write(file, 0L, 33_333L, 66_667L, 100_000L)
        MatteFile.Reader.open(file)!!.use { reader ->
            assertEquals(W, reader.width)
            assertEquals(H, reader.height)
            assertEquals(4, reader.frameCount)
            val into = ByteArray(W * H)
            for (i in listOf(2, 0, 3, 1)) {
                reader.read(i, into)
                assertArrayEquals(pictures[i], into)
            }
        }
    }

    @Test
    fun indexAt_isTheLatestPictureOrTheNearestWithTolerance() {
        val file = temp.newFile()
        write(file, 1_000_000L, 1_033_333L, 1_066_667L)
        MatteFile.Reader.open(file)!!.use { reader ->
            assertEquals(0, reader.indexAt(0L))
            assertEquals(0, reader.indexAt(1_000_000L))
            assertEquals(0, reader.indexAt(1_033_000L))
            assertEquals(1, reader.indexAt(1_033_000L, toleranceUs = 16_700L))
            assertEquals(1, reader.indexAt(1_050_000L))
            assertEquals(2, reader.indexAt(1_050_000L, toleranceUs = 16_700L))
            assertEquals(2, reader.indexAt(9_000_000L))
        }
    }

    @Test
    fun outOfOrderTimes_areDropped() {
        val file = temp.newFile()
        MatteFile.Writer(file, W, H).use { writer ->
            writer.add(100L, picture(1))
            writer.add(100L, picture(2))
            writer.add(50L, picture(3))
            writer.add(200L, picture(4))
            assertEquals(2, writer.frameCount)
            writer.finish()
        }
        MatteFile.Reader.open(file)!!.use { assertEquals(200L, it.timeUs(1)) }
    }

    @Test
    fun missingUnfinishedOrDamagedFiles_dontOpen() {
        assertNull(MatteFile.Reader.open(File(temp.root, "missing.matte")))
        // Never finished: no index.
        val unfinished = temp.newFile()
        MatteFile.Writer(unfinished, W, H).use { it.add(0L, picture(1)) }
        assertNull(MatteFile.Reader.open(unfinished))
        // Not a matte at all.
        val other = temp.newFile().apply { writeBytes(ByteArray(100) { 7 }) }
        assertNull(MatteFile.Reader.open(other))
        // Cut short.
        val whole = temp.newFile()
        write(whole, 0L, 10L)
        val cut = temp.newFile().apply { writeBytes(whole.readBytes().copyOf(whole.length().toInt() - 9)) }
        assertNull(MatteFile.Reader.open(cut))
    }

    @Test(expected = IOException::class)
    fun damagedPicture_failsToRead() {
        val file = temp.newFile()
        write(file, 0L)
        val bytes = file.readBytes()
        // Scramble the deflated picture (after the 16-byte header), keeping the index intact.
        for (i in 16 until 40) bytes[i] = 0x55
        file.writeBytes(bytes)
        MatteFile.Reader.open(file)!!.use { it.read(0, ByteArray(W * H)) }
    }

    private companion object {
        const val W = 16
        const val H = 9
    }
}

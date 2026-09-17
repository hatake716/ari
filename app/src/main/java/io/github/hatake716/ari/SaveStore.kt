package io.github.hatake716.ari

import android.content.Context
import android.util.AtomicFile
import java.io.File

sealed class Slot {
    data object Empty : Slot()
    data class Saved(val colony: Colony) : Slot()
    data class Damaged(val reason: String) : Slot()
}
class SaveStore(context: Context) {
    private val dir = File(context.filesDir,"colonies").apply { mkdirs() }
    private fun file(slot: Int): AtomicFile {
        require(slot in 0..2)
        return AtomicFile(File(dir,"colony-${slot + 1}.json"))
    }
    fun read(slot: Int): Slot {
        val f=file(slot)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return Slot.Empty
        return try { Slot.Saved(StateCodec.decode(f.openRead().bufferedReader().use { it.readText() })) }
        catch (_: Exception) { Slot.Damaged("保存データを読み込めません。元のファイルは保持しています。") }
    }
    fun write(slot: Int, colony: Colony) {
        val oldTimestamp=colony.lastSavedMillis
        colony.lastSavedMillis=System.currentTimeMillis()
        val bytes=StateCodec.encode(colony).toByteArray(Charsets.UTF_8)
        val f=file(slot)
        val out=try { f.startWrite() } catch (error: Exception) { colony.lastSavedMillis=oldTimestamp; throw error }
        try { out.write(bytes); f.finishWrite(out) }
        catch (error: Exception) { f.failWrite(out); colony.lastSavedMillis=oldTimestamp; throw error }
    }
    fun delete(slot: Int) { file(slot).delete() }
}

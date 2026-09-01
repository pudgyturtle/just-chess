package dev.mulvey.justchess

import android.app.Application
import dev.mulvey.justchess.data.AppRepository
import java.io.File

class JustChessApp : Application() {
    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
    }

    fun stockfishBinary(): File {
        return File(applicationInfo.nativeLibraryDir, "libstockfish.so")
    }
}

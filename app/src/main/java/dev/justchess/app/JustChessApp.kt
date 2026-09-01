package dev.justchess.app

import android.app.Application
import dev.justchess.app.data.AppRepository
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

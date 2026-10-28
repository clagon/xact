package dev.clagon.xact

import android.app.Application
import okhttp3.OkHttpClient

/** 依存を手書きで組み立てるコンテナを持つ（ADR 0004）。 */
class XactApplication : Application() {
    val container by lazy { AppContainer(Settings(this)) }
}

class AppContainer(
    val settings: Settings,
) {
    val http = OkHttpClient()
}

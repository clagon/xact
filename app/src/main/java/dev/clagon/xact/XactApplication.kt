package dev.clagon.xact

import android.app.Application

/** 依存を手書きで組み立てるコンテナを持つ（ADR 0004）。部品は各チケットで追加する。 */
class XactApplication : Application() {
    val container = AppContainer()
}

class AppContainer

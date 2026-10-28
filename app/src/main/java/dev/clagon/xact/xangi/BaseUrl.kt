package dev.clagon.xact.xangi

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * ユーザーが入力した xangi の接続先を `http://host:port` 形式に揃える。
 * scheme を省いた `100.64.0.1:18888` も受け付ける。OkHttp と同じ規則で検証し、解釈できなければ null。
 */
fun normalizeBaseUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.encodedPath != "/" || url.query != null) return null
    return url.toString().trimEnd('/')
}

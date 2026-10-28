package dev.clagon.xact.xangi

import java.net.URI

/**
 * ユーザーが入力した xangi の接続先を `http://host:port` 形式に揃える。
 * scheme を省いた `100.64.0.1:18888` も受け付ける。解釈できなければ null。
 */
fun normalizeBaseUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https") || uri.host == null) return null
    if (uri.path
            .orEmpty()
            .trimEnd('/')
            .isNotEmpty() ||
        uri.query != null
    ) {
        return null
    }
    return "${uri.scheme}://${uri.rawAuthority}"
}

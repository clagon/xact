# DI フレームワークと端末内 DB を使わず、依存ライブラリを固定する

Hilt は使わず、`Application` が持つ手書きのコンテナで依存を組み立てる。Room も使わない。データの正本は xangi にあり、端末内に保存するのは DataStore の接続先 URL と Main Chat の Session ID だけにする。依存ライブラリは、Compose・Activity・Lifecycle（ViewModel）といった Android の標準構成に加え、OkHttp + okhttp-sse、kotlinx.serialization、Coil、Markdown 描画ライブラリ 1 つ、DataStore に固定する。テスト用には JUnit、MockWebServer、kotlinx-coroutines-test を使う。これ以外を追加するときは ADR を書く。部品が数個しかない規模では、DI フレームワークのコード生成やオフラインキャッシュは、それ自体が保守の対象になるだけで得るものがないため。

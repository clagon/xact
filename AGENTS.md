# AGENTS.md

xact は xangi をバックエンドとする、Meta Muse 風の個人用 Android クライアント。

- 用語: `CONTEXT.md`。チケット・コード・PR では、ここに定義された用語を使う
- 設計判断: `docs/adr/`。ADR に反する変更をする場合は、先に ADR を更新する

## アーキテクチャ

- `:app` 単一モジュール。パッケージは機能ごとに分ける: `mainchat` / `sidechat` / `notify` / `xangi`
- xangi の HTTP・SSE・JSON を扱うのは `xangi` パッケージだけ。外部にはドメイン用語の型（Session、Turn、Side Chat の状態など）だけを公開する（ADR 0003）
- xangi 本体は改造しない（ADR 0001）
- 画面は ViewModel + `StateFlow` の UDF。UI 状態は画面ごとに 1 つの data class で持つ
- DI は `Application` が持つ手書きコンテナ 1 つ。Hilt は使わない
- データの正本は xangi。端末内に保存するのは DataStore の接続先 URL と Main Chat の Session ID だけ

## 依存ライブラリ

OkHttp + okhttp-sse、kotlinx.serialization、Coil、Compose 用の Markdown 描画ライブラリ 1 つ、DataStore。これ以外を追加するときは ADR を書く（ADR 0004）。

## テスト

- 純粋な Kotlin のユニットテストだけを書く。対象は xangi の API・SSE の解釈と、イベントから状態を導く処理。MockWebServer を使う
- Compose の UI テストと E2E テストは書かない

## 品質ゲート

PR ごとに GitHub Actions で ktlint、Android Lint、ユニットテストを実行する。すべて通らないとマージしない。

## 進め方

- 1 チケット = 1 ブランチ = 1 PR。squash merge する
- minSdk は 37（Android 17）。古い API レベル向けの分岐は書かない

## 言語

識別子は英語。コメント、ADR、コミットメッセージ、PR は日本語。

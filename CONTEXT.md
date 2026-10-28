# xact

Meta Muse 風の体験を、xangi をバックエンドとして提供する個人用 Android クライアント。

## Language

**xact**:
この Android アプリ。xangi の Web Chat API / events SSE を使うクライアント。
_Avoid_: クライアント、アプリ（単独で使うと曖昧）

**xangi**:
バックエンド。AI エージェント CLI を Session 単位で動かすサーバ。xact からは改造せず使う。

**Main Chat**:
ユーザーとエージェントの、ずっと続く一本の会話。日をまたいで文脈を保つ。
_Avoid_: ホーム、メインスレッド

**Side Chat**:
Main Chat から切り出した、特定の用件のための独立した会話。xangi の Session と 1:1 に対応する。「サイド」だが長期間残す前提で、削除はしない。
_Avoid_: タスク、スレッド

**Archive**:
Side Chat を一覧の「アクティブ」から外した状態。xangi の Session の close に対応し、reopen で戻せる。
_Avoid_: 削除、完了

**Card**:
Side Chat の節目（切り出し、入力待ち、エラー）を Main Chat の画面に差し込む表示。xact の画面上だけのもので、Main Chat の Session の会話には含まれない。
_Avoid_: 通知（OS の通知とは別物）

**Session**:
xangi 側の会話単位（`appSessionId`）。xact では Main Chat と各 Side Chat がそれぞれ 1 つの Session を持つ。

**Turn**:
Session 内の 1 往復（ユーザー入力 1 回 → エージェント応答 1 回）。

## Relationships

- **Main Chat** は 1 つだけ存在し、1 つの **Session** を持つ
- **Side Chat** は 0 個以上存在し、それぞれ 1 つの **Session** を持つ
- **Session** は 1 つ以上の **Turn** を含む
- **Card** は 1 つの **Side Chat** を指す。Main Chat のエージェントからは見えない

## Flagged ambiguities

- 「タスク」: Muse の用語だが、xact では **Side Chat** と呼ぶ。xangi の Session と混同しないため。

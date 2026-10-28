# Side Chat の一覧は xangi の Session タイトルの接頭辞で表す

Side Chat かどうかは、xangi の Session タイトルの先頭に `[side] ` を付けて区別する。一覧を xact の端末内やワークスペースのファイルに持つ案もあったが、Main Chat のエージェントが `/api/sessions` から Side Chat を見つけて投稿できるようにするため、持つ情報を xangi 側の1か所に集めた。代わりに、xangi の Web UI でタイトルを変えると Side Chat ではなくなる。また、xangi 側にも `[side]` という表示がそのまま見える。

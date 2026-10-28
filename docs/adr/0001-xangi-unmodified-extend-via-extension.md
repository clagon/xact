# xangi 本体は改造せず、不足 API は xangi extension で補う

xact は xangi をそのまま使う。承認フローやプッシュ通知など、Muse 風の体験に必要だが xangi にない機能は、本体へのパッチではなく xangi extension として追加する。本体を fork すると上流の頻繁な更新に追従できなくなるため。extension は本体と独立して更新・削除できる。ただし、extension ではできない機能（例：エージェント CLI の承認待ち状態への介入）は諦める。

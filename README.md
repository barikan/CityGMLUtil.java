# CityGMLUtil.java

「PLATEAUのCityGMLからMVTを作成する作業の記録」https://qiita.com/t-mat/items/a57f44c46d9e7f0aac56 にあるPLATEAU変換処理が、動かなくなっていたので変更してみました。

## 変更点

### スキーマ参照先が変わっている

「技術仕様案、XMLスキーマ―等の公開URLの移行完了について」  
https://www.chisou.go.jp/tiiki/toshisaisei/itoshisaisei/specification.html  
「i-UR1.4に従って作成されたデータへの対応方法」  
https://www.chisou.go.jp/tiiki/toshisaisei/itoshisaisei/iur/domain.pdf  

「移行完了」とは前向きDXでさすがです。泣けてくるよ。

### 東京には天井データしかない。

建物形状を床から取得するロジックになっていましたが、東京のデータは屋根データしかないので、床がなければ屋根を取得するように変更しました。方言があるのかな。

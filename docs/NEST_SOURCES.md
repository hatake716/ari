# 巣の形状モデルの出典と利用条件

確認日: 2026-09-18。商用利用と改変を認める **Creative Commons Attribution 4.0 International (CC BY 4.0)** を各掲載元のライセンスリンクで確認。[ライセンス本文と条件](https://creativecommons.org/licenses/by/4.0/)に従い作者・出典・変更点をここ、およびアプリ内「遊び方と生態」に表示します。作者による本作の推奨を意味しません。

## 複数の縦坑と横長の部屋

- **作者**: Ingrid de Carvalho Guimarães, Márlon César Pereira, Nathan Rodrigues Batista, Candida Anitta Pereira Rodrigues, William Fernando Antonialli Junior.
- **作品**: *The complex nest architecture of the Ponerinae ant Odontomachus chelifer* (2018), PLOS ONE 13(1): e0189896.
- **掲載元**: https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0189896
- **ライセンス**: CC BY 4.0。記事のCopyright欄に明記、リンク先もCC BY 4.0。
- **参考にしたもの**: 調査に基づく巣の構造モデル。複数の下降坑、側方の連絡、比較的水平な部屋、サイズの異なる空洞が作る階層的な形。調査された巣は24〜77室であり、48を一般的な生物学上限とする根拠はありません。
- **本作での変更**: 構造上の特徴を2Dの生成グラフへ簡略化。入口は初期設計の1か所を維持し、原図の座標や接続網をそのまま複製しません。部屋の用途、増築頻度、上限を設けない成長は独自設定です。

## 不均一な部屋と坑道を持つ公開3Dモデル

- **作者**: Nathan Rodrigues Batista, Vinicius Edson Soares de Oliveira, William Fernando Antonialli-Junior.
- **作品**: *A 3D model to illustrate the nest architecture of Acromyrmex balzani (Hymenoptera; Formicidae)* (2021), Revista Brasileira de Entomologia 65(3): e20210037.
- **掲載元**: https://www.scielo.br/j/rbent/a/JWyskVC9KZ9Qd8QRc9yWP8J/?lang=en
- **DOI**: https://doi.org/10.1590/1806-9665-RBENT-2021-0037
- **ライセンス**: 掲載ページがCC BY 4.0へリンク。PDFの著作権欄にもCC BYによる利用・転載許可あり。
- **対象モデル**: Figures 1–2とSupplementary Material 3「Computational 3D models」。付録ZIPにはモデル説明PDFと9個の`.promob`ファイルを収録。ネイティブモデルのプレビューとXML構造を確認しました。
- **付録**: https://minio.scielo.br/documentstore/1806-9665/JWyskVC9KZ9Qd8QRc9yWP8J/e330232e97836d9c236836c4ca80857fd3a1437b.zip
- **取得ZIPのSHA-256**: `7c2e3823a351f24d25e711fcbcc173c302d38aac1e21bceec0e4cbc6ab1ef70d`
- **参考にしたもの**: 下降坑でつながる寸法・間隔の異なる楕円状の室、表面の不均一さ、付属する小さな空洞。
- **本作での変更**: 3D復元の構造を参照し、凹凸のある横長の断面輪郭、ゆるい曲線の坑道、決定的なばらつきとして独自実装。菌園・植物製の入口・実測寸法や室数はゲームへ移植しません。

## 実装との対応

- `NestGrowth.kt`: 固定格子を使わない縦坑・枝分かれ候補、干渉検査、混雑時の安全な既存室拡張。部屋の輪郭・間隔・分岐確率などの係数は独自のゲーム設定です。
- `Colony.kt`: 曲線通路の幾何、通過時間を使う経路探索。アリ・外敵・障害物を同じ経路へ対応させます。
- `NestView.kt`: 横長で凹凸のある室、太さに小さな変動のある坑道を土の断面へ動的描画。大きな巣でも表示画面内だけのキャッシュを作成します。

原論文の図・モデルファイルはアプリおよびGitへ同梱していません。原モデルの形態的特徴を参照した独自の断面表現であり、種の完全な復元や研究用の予測モデルとは説明しません。部屋数・深さにはゲームルールとしての固定上限を置きませんが、実際に成長する量は個体数・掘削時間・勝敗条件と端末資源に依存します。

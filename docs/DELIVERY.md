# ビルドと配布

- 表示名: アリの巣
- applicationId: `io.github.hatake716.ari`
- versionName: `1.3.0`
- versionCode: `5`
- minSdk: `26` / compileSdk・targetSdk: `36`
- JDK: 17 / Gradle: 8.14.3 / AGP: 8.13.0 / Kotlin: 2.2.20

## 成果物

| 成果物 | 用途 | ビルド先 |
|---|---|---|
| Debug APK | 開発・操作テスト | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | Android端末への直接インストール | `app/build/outputs/apk/release/app-release.apk` |
| Release AAB | 将来のGoogle Play提出用 | `app/build/outputs/bundle/release/app-release.aab` |

AABは単体で端末にインストールするAPKではありません。本作の作成作業ではGoogle Play Consoleへのアップロード・審査申請・公開は行っていません。

## 署名

ルートの `keystore.properties` が存在する場合のみ、releaseを配布鍵で署名します。設定がない環境ではreleaseは未署名です。CIのdebug APKはdebug用署名です。

```properties
storeFile=/absolute/private/path/ari-release.jks
storePassword=YOUR_PRIVATE_PASSWORD
keyAlias=ari
keyPassword=YOUR_PRIVATE_PASSWORD
```

この開発環境では初回署名鍵を `/home/takeshi/.local/share/ari/signing/ari-release.jks` に作成し、同ディレクトリのprivate設定ファイルに復元用設定を置いています。ディレクトリは700、鍵と設定は600。鍵はGitに含まれません。今後も同じアプリを上書き更新するため、この鍵とパスワードを一緒に保管してください。

## 検証手順

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease bundleRelease assembleDebug assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
# Android SDK の apksigner で成果物を検証
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk app/build/outputs/bundle/release/app-release.aab
```

UIテストは専用エミュレーターの本アプリの保存枠を初期化します。日常利用の実機を対象に実行しないでください。テストのスクリーンショットはエミュレーター内のアプリ外部files/validationとfiles/visual-v1.1に出力されます。

リリース鍵・デバッグ鍵が違うAPKは互換更新できません。ユーザーの保存データを守るため、実機の既存アプリを無断で削除して署名不一致を解消しないでください。

## 保存とプライバシー

ゲームは端末内で完結します。ネットワーク権限、広告SDK、分析SDK、アカウント登録はありません。保存データはアプリの内部ストレージに3枠、BGM設定はSharedPreferencesに保存します。アプリの削除・ストレージ消去で保存も失われます。バックアップは無効に設定しています。

AtomicFileを使用し、読み取り不能のデータは一覧で明示して元ファイルを保持します。新しいスキーマへの互換性がない場合も黙って初期化しません。保存時のディスクエラーは観察画面に表示します。


## 1.1.0

配布コピーは `artifacts/release/v1.1.0/ARI-1.1.0.apk` と `ARI-1.1.0.aab`。同じディレクトリに `manifest.json` と `SHA256SUMS` を置いています。12か月の背景はすべて同梱され、起動後のダウンロードは不要です。

1.0.0と同じ署名鍵、同じapplicationId、保存スキーマversion 1を使います。暦は既存の経過日数から計算するため、季節用のセーブデータ変換は不要です。


## 1.2.0

配布コピーは `artifacts/release/v1.2.0/ARI-1.2.0.apk` と `ARI-1.2.0.aab`。外敵の描画と増室処理を更新し、観察画面に室数・掘削進捗・全体表示を追加しています。applicationIdと署名鍵、保存スキーマversion 1は継続。旧版の保存をそのまま読めます。深い巣・48室を保存できるよう座標と室数の検証上限を広げたため、新版で増室した保存を旧版へ戻す操作は対象外です。追加の描画・成長テストの画像は専用エミュレーターの外部files/visual-v1.2へ出力します。


## 1.2.1

エンディングから同じ保存枠の巣の建設モードへ進む操作を追加。終了済みの保存枠を開き直した場合もエンディングを表示します。新たな女王を迎えるまで前の巣の保存は保持し、建設を中断した場合には旧記録を残します。保存形式・署名鍵・applicationIdは継続しています。配布コピーは `artifacts/release/v1.2.1/ARI-1.2.1.apk` と `ARI-1.2.1.aab` です。

## 1.3.0

室数と深さの固定上限を撤廃し、研究公開モデルを参考にした不規則な縦坑・側枝・横長の部屋へ更新。新しい坑道には曲がり量 `bend` を保存し、古い保存にない場合は直線として読み込みます。保存スキーマversion 1、署名鍵、applicationIdを維持。1.3.0の大きな巣を旧版へ戻すことはできません。配布先は `artifacts/release/v1.3.0/ARI-1.3.0.apk` と `ARI-1.3.0.aab`。画面キャッシュは巣全体の寸法によらず表示画面の画素数に制限し、部屋数を描画上限で代用しません。

クリア後の母女王の観察継続と、繰り返す子女王の旅立ちを追加。`completedFlights`・`nextRoyalDay`・`queenLifespanDays`・`queenDiedOfAge` を保存します。フィールドのない旧セーブは、クリア済みなら旅立ち1回・次の育成は保存された日から365日以降、母女王の寿命は創設から20年として読み込みます。新規巣は寿命10〜20年の個体差を設定。女王の寿命を再読み込みで抽選し直しません。観察継続は同じ保存枠を即時保存し、新しい巣を作る方を選んだ場合は従来どおり女王を迎えるまで旧記録を保持します。

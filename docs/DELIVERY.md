# ビルドと配布

- 表示名: アリの巣
- applicationId: `io.github.hatake716.ari`
- versionName: `1.1.0`
- versionCode: `2`
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

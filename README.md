# 音声スペクトログラム解析アプリ

リアルタイムで音声を録音し、周波数スペクトログラムを表示するAndroidアプリです。

## 機能

- **リアルタイム音声録音**: マイクからの音声をリアルタイムで取得
- **FFT処理**: 高速フーリエ変換による周波数解析
- **スペクトログラム表示**: 時間-周波数の2次元表示
- **カラーマップ**: 音量レベルを色で視覚化（青→緑→黄→赤）
- **権限管理**: 音声録音権限の適切な処理

## 技術仕様

- **サンプリングレート**: 44.1kHz
- **音声フォーマット**: PCM 16bit モノラル
- **FFTサイズ**: 動的（AudioRecordバッファサイズに基づく）
- **窓関数**: ハミング窓
- **表示範囲**: 0Hz - 22.05kHz
- **動的レンジ**: -120dB - -60dB

## 使用方法

1. アプリを起動
2. 音声録音権限を許可
3. 「録音開始」ボタンをタップ
4. リアルタイムでスペクトログラムが表示される
5. 「停止」ボタンで録音を終了

## 必要な権限

- `RECORD_AUDIO`: マイクからの音声録音
- `WRITE_EXTERNAL_STORAGE`: 将来の拡張用（現在は未使用）
- `READ_EXTERNAL_STORAGE`: 将来の拡張用（現在は未使用）

## ビルド要件

- Android Studio Arctic Fox以降
- Android SDK API Level 21以降
- Gradle 7.0以降

## プロジェクト構造

```
src/main/java/com/audioanalyzer/spectrogram/
├── MainActivity.java          # メインアクティビティ
├── FFTProcessor.java          # FFT処理クラス
└── SpectrogramView.java       # スペクトログラム表示ビュー
```

## 参考

このプロジェクトは [audio-analyzer-for-android](https://github.com/woheller69/audio-analyzer-for-android) からインスピレーションを得て作成されました。

## ライセンス

Apache License 2.0
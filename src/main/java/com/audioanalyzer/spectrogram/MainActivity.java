package com.audioanalyzer.spectrogram;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int SAMPLE_RATE = 16000; // 16kHzに変更して負荷軽減
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private SpectrogramView spectrogramView;
    private Button recordButton;
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.util.Log.d("AudioSpectrogram", "onCreate開始");
        
        setContentView(R.layout.activity_main);
        
        recordButton = findViewById(R.id.recordButton);
        spectrogramView = findViewById(R.id.spectrogramView);
        mainHandler = new Handler(Looper.getMainLooper());
        
        android.util.Log.d("AudioSpectrogram", "ビュー初期化完了 - recordButton: " + (recordButton != null) + ", spectrogramView: " + (spectrogramView != null));
        
        recordButton.setOnClickListener(v -> {
            android.util.Log.d("AudioSpectrogram", "録音ボタンクリック - isRecording: " + isRecording);
            
            if (isRecording) {
                android.util.Log.d("AudioSpectrogram", "録音停止処理開始");
                stopRecording();
                Toast.makeText(this, "録音を停止しました", Toast.LENGTH_SHORT).show();
            } else {
                android.util.Log.d("AudioSpectrogram", "録音開始処理開始");
                Toast.makeText(this, "録音を開始します", Toast.LENGTH_SHORT).show();
                
                startRecording();
            }
        });
        
        android.util.Log.d("AudioSpectrogram", "onCreate完了");
        
        checkPermissions();
    }
    
    private void testSpectrogram() {
        android.util.Log.d("AudioSpectrogram", "testSpectrogram開始");
        
        // より現実的なテストデータを生成
        double[] testData = new double[512];
        for (int i = 0; i < testData.length; i++) {
            // 複数の周波数成分を持つテストデータ
            double freq1 = -50 + 20 * Math.sin(i * Math.PI / 50); // 低周波成分
            double freq2 = -40 + 15 * Math.sin(i * Math.PI / 100); // 中周波成分
            double freq3 = -60 + 10 * Math.sin(i * Math.PI / 200); // 高周波成分
            double noise = Math.random() * 5 - 2.5; // ノイズ
            
            testData[i] = freq1 + freq2 + freq3 + noise;
        }
        
        // 複数回テストデータを送信してスペクトログラムの履歴を作成
        if (spectrogramView != null) {
            for (int j = 0; j < 10; j++) {
                // 少しずつ変化するデータを生成
                double[] variantData = new double[512];
                for (int i = 0; i < variantData.length; i++) {
                    double phase = j * 0.1;
                    double freq1 = -50 + 20 * Math.sin(i * Math.PI / 50 + phase);
                    double freq2 = -40 + 15 * Math.sin(i * Math.PI / 100 + phase);
                    double freq3 = -60 + 10 * Math.sin(i * Math.PI / 200 + phase);
                    double noise = Math.random() * 5 - 2.5;
                    
                    variantData[i] = freq1 + freq2 + freq3 + noise;
                }
                spectrogramView.updateSpectrogram(variantData);
            }
            android.util.Log.d("AudioSpectrogram", "テストデータ送信完了");
        }
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "音声録音権限が許可されました。録音を開始します。", Toast.LENGTH_SHORT).show();
                // 権限が許可されたら録音を開始
                startRecording();
            } else {
                Toast.makeText(this, "音声録音権限が拒否されました。アプリの機能を使用するには権限が必要です。", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void startRecording() {
        android.util.Log.d("AudioSpectrogram", "startRecording呼び出し");
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("AudioSpectrogram", "権限なし - 権限要求");
            Toast.makeText(this, "音声録音権限を許可してください", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }
        
        android.util.Log.d("AudioSpectrogram", "権限確認済み - 録音開始処理");
        
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Toast.makeText(this, "音声録音がサポートされていません", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            android.util.Log.d("AudioSpectrogram", "AudioRecord作成開始 - bufferSize: " + bufferSize);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 2);
            
            android.util.Log.d("AudioSpectrogram", "AudioRecord作成完了 - state: " + audioRecord.getState());
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                android.util.Log.e("AudioSpectrogram", "AudioRecord初期化失敗 - state: " + audioRecord.getState());
                Toast.makeText(this, "AudioRecord初期化に失敗しました", Toast.LENGTH_SHORT).show();
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                return;
            }
            
            android.util.Log.d("AudioSpectrogram", "録音開始");
            audioRecord.startRecording();
            isRecording = true;
            recordButton.setText("停止");
            
            android.util.Log.d("AudioSpectrogram", "録音スレッド開始");
            recordingThread = new Thread(this::recordAudio);
            recordingThread.start();
            
            android.util.Log.d("AudioSpectrogram", "startRecording完了");
            
        } catch (SecurityException e) {
            Toast.makeText(this, "音声録音権限が拒否されました。設定から権限を許可してください", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "音声録音の設定が無効です", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        } catch (Exception e) {
            Toast.makeText(this, "音声録音の開始に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void stopRecording() {
        android.util.Log.d("AudioSpectrogram", "stopRecording呼び出し - isRecording: " + isRecording);
        
        isRecording = false;
        recordButton.setText("録音開始");
        
        if (audioRecord != null) {
            android.util.Log.d("AudioSpectrogram", "AudioRecord停止処理開始");
            try {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                android.util.Log.d("AudioSpectrogram", "AudioRecord停止完了");
            } catch (Exception e) {
                android.util.Log.e("AudioSpectrogram", "AudioRecord停止中にエラー", e);
            }
        }
        
        if (recordingThread != null) {
            android.util.Log.d("AudioSpectrogram", "録音スレッド終了待機開始");
            try {
                recordingThread.join(5000); // 5秒でタイムアウト
                android.util.Log.d("AudioSpectrogram", "録音スレッド終了完了");
            } catch (InterruptedException e) {
                android.util.Log.e("AudioSpectrogram", "録音スレッド終了中に割り込み", e);
                e.printStackTrace();
            }
            recordingThread = null;
        }
        
        android.util.Log.d("AudioSpectrogram", "stopRecording完了");
    }
    
    private void recordAudio() {
        android.util.Log.d("AudioSpectrogram", "recordAudio()開始");
        
        // バッファサイズを最小にして最大速度を実現
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBufferSize, 512); // 最小512サンプル
        short[] audioBuffer = new short[bufferSize];
        FFTProcessor fftProcessor = new FFTProcessor(bufferSize);
        
        // 入力ゲイン調整用の係数
        final double INPUT_GAIN = 10.0; // 入力信号を10倍に増幅
        
        android.util.Log.d("AudioSpectrogram", "録音ループ開始 - bufferSize: " + bufferSize);
        
        int frameCount = 0;
        int consecutiveErrors = 0;
        final int MAX_CONSECUTIVE_ERRORS = 10;
        
        while (isRecording && audioRecord != null && consecutiveErrors < MAX_CONSECUTIVE_ERRORS) {
            try {
                int bytesRead = audioRecord.read(audioBuffer, 0, bufferSize);
                frameCount++;
                
                // 詳細ログは最初の数フレームのみ
                if (frameCount <= 5 || frameCount % 100 == 0) {
                    android.util.Log.d("AudioSpectrogram", "フレーム " + frameCount + ": bytesRead=" + bytesRead);
                }
                
                if (bytesRead > 0) {
                    // 正常な読み取りの場合、エラーカウンターをリセット
                    consecutiveErrors = 0;
                    
                    // 音声データの統計情報をログ出力（最初の数フレームのみ）
                    if (frameCount <= 3) {
                        int maxValue = 0;
                        int minValue = 0;
                        long sum = 0;
                        for (int i = 0; i < bytesRead; i++) {
                            short sample = audioBuffer[i];
                            if (sample > maxValue) maxValue = sample;
                            if (sample < minValue) minValue = sample;
                            sum += Math.abs(sample);
                        }
                        double average = (double) sum / bytesRead;
                        
                        android.util.Log.d("AudioSpectrogram", "音声データ統計 - Max: " + maxValue + ", Min: " + minValue + ", Avg: " + String.format("%.2f", average));
                        
                        // 最初の10サンプルをログ出力（デバッグ用）
                        StringBuilder samples = new StringBuilder("最初の10サンプル: ");
                        for (int i = 0; i < Math.min(10, bytesRead); i++) {
                            samples.append(audioBuffer[i]).append(" ");
                        }
                        android.util.Log.d("AudioSpectrogram", samples.toString());
                    }
                    
                    // 入力ゲインを適用
                    short[] amplifiedBuffer = new short[bytesRead];
                    for (int i = 0; i < bytesRead; i++) {
                        double amplified = audioBuffer[i] * INPUT_GAIN;
                        // クリッピング防止
                        amplifiedBuffer[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, amplified));
                    }
                    
                    // メルスペクトログラム処理を実行
                    if (frameCount <= 3) {
                        android.util.Log.d("AudioSpectrogram", "メルスペクトログラム処理開始（ゲイン適用後）");
                    }
                    
                    double[] melResult = fftProcessor.processMelSpectrogram(amplifiedBuffer, bytesRead);
                    
                    if (frameCount <= 3) {
                        android.util.Log.d("AudioSpectrogram", "メルスペクトログラム処理完了: melResult.length=" + melResult.length);
                        
                        // メルスペクトログラム結果の統計情報をログ出力（最初の数フレームのみ）
                        double maxMel = Double.MIN_VALUE;
                        double minMel = Double.MAX_VALUE;
                        double sumMel = 0;
                        for (double value : melResult) {
                            if (value > maxMel) maxMel = value;
                            if (value < minMel) minMel = value;
                            sumMel += value;
                        }
                        double avgMel = sumMel / melResult.length;
                        
                        android.util.Log.d("AudioSpectrogram", "メルスペクトログラム結果統計 - Max: " + String.format("%.2f", maxMel) + ", Min: " + String.format("%.2f", minMel) + ", Avg: " + String.format("%.2f", avgMel));
                    }
                    
                    // UIスレッドでメルスペクトログラムを更新
                    final int currentFrame = frameCount;
                    mainHandler.post(() -> {
                        if (spectrogramView != null) {
                            spectrogramView.updateSpectrogram(melResult);
                            if (currentFrame <= 3 || currentFrame % 100 == 0) {
                                android.util.Log.d("AudioSpectrogram", "メルスペクトログラム更新完了 - フレーム " + currentFrame);
                            }
                        }
                    });
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    consecutiveErrors++;
                    android.util.Log.e("AudioSpectrogram", "AudioRecord ERROR_INVALID_OPERATION (エラー回数: " + consecutiveErrors + ")");
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        android.util.Log.e("AudioSpectrogram", "連続エラー上限に達したため録音を停止");
                        break;
                    }
                    // 短時間待機してリトライ
                    try { Thread.sleep(10); } catch (InterruptedException ie) { break; }
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    consecutiveErrors++;
                    android.util.Log.e("AudioSpectrogram", "AudioRecord ERROR_BAD_VALUE (エラー回数: " + consecutiveErrors + ")");
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        android.util.Log.e("AudioSpectrogram", "連続エラー上限に達したため録音を停止");
                        break;
                    }
                    // 短時間待機してリトライ
                    try { Thread.sleep(10); } catch (InterruptedException ie) { break; }
                } else {
                    consecutiveErrors++;
                    android.util.Log.w("AudioSpectrogram", "予期しないbytesRead値: " + bytesRead + " (エラー回数: " + consecutiveErrors + ")");
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        android.util.Log.e("AudioSpectrogram", "連続エラー上限に達したため録音を停止");
                        break;
                    }
                }
            } catch (Exception e) {
                consecutiveErrors++;
                android.util.Log.e("AudioSpectrogram", "recordAudio()でエラー発生 (エラー回数: " + consecutiveErrors + ")", e);
                if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                    android.util.Log.e("AudioSpectrogram", "連続エラー上限に達したため録音を停止");
                    break;
                }
                // 短時間待機してリトライ
                try { Thread.sleep(50); } catch (InterruptedException ie) { break; }
            }
        }
        
        android.util.Log.d("AudioSpectrogram", "recordAudio()終了 - 総フレーム数: " + frameCount);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
}
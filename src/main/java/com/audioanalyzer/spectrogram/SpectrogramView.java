package com.audioanalyzer.spectrogram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SpectrogramView extends View {
    private static final int MAX_HISTORY = 200; // スペクトログラムの履歴数
    private static final int SAMPLE_RATE = 16000;
    
    private List<double[]> spectrogramHistory;
    private Paint paint;
    private Paint textPaint;
    private Paint gridPaint;
    private Bitmap spectrogramBitmap;
    private Canvas spectrogramCanvas;
    private int viewWidth;
    private int viewHeight;
    private double maxMagnitude = -20; // dB
    private double minMagnitude = -80; // dB
    private static int logCount = 0; // デバッグ用カウンター
    private long lastUpdateTime = 0; // 最後の更新時間
    private static final long UPDATE_INTERVAL_MS = 1; // 更新間隔（ミリ秒）- 最大速度
    
    // 水平オフセット方式のための変数
    private int scrollOffset = 0;
    
    // 動的レンジ調整のための変数（より感度の高い初期値）
    private double adaptiveMinMagnitude = -60; // より高い最小値
    private double adaptiveMaxMagnitude = -10; // より高い最大値
    private static final double DYNAMIC_RANGE_ALPHA = 0.2; // より高い適応速度
    
    // カラーマップLUT
    private int[] colorLUT = new int[256];
    
    public SpectrogramView(Context context) {
        super(context);
        init();
    }
    
    public SpectrogramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public SpectrogramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        spectrogramHistory = new ArrayList<>();
        
        paint = new Paint();
        paint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24);
        textPaint.setAntiAlias(true);
        
        gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStrokeWidth(1);
        gridPaint.setAlpha(128);
        
        // カラーマップLUTを初期化
        initializeColorLUT();
        
        android.util.Log.d("SpectrogramView", "SpectrogramView初期化完了");
    }
    
    private void initializeColorLUT() {
        // より自然なカラーマップ（紫色の区間を減らし、青-緑-黄-赤のグラデーション）
        for (int i = 0; i < 256; i++) {
            float normalized = i / 255.0f;
            
            // 改良されたカラーマップ（jet風だが紫を抑制）
            int r, g, b;
            if (normalized < 0.2f) {
                // 黒から濃い青
                float t = normalized * 5;
                r = 0;
                g = 0;
                b = (int) (t * 128);
            } else if (normalized < 0.4f) {
                // 濃い青から明るい青
                float t = (normalized - 0.2f) * 5;
                r = 0;
                g = (int) (t * 100);
                b = (int) (128 + t * 127);
            } else if (normalized < 0.6f) {
                // 青から緑
                float t = (normalized - 0.4f) * 5;
                r = 0;
                g = (int) (100 + t * 155);
                b = (int) (255 - t * 255);
            } else if (normalized < 0.8f) {
                // 緑から黄
                float t = (normalized - 0.6f) * 5;
                r = (int) (t * 255);
                g = 255;
                b = 0;
            } else {
                // 黄から赤
                float t = (normalized - 0.8f) * 5;
                r = 255;
                g = (int) (255 - t * 255);
                b = 0;
            }
            
            colorLUT[i] = Color.rgb(Math.max(0, Math.min(255, r)), 
                                   Math.max(0, Math.min(255, g)), 
                                   Math.max(0, Math.min(255, b)));
        }
    }
    

    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        viewWidth = w;
        viewHeight = h;
        scrollOffset = 0; // オフセットをリセット
        
        android.util.Log.d("SpectrogramView", "onSizeChanged: " + w + "x" + h);
        
        if (w > 0 && h > 0) {
            spectrogramBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            spectrogramCanvas = new Canvas(spectrogramBitmap);
            spectrogramCanvas.drawColor(Color.BLACK);
            
            android.util.Log.d("SpectrogramView", "ビットマップ作成完了: " + w + "x" + h);
            

        }
    }
    
    public void updateSpectrogram(double[] magnitudes) {
        long currentTime = System.currentTimeMillis();
        
        if (magnitudes == null || viewWidth <= 0 || viewHeight <= 0) {
            android.util.Log.w("SpectrogramView", "updateSpectrogram: 無効なパラメータ - magnitudes=" + (magnitudes != null ? magnitudes.length : "null") + ", viewWidth=" + viewWidth + ", viewHeight=" + viewHeight);
            return;
        }
        
        // 最大速度のため更新頻度制御を無効化
        lastUpdateTime = currentTime;
        
        android.util.Log.d("SpectrogramView", "スペクトログラム更新開始 - データ長: " + magnitudes.length);
        
        // 動的レンジ調整
        updateDynamicRange(magnitudes);
        
        // 履歴に追加
        spectrogramHistory.add(magnitudes.clone());
        
        // 履歴サイズを制限（画面幅まで保持）
        int maxHistorySize = spectrogramBitmap != null ? spectrogramBitmap.getWidth() : MAX_HISTORY;
        while (spectrogramHistory.size() > maxHistorySize) {
            spectrogramHistory.remove(0);
        }
        
        // スペクトログラムを再描画
        redrawSpectrogram();
        
        // ビューを更新
        invalidate();
    }
    
    private void updateDynamicRange(double[] magnitudes) {
        // 現在のフレームの最大・最小値を計算
        double frameMin = Double.MAX_VALUE;
        double frameMax = Double.MIN_VALUE;
        
        for (double magnitude : magnitudes) {
            if (!Double.isNaN(magnitude) && !Double.isInfinite(magnitude)) {
                frameMin = Math.min(frameMin, magnitude);
                frameMax = Math.max(frameMax, magnitude);
            }
        }
        
        // 有効な値が見つからない場合はデフォルト値を使用
        if (frameMin == Double.MAX_VALUE || frameMax == Double.MIN_VALUE) {
            frameMin = -60;
            frameMax = -10;
        }
        
        // より保守的な動的レンジ調整（変化を抑制し、紫色の過度な表示を防ぐ）
        double conservativeAlpha = DYNAMIC_RANGE_ALPHA * 0.2; // 適応速度をさらに減少
        
        // 最大値は上方向にのみ調整（下がりすぎを防ぐ）
        if (frameMax > adaptiveMaxMagnitude) {
            adaptiveMaxMagnitude = adaptiveMaxMagnitude * (1 - conservativeAlpha) + frameMax * conservativeAlpha;
        } else {
            adaptiveMaxMagnitude = adaptiveMaxMagnitude * 0.9995 + frameMax * 0.0005; // 非常にゆっくり下降
        }
        
        // 最小値は下方向にのみ調整（上がりすぎを防ぐ）
        if (frameMin < adaptiveMinMagnitude) {
            adaptiveMinMagnitude = adaptiveMinMagnitude * (1 - conservativeAlpha) + frameMin * conservativeAlpha;
        } else {
            adaptiveMinMagnitude = adaptiveMinMagnitude * 0.9995 + frameMin * 0.0005; // 非常にゆっくり上昇
        }
        
        // 固定の最小レンジを保証（30dBの範囲を確保してコントラストを改善）
        double currentRange = adaptiveMaxMagnitude - adaptiveMinMagnitude;
        if (currentRange < 30) {
            double center = (adaptiveMaxMagnitude + adaptiveMinMagnitude) / 2;
            adaptiveMinMagnitude = center - 15;
            adaptiveMaxMagnitude = center + 15;
        }
        
        // 絶対的な境界を設定（より感度の高い範囲）
        adaptiveMinMagnitude = Math.max(-80, Math.min(-40, adaptiveMinMagnitude));
        adaptiveMaxMagnitude = Math.max(-20, Math.min(10, adaptiveMaxMagnitude));
        
        // デバッグログ（最初の数フレームのみ）
        if (logCount < 5) {
            android.util.Log.d("SpectrogramView", "動的レンジ調整: frameMin=" + String.format("%.1f", frameMin) + 
                ", frameMax=" + String.format("%.1f", frameMax) + 
                ", adaptiveMin=" + String.format("%.1f", adaptiveMinMagnitude) + 
                ", adaptiveMax=" + String.format("%.1f", adaptiveMaxMagnitude));
            logCount++;
        }
    }
    

    
    // メルスペクトログラム用のテストデータ生成
    public void generateMelSpectrogramTestData() {
        android.util.Log.d("SpectrogramView", "generateMelSpectrogramTestData開始");
        
        // メルスケールでの周波数分布を模擬
        for (int j = 0; j < 100; j++) {
            double[] melData = generateMelScaleData(j);
            updateSpectrogram(melData);
            
            // アニメーション効果
            try {
                Thread.sleep(25); // 待機時間を短縮
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        android.util.Log.d("SpectrogramView", "generateMelSpectrogramTestData完了");
    }
    
    // メルスケールデータの生成
    private double[] generateMelScaleData(int timeFrame) {
        double[] melData = new double[128]; // メルスペクトログラムは通常128バンド
        double time = timeFrame * 0.05;
        
        for (int i = 0; i < melData.length; i++) {
            // メルスケールでの周波数（低周波数により多くの解像度）
            double melFreq = (double) i / melData.length;
            double linearFreq = melToLinear(melFreq);
            
            // 音楽的なパターンを生成（低周波数により強いエネルギー）
            double baseEnergy = -60 + 40 * Math.exp(-linearFreq * 3); // 低周波数で高エネルギー
            
            // 時間変化パターン
            double temporal1 = 15 * Math.sin(time * 2 + melFreq * 8);
            double temporal2 = 10 * Math.cos(time * 1.5 + melFreq * 12);
            double temporal3 = 8 * Math.sin(time * 3 + melFreq * 6);
            
            // 楽器の倍音構造を模擬
            double harmonics = 0;
            for (int h = 1; h <= 5; h++) {
                double harmonicFreq = linearFreq * h;
                if (harmonicFreq < 1.0) {
                    harmonics += (12.0 / h) * Math.sin(time * 4 + harmonicFreq * 20);
                }
            }
            
            // 人間の声の特徴を模擬（フォルマント）
            double formant1 = 8 * Math.exp(-Math.pow((linearFreq - 0.15) * 10, 2)); // 第1フォルマント
            double formant2 = 6 * Math.exp(-Math.pow((linearFreq - 0.35) * 8, 2));  // 第2フォルマント
            double formant3 = 4 * Math.exp(-Math.pow((linearFreq - 0.65) * 6, 2));  // 第3フォルマント
            
            melData[i] = baseEnergy + temporal1 + temporal2 + temporal3 + harmonics + formant1 + formant2 + formant3;
            
            // ランダムノイズを追加
            melData[i] += (Math.random() - 0.5) * 3;
            
            // 時々音楽的なスパイクを追加
            if (Math.random() < 0.03) {
                melData[i] += 15;
            }
            
            // 値の範囲を制限
            melData[i] = Math.max(-80, Math.min(-10, melData[i]));
        }
        
        // デバッグ用：最初のフレームのみログ出力
        if (timeFrame == 0) {
            android.util.Log.d("SpectrogramView", "メルスペクトログラムサンプル: [0]=" + String.format("%.1f", melData[0]) + 
                ", [32]=" + String.format("%.1f", melData[32]) + 
                ", [64]=" + String.format("%.1f", melData[64]) + 
                ", [96]=" + String.format("%.1f", melData[96]));
        }
        
        return melData;
    }
    
    // メルスケールから線形スケールへの変換
    private double melToLinear(double mel) {
        // メルスケール変換の逆関数
        return (Math.exp(mel * Math.log(1 + 4000.0 / 700.0)) - 1) * 700.0 / 4000.0;
    }
    
    private void redrawSpectrogram() {
        if (spectrogramBitmap == null || spectrogramCanvas == null) {
            return;
        }
        
        int width = spectrogramBitmap.getWidth();
        int height = spectrogramBitmap.getHeight();
        
        // 背景を黒でクリア（完全に塗りつぶし）
        spectrogramCanvas.drawColor(Color.BLACK);
        
        // 描画用ペイントを設定
        Paint drawPaint = new Paint();
        drawPaint.setAntiAlias(false); // アンチエイリアスを無効にして正確な描画
        drawPaint.setStyle(Paint.Style.FILL);
        
        int historySize = spectrogramHistory.size();
        
        if (historySize == 0) {
            return; // 履歴がない場合は何も描画しない
        }
        
        // 最新のデータから順番に描画（右端から左へ）
        for (int historyIndex = 0; historyIndex < Math.min(historySize, width); historyIndex++) {
            double[] magnitudes = spectrogramHistory.get(historySize - 1 - historyIndex);
            
            // 描画位置を計算（右端から左に向かって描画）
            int columnX = width - 1 - historyIndex;
            
            // 各周波数バンドを描画
            for (int freqIndex = 0; freqIndex < magnitudes.length; freqIndex++) {
                int color = magnitudeToColorLUT(magnitudes[freqIndex]);
                drawPaint.setColor(color);
                
                // Y座標は周波数（上が高周波、下が低周波）
                int yStart = height - (int) ((double) (freqIndex + 1) / magnitudes.length * height);
                int yEnd = height - (int) ((double) freqIndex / magnitudes.length * height);
                int rowHeight = Math.max(1, yEnd - yStart);
                
                // 1ピクセル幅の矩形を描画（隙間なく描画）
                spectrogramCanvas.drawRect(columnX, yStart, columnX + 1, yStart + rowHeight, drawPaint);
            }
        }
        
        // デバッグ情報をログ出力（最初の数回のみ）
        if (logCount < 3) {
            android.util.Log.d("SpectrogramView", "redrawSpectrogram: historySize=" + historySize + ", width=" + width + ", height=" + height);
        }
        
        invalidate();
    }
    
    private int magnitudeToColorLUT(double magnitude) {
        // NaNや無限大の値をチェック
        if (Double.isNaN(magnitude) || Double.isInfinite(magnitude)) {
            return Color.BLACK; // 無効な値は黒で表示
        }
        
        // 動的レンジを使用してdB値を0-1の範囲に正規化
        double range = adaptiveMaxMagnitude - adaptiveMinMagnitude;
        if (range <= 0) {
            return Color.BLACK; // レンジが無効な場合は黒
        }
        
        double normalized = (magnitude - adaptiveMinMagnitude) / range;
        normalized = Math.max(0, Math.min(1, normalized));
        
        // LUTインデックスを計算
        int lutIndex = (int) (normalized * 255);
        lutIndex = Math.max(0, Math.min(255, lutIndex));
        
        // デバッグログ（最初の数回のみ）
        if (logCount < 10) {
            android.util.Log.d("SpectrogramView", "色変換デバッグ - magnitude: " + String.format("%.2f", magnitude) + 
                "dB, range: [" + String.format("%.2f", adaptiveMinMagnitude) + ", " + String.format("%.2f", adaptiveMaxMagnitude) + 
                "], normalized: " + String.format("%.3f", normalized) + ", lutIndex: " + lutIndex + ", color: " + Integer.toHexString(colorLUT[lutIndex]));
        }
        
        return colorLUT[lutIndex];
    }
    
    // 後方互換性のための旧メソッド
    private int magnitudeToColor(double magnitude) {
        return magnitudeToColorLUT(magnitude);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        android.util.Log.d("SpectrogramView", "onDraw呼び出し: bitmap=" + (spectrogramBitmap != null) + ", canvas=" + (canvas != null));
        
        if (spectrogramBitmap != null) {
            android.util.Log.d("SpectrogramView", "ビットマップ描画: size=" + spectrogramBitmap.getWidth() + "x" + spectrogramBitmap.getHeight());
            canvas.drawBitmap(spectrogramBitmap, 0, 0, null);
            
            // 現在の時刻を示す赤い線を右端に描画
            Paint timePaint = new Paint();
            timePaint.setColor(Color.RED);
            timePaint.setStrokeWidth(3);
            timePaint.setAlpha(200);
            canvas.drawLine(viewWidth - 2, 0, viewWidth - 2, viewHeight, timePaint);
            
        } else {
            android.util.Log.w("SpectrogramView", "ビットマップがnullです");
            // 背景をグラデーションで塗りつぶし
            Paint gradientPaint = new Paint();
            gradientPaint.setColor(Color.rgb(20, 20, 40));
            canvas.drawRect(0, 0, viewWidth, viewHeight, gradientPaint);
            
            // 「データ待機中」メッセージを表示
            Paint messagePaint = new Paint();
            messagePaint.setColor(Color.WHITE);
            messagePaint.setTextSize(32);
            messagePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("スペクトログラムデータ待機中...", viewWidth / 2, viewHeight / 2, messagePaint);
        }
        
        // グリッドと軸ラベルを描画
        drawGrid(canvas);
        drawLabels(canvas);
    }
    
    private void drawGrid(Canvas canvas) {
        // 水平グリッド線（周波数）
        for (int i = 0; i <= 10; i++) {
            int y = (int) ((double) i / 10 * viewHeight);
            canvas.drawLine(0, y, viewWidth, y, gridPaint);
        }
        
        // 垂直グリッド線（時間）
        for (int i = 0; i <= 10; i++) {
            int x = (int) ((double) i / 10 * viewWidth);
            canvas.drawLine(x, 0, x, viewHeight, gridPaint);
        }
    }
    
    private void drawLabels(Canvas canvas) {
        // 周波数ラベル
        for (int i = 0; i <= 5; i++) {
            int frequency = (int) ((double) i / 5 * SAMPLE_RATE / 2);
            int y = viewHeight - (int) ((double) i / 5 * viewHeight);
            canvas.drawText(frequency + "Hz", 10, y - 5, textPaint);
        }
        
        // タイトル
        canvas.drawText("リアルタイムスペクトログラム", viewWidth / 2 - 100, 30, textPaint);
    }
}
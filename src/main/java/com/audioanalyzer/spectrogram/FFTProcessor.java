package com.audioanalyzer.spectrogram;

public class FFTProcessor {
    private int bufferSize;
    private SoundEngine soundEngine;
    
    // 配列再利用用のバッファ
    private float[] realPart;
    private float[] imagPart;
    private float[] magnitude;
    private float[] melCenters;
    
    public FFTProcessor(int bufferSize) {
        android.util.Log.d("AudioSpectrogram", "FFTProcessor初期化開始 - bufferSize: " + bufferSize);
        
        // bufferSizeを2の累乗に調整
        this.bufferSize = getNextPowerOfTwo(bufferSize);
        android.util.Log.d("AudioSpectrogram", "bufferSize調整: " + bufferSize + " -> " + this.bufferSize);
        
        // SoundEngineを初期化
        this.soundEngine = new SoundEngine();
        this.soundEngine.initFSin();
        
        // 配列再利用用のバッファを初期化
        this.realPart = new float[this.bufferSize];
        this.imagPart = new float[this.bufferSize];
        this.magnitude = new float[this.bufferSize / 2];
        
        // メルフィルターバンクの中心周波数を事前計算
        initializeMelCenters();
        
        android.util.Log.d("AudioSpectrogram", "FFTProcessor初期化完了 - SoundEngine準備完了");
    }
    
    private int getNextPowerOfTwo(int n) {
        if (n <= 0) return 1024; // デフォルト値
        
        int powerOfTwo = 1;
        while (powerOfTwo < n) {
            powerOfTwo <<= 1;
        }
        return powerOfTwo;
    }
    
    public double[] processFFT(short[] audioData, int length) {
        android.util.Log.d("AudioSpectrogram", "processFFT開始 - audioData: " + (audioData != null ? audioData.length : "null") + ", length: " + length + ", bufferSize: " + bufferSize);
        
        if (audioData == null) {
            android.util.Log.e("AudioSpectrogram", "audioDataがnullです");
            return new double[bufferSize / 2];
        }
        
        int n = bufferSize;
        int log2_n = (int) (Math.log(n)/Math.log(2));
        
        // SoundEngineを使用してshortからfloatに変換
        soundEngine.shortToFloat(audioData, realPart, Math.min(length, n));
        soundEngine.clearFloat(imagPart, n); // 虚数部をクリア
        
        // 残りの部分をゼロで埋める
        if (length < n) {
            for (int i = length; i < n; i++) {
                realPart[i] = 0.0f;
            }
        }
        
        android.util.Log.d("AudioSpectrogram", "ハミング窓適用開始");
        
        // ハミング窓を適用
        soundEngine.windowHamming(realPart, n);
        
        android.util.Log.d("AudioSpectrogram", "ハミング窓適用完了");
        
        // FFTを実行
        soundEngine.fft(realPart, imagPart, log2_n, 0);
        
        // 極座標に変換（magnitude計算）
        soundEngine.toPolar(realPart, imagPart, n);
        
        // パワースペクトラムを計算
        double[] magnitude = new double[bufferSize / 2];
        for (int i = 0; i < magnitude.length; i++) {
            magnitude[i] = realPart[i];
            // dBスケールに変換
            magnitude[i] = 20 * Math.log10(magnitude[i] + 1e-10);
        }
        
        return magnitude;
    }
    
    // 配列再利用版メルスペクトログラム処理
    public void processMelSpectrogramToBuffer(short[] audioData, int length, double[] outputBuffer) {
        android.util.Log.d("AudioSpectrogram", "processMelSpectrogramToBuffer開始 - audioData: " + (audioData != null ? audioData.length : "null") + ", length: " + length);
        
        // FFTを実行（配列再利用）
        processFFTToBuffer(audioData, length);
        
        // メルフィルターバンクを適用（配列再利用）
        applyMelFilterBankToBuffer(outputBuffer);
        
        android.util.Log.d("AudioSpectrogram", "processMelSpectrogramToBuffer完了");
    }
    
    // 配列再利用版FFT処理（SoundEngine使用）
    private void processFFTToBuffer(short[] audioData, int length) {
        if (audioData == null) {
            android.util.Log.e("AudioSpectrogram", "audioDataがnullです");
            return;
        }
        
        int n = bufferSize;
        int log2_n = (int) (Math.log(n)/Math.log(2));
        
        // SoundEngineを使用してshortからfloatに変換
        soundEngine.shortToFloat(audioData, realPart, Math.min(length, n));
        soundEngine.clearFloat(imagPart, n); // 虚数部をクリア
        
        // 残りの部分をゼロで埋める
        if (length < n) {
            for (int i = length; i < n; i++) {
                realPart[i] = 0.0f;
            }
        }
        
        // ハミング窓を適用
        soundEngine.windowHamming(realPart, n);
        
        // FFTを実行
        soundEngine.fft(realPart, imagPart, log2_n, 0);
        
        // 極座標に変換（magnitude計算）
        soundEngine.toPolar(realPart, imagPart, n);
        
        // magnitude配列に結果をコピー（前半のみ使用）
        System.arraycopy(realPart, 0, magnitude, 0, magnitude.length);
        
        // dBスケールに変換
        for (int i = 0; i < magnitude.length; i++) {
            magnitude[i] = (float)(20 * Math.log10(magnitude[i] + 1e-10));
        }
    }
    
    // 配列再利用版メルフィルターバンク処理
    private void applyMelFilterBankToBuffer(double[] outputBuffer) {
        int numMelBands = 128; // メルバンド数
        
        // サンプリング周波数（16000Hz）
        float sampleRate = 16000.0f;
        float nyquistFreq = sampleRate / 2.0f;
        
        // 各メルバンドの値を計算
        for (int m = 0; m < numMelBands && m < outputBuffer.length; m++) {
            float sum = 0.0f;
            float weightSum = 0.0f;
            
            // FFTビンをメルバンドにマッピング
            for (int k = 0; k < magnitude.length; k++) {
                float freq = k * nyquistFreq / magnitude.length;
                
                // 三角フィルターの重み計算
                float weight = 0.0f;
                
                if (freq >= melCenters[m] && freq <= melCenters[m + 1]) {
                    // 左側の傾斜
                    weight = (freq - melCenters[m]) / (melCenters[m + 1] - melCenters[m]);
                } else if (freq >= melCenters[m + 1] && freq <= melCenters[m + 2]) {
                    // 右側の傾斜
                    weight = (melCenters[m + 2] - freq) / (melCenters[m + 2] - melCenters[m + 1]);
                }
                
                if (weight > 0) {
                    sum += magnitude[k] * weight;
                    weightSum += weight;
                }
            }
            
            // 正規化
            outputBuffer[m] = weightSum > 0 ? sum / weightSum : -80.0;
            
            // 最小値を設定（ログスケールでの無音レベル）
            outputBuffer[m] = Math.max(outputBuffer[m], -80.0);
        }
    }
    
    // メルフィルターバンクの中心周波数を事前計算
    private void initializeMelCenters() {
        int numMelBands = 128;
        melCenters = new float[numMelBands + 2];
        
        // メルスケールでの周波数範囲
        float minMel = hzToMel(80.0f);   // 最低周波数 80Hz
        float maxMel = hzToMel(8000.0f); // 最高周波数 8000Hz
        
        // メルフィルターバンクの中心周波数を計算
        for (int i = 0; i < melCenters.length; i++) {
            float mel = minMel + (maxMel - minMel) * i / (numMelBands + 1);
            melCenters[i] = melToHz(mel);
        }
    }
    
    // SoundEngineを使用するため、独自のFFT実装は不要
    
    // 元のメソッドも残す（互換性のため）
    public double[] processMelSpectrogram(short[] audioData, int length) {
        android.util.Log.d("AudioSpectrogram", "processMelSpectrogram開始 - audioData: " + (audioData != null ? audioData.length : "null") + ", length: " + length);
        
        // 入力データの統計情報をログ出力（デバッグ用）
        if (audioData != null && length > 0) {
            int maxValue = 0;
            int minValue = 0;
            long sum = 0;
            int nonZeroCount = 0;
            for (int i = 0; i < Math.min(length, audioData.length); i++) {
                short sample = audioData[i];
                if (sample > maxValue) maxValue = sample;
                if (sample < minValue) minValue = sample;
                sum += Math.abs(sample);
                if (sample != 0) nonZeroCount++;
            }
            double average = length > 0 ? (double) sum / length : 0;
            
            android.util.Log.d("AudioSpectrogram", "入力音声データ統計 - Max: " + maxValue + ", Min: " + minValue + ", Avg: " + String.format("%.2f", average) + ", NonZero: " + nonZeroCount + "/" + length);
        }
        
        // 通常のFFTを実行
        double[] magnitudeDouble = processFFT(audioData, length);
        
        // FFT結果の統計情報をログ出力（デバッグ用）
        if (magnitude != null && magnitude.length > 0) {
            double maxMag = Double.MIN_VALUE;
            double minMag = Double.MAX_VALUE;
            double sumMag = 0;
            for (double value : magnitude) {
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    if (value > maxMag) maxMag = value;
                    if (value < minMag) minMag = value;
                    sumMag += value;
                }
            }
            double avgMag = magnitude.length > 0 ? sumMag / magnitude.length : 0;
            
            android.util.Log.d("AudioSpectrogram", "FFT結果統計 - Max: " + String.format("%.2f", maxMag) + "dB, Min: " + String.format("%.2f", minMag) + "dB, Avg: " + String.format("%.2f", avgMag) + "dB");
        }
        
        // メルフィルターバンクを適用
        double[] melSpectrogram = applyMelFilterBank(magnitudeDouble);
        
        // メルスペクトログラム結果の統計情報をログ出力（デバッグ用）
        if (melSpectrogram != null && melSpectrogram.length > 0) {
            double maxMel = Double.MIN_VALUE;
            double minMel = Double.MAX_VALUE;
            double sumMel = 0;
            for (double value : melSpectrogram) {
                if (!Double.isNaN(value) && !Double.isInfinite(value)) {
                    if (value > maxMel) maxMel = value;
                    if (value < minMel) minMel = value;
                    sumMel += value;
                }
            }
            double avgMel = melSpectrogram.length > 0 ? sumMel / melSpectrogram.length : 0;
            
            android.util.Log.d("AudioSpectrogram", "メルスペクトログラム結果統計 - Max: " + String.format("%.2f", maxMel) + "dB, Min: " + String.format("%.2f", minMel) + "dB, Avg: " + String.format("%.2f", avgMel) + "dB");
        }
        
        android.util.Log.d("AudioSpectrogram", "processMelSpectrogram完了 - melSpectrogram.length=" + melSpectrogram.length);
        return melSpectrogram;
    }
    
    // メルフィルターバンクの適用
    private double[] applyMelFilterBank(double[] magnitude) {
        int numMelBands = 128; // メルバンド数
        double[] melSpectrogram = new double[numMelBands];
        
        // サンプリング周波数（16000Hz）
        double sampleRate = 16000.0;
        double nyquistFreq = sampleRate / 2.0;
        
        // メルスケールでの周波数範囲
        double minMel = hzToMel(80.0);   // 最低周波数 80Hz
        double maxMel = hzToMel(8000.0); // 最高周波数 8000Hz
        
        // メルフィルターバンクの中心周波数を計算
        double[] melCenters = new double[numMelBands + 2];
        for (int i = 0; i < melCenters.length; i++) {
            double mel = minMel + (maxMel - minMel) * i / (numMelBands + 1);
            melCenters[i] = melToHz(mel);
        }
        
        // 各メルバンドの値を計算
        for (int m = 0; m < numMelBands; m++) {
            double sum = 0.0;
            double weightSum = 0.0;
            
            // FFTビンをメルバンドにマッピング
            for (int k = 0; k < magnitude.length; k++) {
                double freq = k * nyquistFreq / magnitude.length;
                
                // 三角フィルターの重み計算
                double weight = 0.0;
                
                if (freq >= melCenters[m] && freq <= melCenters[m + 1]) {
                    // 左側の傾斜
                    weight = (freq - melCenters[m]) / (melCenters[m + 1] - melCenters[m]);
                } else if (freq >= melCenters[m + 1] && freq <= melCenters[m + 2]) {
                    // 右側の傾斜
                    weight = (melCenters[m + 2] - freq) / (melCenters[m + 2] - melCenters[m + 1]);
                }
                
                if (weight > 0) {
                    sum += magnitude[k] * weight;
                    weightSum += weight;
                }
            }
            
            // 正規化
            melSpectrogram[m] = weightSum > 0 ? sum / weightSum : -80.0;
            
            // 最小値を設定（ログスケールでの無音レベル）
            melSpectrogram[m] = Math.max(melSpectrogram[m], -80.0);
        }
        
        return melSpectrogram;
    }
    
    // Hzからメルスケールへの変換（float版）
    private float hzToMel(float hz) {
        return (float)(2595.0 * Math.log10(1.0 + hz / 700.0));
    }
    
    // メルスケールからHzへの変換（float版）
    private float melToHz(float mel) {
        return (float)(700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0));
    }
    
    // Hzからメルスケールへの変換（double版、互換性のため）
    private double hzToMel(double hz) {
        return 2595.0 * Math.log10(1.0 + hz / 700.0);
    }
    
    // メルスケールからHzへの変換（double版、互換性のため）
    private double melToHz(double mel) {
        return 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);
    }
    
    // SoundEngineのwindowHammingメソッドを使用するため、独自実装は不要
    
    // SoundEngineのfftメソッドを使用するため、独自実装は不要
}
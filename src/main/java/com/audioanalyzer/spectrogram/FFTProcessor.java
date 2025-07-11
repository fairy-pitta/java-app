package com.audioanalyzer.spectrogram;

public class FFTProcessor {
    private int bufferSize;
    private double[] window;
    
    public FFTProcessor(int bufferSize) {
        android.util.Log.d("AudioSpectrogram", "FFTProcessor初期化開始 - bufferSize: " + bufferSize);
        
        // bufferSizeを2の累乗に調整
        this.bufferSize = getNextPowerOfTwo(bufferSize);
        android.util.Log.d("AudioSpectrogram", "bufferSize調整: " + bufferSize + " -> " + this.bufferSize);
        
        this.window = createHammingWindow(this.bufferSize);
        android.util.Log.d("AudioSpectrogram", "FFTProcessor初期化完了 - window配列長: " + (window != null ? window.length : "null"));
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
        
        if (window == null) {
            android.util.Log.e("AudioSpectrogram", "window配列がnullです - 再初期化します");
            window = createHammingWindow(bufferSize);
        }
        
        // 入力振幅の最大値を計算（正規化用）
        double maxAmplitude = 0.0;
        for (int i = 0; i < length && i < bufferSize; i++) {
            maxAmplitude = Math.max(maxAmplitude, Math.abs(audioData[i]));
        }
        
        // 正規化係数を計算
        double normalizationFactor = maxAmplitude > 0 ? 1.0 / maxAmplitude : 1.0;
        
        // 音声データをdouble配列に変換
        double[] realPart = new double[bufferSize];
        double[] imagPart = new double[bufferSize];
        
        android.util.Log.d("AudioSpectrogram", "ハミング窓適用開始");
        
        // ハミング窓を適用し、同時に正規化
        for (int i = 0; i < length && i < bufferSize; i++) {
            realPart[i] = audioData[i] * window[i] * normalizationFactor;
            imagPart[i] = 0.0;
        }
        
        android.util.Log.d("AudioSpectrogram", "ハミング窓適用完了");
        
        // FFTを実行
        fft(realPart, imagPart);
        
        // パワースペクトラムを計算（FFTサイズで正規化）
        double[] magnitude = new double[bufferSize / 2];
        double fftNormalization = 1.0 / bufferSize;
        for (int i = 0; i < magnitude.length; i++) {
            magnitude[i] = Math.sqrt(realPart[i] * realPart[i] + imagPart[i] * imagPart[i]) * fftNormalization;
            // dBスケールに変換
            magnitude[i] = 20 * Math.log10(magnitude[i] + 1e-10);
        }
        
        return magnitude;
    }
    
    // メルスペクトログラム処理
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
        double[] magnitude = processFFT(audioData, length);
        
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
        double[] melSpectrogram = applyMelFilterBank(magnitude);
        
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
    
    // Hzからメルスケールへの変換
    private double hzToMel(double hz) {
        return 2595.0 * Math.log10(1.0 + hz / 700.0);
    }
    
    // メルスケールからHzへの変換
    private double melToHz(double mel) {
        return 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);
    }
    
    private double[] createHammingWindow(int size) {
        android.util.Log.d("AudioSpectrogram", "ハミング窓作成開始 - size: " + size);
        
        if (size <= 0) {
            android.util.Log.e("AudioSpectrogram", "無効なサイズ: " + size);
            return new double[1024]; // デフォルトサイズ
        }
        
        double[] window = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (size - 1));
        }
        
        android.util.Log.d("AudioSpectrogram", "ハミング窓作成完了 - 配列長: " + window.length);
        return window;
    }
    
    private void fft(double[] real, double[] imag) {
        int n = real.length;
        android.util.Log.d("AudioSpectrogram", "FFT開始 - 配列長: " + n);
        
        // nが2の累乗かチェック
        if ((n & (n - 1)) != 0) {
            android.util.Log.e("AudioSpectrogram", "FFTエラー: 配列長が2の累乗ではありません - " + n);
            return;
        }
        
        // ビット反転
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            
            if (i < j && i < real.length && j < real.length) {
                double tempReal = real[i];
                double tempImag = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tempReal;
                imag[j] = tempImag;
            }
        }
        
        // FFT計算
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wlenReal = Math.cos(angle);
            double wlenImag = Math.sin(angle);
            
            for (int i = 0; i < n; i += len) {
                double wReal = 1.0;
                double wImag = 0.0;
                
                for (int j = 0; j < len / 2; j++) {
                    int u = i + j;
                    int v = i + j + len / 2;
                    
                    // 配列境界チェック
                    if (u >= real.length || v >= real.length || u < 0 || v < 0) {
                        android.util.Log.e("AudioSpectrogram", "FFT配列境界エラー: u=" + u + ", v=" + v + ", length=" + real.length);
                        continue;
                    }
                    
                    double uReal = real[u];
                    double uImag = imag[u];
                    double vReal = real[v] * wReal - imag[v] * wImag;
                    double vImag = real[v] * wImag + imag[v] * wReal;
                    
                    real[u] = uReal + vReal;
                    imag[u] = uImag + vImag;
                    real[v] = uReal - vReal;
                    imag[v] = uImag - vImag;
                    
                    double tempReal = wReal * wlenReal - wImag * wlenImag;
                    wImag = wReal * wlenImag + wImag * wlenReal;
                    wReal = tempReal;
                }
            }
        }
        
        android.util.Log.d("AudioSpectrogram", "FFT完了");
    }
}
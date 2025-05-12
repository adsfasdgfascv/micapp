package com.example.markfinalss;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class MainActivity extends AppCompatActivity {
    private View borderView;
    private ObjectAnimator rotationAnimator;
    private boolean isSoundDetected = false;

    private static final int REQUEST_CODE = 1001;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final double AMPLITUDE_THRESHOLD = 100;
    private boolean permissionGranted = false;
    private final String[] permissions = { Manifest.permission.RECORD_AUDIO };
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private String outputFile;
    private boolean soundDetected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Initialize borderView and rotationAnimator
        borderView = findViewById(R.id.borderView);
        if (borderView == null) {
            Log.e("MainActivity", "borderView is null in onCreate");
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_SHORT).show();
            return;
        }
        rotationAnimator = ObjectAnimator.ofFloat(borderView, "rotation", 0f, 360f);
        rotationAnimator.setDuration(2000);
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.setInterpolator(new LinearInterpolator());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Check and request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
        } else {
            permissionGranted = true;
        }

        // Set up output file path
        outputFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recording.pcm").getAbsolutePath();

        Button btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
                ((TextView) findViewById(R.id.tvStatus)).setText("Recording...");
            } else {
                stopRecording();
                ((TextView) findViewById(R.id.tvStatus)).setText("Ready to record");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE && grantResults.length > 0) {
            permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void startRecording() {
        if (!permissionGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE);
            return;
        }

        // Calculate the minimum buffer size required for AudioRecord
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Toast.makeText(this, "Invalid audio parameters", Toast.LENGTH_SHORT).show();
            return;
        }

        // Initialize AudioRecord
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "Failed to initialize AudioRecord", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start recording
        try {
            audioRecord.startRecording();
        } catch (SecurityException e) {
            Log.e("MainActivity", "Recording permission denied", e);
            Toast.makeText(this, "Recording permission denied", Toast.LENGTH_SHORT).show();
            return;
        }
        isRecording = true;
        soundDetected = false;
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

        // Update button UI
        Button btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setBackgroundResource(R.drawable.ic_stop); // Switch to stop icon when recording

        // Start a thread to write audio data and monitor amplitude
        recordingThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        // Write audio data to file
                        fos.write(buffer, 0, read);

                        // Calculate amplitude to detect sound
                        double amplitude = calculateAmplitude(buffer, read);
                        Log.d("MainActivity", "Amplitude: " + amplitude);
                        boolean currentSoundDetected = amplitude > AMPLITUDE_THRESHOLD;

                        if (currentSoundDetected != isSoundDetected) {
                            isSoundDetected = currentSoundDetected;
                            runOnUiThread(() -> {
                                updateBorderUI();
                                Log.d("MainActivity", "Sound detected: " + isSoundDetected);
                            });
                        }

                        if (currentSoundDetected && !soundDetected) {
                            soundDetected = true;
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sound detected!", Toast.LENGTH_SHORT).show());
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("MainActivity", "Error saving recording", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error saving recording", Toast.LENGTH_SHORT).show());
            }
        });
        recordingThread.start();

        // Set initial border state
        isSoundDetected = false;
        updateBorderUI();
    }

    private void updateBorderUI() {
        if (borderView == null) {
            Log.e("MainActivity", "borderView is null in updateBorderUI");
            return;
        }
        try {
            if (isSoundDetected) {
                borderView.setBackgroundResource(R.drawable.ring_grad);
                rotationAnimator.start();
                Log.d("MainActivity", "Set gradient border");
            } else {
                borderView.setBackgroundResource(R.drawable.ring_red);
                rotationAnimator.cancel();
                Log.d("MainActivity", "Set red border");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error updating border UI", e);
            Toast.makeText(this, "UI update error", Toast.LENGTH_SHORT).show();
        }
    }

    private double calculateAmplitude(byte[] buffer, int read) {
        long sum = 0;
        for (int i = 0; i < read; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sum += sample * sample;
        }
        double rms = Math.sqrt(sum / (read / 2.0));
        return rms;
    }

    private void stopRecording() {
        if (audioRecord != null && isRecording) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            if (recordingThread != null) {
                recordingThread.interrupt();
                recordingThread = null;
            }
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();

            // Update button UI
            Button btnRecord = findViewById(R.id.btnRecord);
            btnRecord.setBackgroundResource(R.drawable.ic_record); // Revert to record icon

            // Reset border to red
            if (borderView != null) {
                borderView.setBackgroundResource(R.drawable.ring_red);
                rotationAnimator.cancel();
            }
            isSoundDetected = false;

            // Verify the recorded file
            verifyRecordingFile();
        }
    }

    private void verifyRecordingFile() {
        File file = new File(outputFile);
        if (file.exists() && file.length() > 0) {
            Toast.makeText(this, "Recording saved! File size: " + (file.length() / 1024) + " KB", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Recording failed: File not found or empty", Toast.LENGTH_LONG).show();
        }

        if (!soundDetected) {
            Toast.makeText(this, "No significant sound detected during recording", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
}
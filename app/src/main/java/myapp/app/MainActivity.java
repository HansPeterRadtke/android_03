package myapp.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONObject;

public class MainActivity extends Activity {

  private ScrollView scrollView;
  private TextView textView;
  private WebSocketClient wsClient;
  private int totalReceived = 0;
  private int nextLogThreshold = 100000;
  private boolean isRecording = false;
  private Thread recordingThread;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
    }

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);

    Button button = new Button(this);
    button.setText("Send test message");
    layout.addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    Button speedTestButton = new Button(this);
    speedTestButton.setText("Speed Test");
    layout.addView(speedTestButton, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    Button recordButton = new Button(this);
    recordButton.setText("Start Recording");
    layout.addView(recordButton, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    textView = new TextView(this);
    textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    textView.setTextIsSelectable(true);
    textView.setSingleLine(false);
    textView.setMaxLines(Integer.MAX_VALUE);

    scrollView = new ScrollView(this);
    scrollView.setFillViewport(true);
    scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    scrollView.addView(textView);
    layout.addView(scrollView);

    setContentView(layout);

    try {
      URI uri = new URI("ws://android.jonnyonthefly.org");
      wsClient = new WebSocketClient(uri) {
        @Override
        public void onOpen(ServerHandshake handshake) {
          print("[INFO] WebSocket Connected");
        }

        @Override
        public void onMessage(String message) {
          print("[DEBUG] Received: " + message);
          try {
            JSONObject obj = new JSONObject(message);
            if (obj.has("transcription")) {
              print("[INFO] Transcription: " + obj.getString("transcription"));
            }
          } catch (Exception e) {
            print("[ERROR] Failed to parse server response: " + e.toString());
          }
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
          int len = bytes.remaining();
          totalReceived += len;
          if (totalReceived >= nextLogThreshold) {
            print("[DEBUG] Total received: " + totalReceived);
            nextLogThreshold += 100000;
          }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
          print("[INFO] WebSocket Closed: " + reason);
        }

        @Override
        public void onError(Exception ex) {
          print("[ERROR] WebSocket Error: " + ex.toString());
        }
      };
      wsClient.connect();
    } catch (Exception e) {
      print("[ERROR] WebSocket Init Failed: " + e.toString());
    }

    button.setOnClickListener(v -> {
      try {
        JSONObject obj = new JSONObject();
        obj.put("msg", "test");
        print("[DEBUG] Sending message test");
        wsClient.send(obj.toString());
      } catch (Exception e) {
        print("[ERROR] Failed to send message: " + e.toString());
      }
    });

    speedTestButton.setOnClickListener(v -> {
      try {
        int size = 1000000;
        JSONObject obj = new JSONObject();
        obj.put("size", size);
        wsClient.send(obj.toString());

        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = 'x';

        print("[DEBUG] Sending " + size + " bytes");
        long start = System.currentTimeMillis();
        wsClient.send(data);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
          @Override
          public void run() {
            long end = System.currentTimeMillis();
            double seconds = (end - start) / 1000.0;
            double speed = size / seconds;
            print("[INFO] Bandwidth: " + String.format(Locale.US, "%.2f bytes/sec", speed));
            timer.cancel();
          }
        }, 10000);
      } catch (Exception e) {
        print("[ERROR] Speed test failed: " + e.toString());
      }
    });

    recordButton.setOnClickListener(v -> {
      if (isRecording) {
        isRecording = false;
        print("[INFO] Stopped recording.");
      } else {
        isRecording = true;
        print("[INFO] Started recording.");
        recordingThread = new Thread(() -> {
          try {
            int sampleRate = 16000;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            byte[] buffer = new byte[bufferSize];
            ByteArrayOutputStream audioData = new ByteArrayOutputStream();
            recorder.startRecording();

            while (isRecording) {
              int read = recorder.read(buffer, 0, buffer.length);
              if (read > 0) {
                audioData.write(buffer, 0, read);
              }
            }

            recorder.stop();
            recorder.release();
            byte[] finalData = audioData.toByteArray();
            audioData.close();

            print("[DEBUG] Sending audio init message (" + finalData.length + " bytes)");
            JSONObject init = new JSONObject();
            init.put("audio", true);
            init.put("length", finalData.length);
            wsClient.send(init.toString());

            print("[DEBUG] Streaming audio data...");
            wsClient.send(finalData);

          } catch (Exception e) {
            print("[ERROR] Recording/send failed: " + e.toString());
          }
        });
        recordingThread.start();
      }
    });
  }

  private void print(String msg) {
    runOnUiThread(() -> {
      textView.append("[" + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "] " + msg + "\n");
      scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    });
  }
}
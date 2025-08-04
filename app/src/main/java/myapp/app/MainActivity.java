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

import java.net.URI;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;

public class MainActivity extends Activity {

  private ScrollView scrollView;
  private TextView textView;
  private final AtomicReference<WebSocketClient> wsClientRef = new AtomicReference<>(null);
  private boolean isRecording = false;
  private Thread recordingThread;
  private Button recordButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
    }

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);

    recordButton = new Button(this);
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

    initWebSocket();

    recordButton.setOnClickListener(v -> {
      if (isRecording) {
        isRecording = false;
        recordButton.setText("Start Recording");
        print("[INFO] Stopped recording.");
        if (recordingThread != null && recordingThread.isAlive()) {
          try {
            recordingThread.join();
          } catch (InterruptedException e) {
            print("[ERROR] Thread join interrupted: " + e.toString());
          }
        }
      } else {
        isRecording = true;
        recordButton.setText("Stop Recording");
        print("[INFO] Started recording.");
        recordingThread = new Thread(() -> {
          try {
            WebSocketClient wsClient;
            int retries = 0;
            while (((wsClient = wsClientRef.get()) == null || !wsClient.isOpen()) && retries < 50) {
              print("[DEBUG] Waiting for WebSocket to open...");
              Thread.sleep(100);
              retries++;
            }
            if (wsClient == null || !wsClient.isOpen()) {
              print("[ERROR] WebSocket not ready. Reinitializing.");
              initWebSocket();
              return;
            }

            int sampleRate = 16000;
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
              print("[ERROR] AudioRecorder not initialized.");
              return;
            }

            byte[] buffer = new byte[bufferSize];
            recorder.startRecording();

            JSONObject init = new JSONObject();
            init.put("audio", true);
            init.put("length", -1);
            print("[DEBUG] Sending init JSON: " + init.toString());
            wsClient.send(init.toString());

            while (isRecording) {
              int read = recorder.read(buffer, 0, buffer.length);
              if (read > 0) {
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                try {
                  wsClient.send(chunk);
                } catch (Exception e) {
                  print("[ERROR] Failed to send chunk. Reinitializing. " + e.toString());
                  initWebSocket();
                  break;
                }
              }
            }

            recorder.stop();
            recorder.release();
          } catch (Exception e) {
            print("[ERROR] Streaming recording failed: " + e.toString());
          }
        });
        recordingThread.start();
      }
    });
  }

  private void initWebSocket() {
    try {
      URI uri = new URI("ws://android.jonnyonthefly.org");
      WebSocketClient newClient = new WebSocketClient(uri) {
        @Override
        public void onOpen(ServerHandshake handshake) {
          print("[INFO] WebSocket Connected");
          runOnUiThread(() -> recordButton.setEnabled(true));
        }

        @Override
        public void onMessage(String message) {
          print("[DEBUG] Raw message from server: " + message);
          try {
            JSONObject obj = new JSONObject(message);
            if (obj.has("transcription")) {
              print("[INFO] Transcription: " + obj.getString("transcription"));
            } else {
              print("[DEBUG] JSON received but no transcription key");
            }
          } catch (Exception e) {
            print("[ERROR] Failed to parse server response: " + e.toString());
          }
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
          print("[DEBUG] Received binary chunk of " + bytes.remaining() + " bytes");
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
          print("[INFO] WebSocket Closed: " + reason);
          runOnUiThread(() -> recordButton.setEnabled(false));
        }

        @Override
        public void onError(Exception ex) {
          print("[ERROR] WebSocket Error: " + ex.toString());
        }
      };
      wsClientRef.set(newClient);
      newClient.connect();
    } catch (Exception e) {
      print("[ERROR] WebSocket Init Failed: " + e.toString());
    }
  }

  private void print(String msg) {
    runOnUiThread(() -> {
      textView.append("[" + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "] " + msg + "\n");
      scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    });
  }
}
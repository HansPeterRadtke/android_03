package myapp.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.util.Log;

public class MainActivity extends Activity {

  private ScrollView scrollView;
  private TextView   textView  ;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);

    Button button = new Button(this);
    button.setText("Button");
    layout.addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    textView = new TextView(this);
    textView.setLayoutParams    (new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    textView.setTextIsSelectable(true);
    textView.setSingleLine      (false);
    textView.setMaxLines        (Integer.MAX_VALUE);

    scrollView = new ScrollView(this);
    scrollView.setFillViewport(true);
    scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    scrollView.addView(textView);
    layout.addView(scrollView);

    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        final String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              URL url = new URL("http://android.jonnyonthefly.org/?msg=test");
              HttpURLConnection connection = (HttpURLConnection) url.openConnection();
              connection.setRequestMethod("GET");
              BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
              String inputLine;
              StringBuilder response = new StringBuilder();
              while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
              }
              in.close();
              final String result = response.toString();
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  print("[" + timestamp + "] Response: " + result);
                }
              });
            } catch (final Exception e) {
              final String fullError = Log.getStackTraceString(e);
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  print("[" + timestamp + "] Error: " + fullError);
                }
              });
            }
          }
        }).start();
      }
    });

    setContentView(layout);
  }

  private void print(String msg) {
    textView.append(msg + "\n");
    scrollView.post(new Runnable() {
      @Override
      public void run() {
        scrollView.fullScroll(View.FOCUS_DOWN);
      }
    });
  }
}
package myapp.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.ViewGroup.LayoutParams;
import myapp.app.utils.TextGenerator;

public class MainActivity extends Activity {

  private ScrollView scrollView;
  private TextView textView;
  private TextGenerator generator;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    generator = new TextGenerator();

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);

    Button button = new Button(this);
    button.setText("Button");
    layout.addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    scrollView = new ScrollView(this);
    textView = new TextView(this);
    scrollView.addView(textView);
    layout.addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    setContentView(layout);

    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        print("Random text: " + generator.generate());
      }
    });
  }

  private void print(String msg) {
    textView.append(msg + "\n");
    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
  }
}
package myapp.app.utils;

import java.util.Random;

public class TextGenerator {

  private final Random random = new Random();

  public String generate() {
    int length = 10 + random.nextInt(10); // random length between 10 and 19
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      char c = (char) ('a' + random.nextInt(26));
      sb.append(c);
    }
    return sb.toString();
  }
}
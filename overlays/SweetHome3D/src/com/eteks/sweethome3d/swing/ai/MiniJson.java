/*
 * MiniJson.java
 * Small dependency-free JSON parser used by the El Sallab AI experiment.
 */
package com.eteks.sweethome3d.swing.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the JSON subset returned by Groq structured outputs.
 * Kept local to the experiment so the upstream Sweet Home 3D dependencies
 * remain untouched.
 */
final class MiniJson {
  private final String text;
  private int index;

  private MiniJson(String text) {
    this.text = text;
  }

  public static Object parse(String text) {
    if (text == null) {
      throw new IllegalArgumentException("JSON is null");
    }
    MiniJson parser = new MiniJson(text);
    Object value = parser.readValue();
    parser.skipWhitespace();
    if (parser.index != text.length()) {
      throw parser.error("Unexpected trailing content");
    }
    return value;
  }

  public static String quote(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder result = new StringBuilder(value.length() + 16);
    result.append('\"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\"': result.append("\\\""); break;
        case '\\': result.append("\\\\"); break;
        case '\b': result.append("\\b"); break;
        case '\f': result.append("\\f"); break;
        case '\n': result.append("\\n"); break;
        case '\r': result.append("\\r"); break;
        case '\t': result.append("\\t"); break;
        default:
          if (c < 0x20) {
            String hex = Integer.toHexString(c);
            result.append("\\u");
            for (int j = hex.length(); j < 4; j++) {
              result.append('0');
            }
            result.append(hex);
          } else {
            result.append(c);
          }
      }
    }
    return result.append('\"').toString();
  }

  private Object readValue() {
    skipWhitespace();
    if (this.index >= this.text.length()) {
      throw error("Expected a value");
    }
    char c = this.text.charAt(this.index);
    if (c == '{') {
      return readObject();
    } else if (c == '[') {
      return readArray();
    } else if (c == '\"') {
      return readString();
    } else if (c == 't') {
      readLiteral("true");
      return Boolean.TRUE;
    } else if (c == 'f') {
      readLiteral("false");
      return Boolean.FALSE;
    } else if (c == 'n') {
      readLiteral("null");
      return null;
    } else if (c == '-' || c >= '0' && c <= '9') {
      return readNumber();
    }
    throw error("Unexpected character '" + c + "'");
  }

  private Map<String, Object> readObject() {
    LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
    expect('{');
    skipWhitespace();
    if (peek('}')) {
      this.index++;
      return result;
    }
    while (true) {
      skipWhitespace();
      if (!peek('\"')) {
        throw error("Expected an object key");
      }
      String key = readString();
      skipWhitespace();
      expect(':');
      result.put(key, readValue());
      skipWhitespace();
      if (peek('}')) {
        this.index++;
        return result;
      }
      expect(',');
    }
  }

  private List<Object> readArray() {
    ArrayList<Object> result = new ArrayList<Object>();
    expect('[');
    skipWhitespace();
    if (peek(']')) {
      this.index++;
      return result;
    }
    while (true) {
      result.add(readValue());
      skipWhitespace();
      if (peek(']')) {
        this.index++;
        return result;
      }
      expect(',');
    }
  }

  private String readString() {
    expect('\"');
    StringBuilder result = new StringBuilder();
    while (this.index < this.text.length()) {
      char c = this.text.charAt(this.index++);
      if (c == '\"') {
        return result.toString();
      } else if (c == '\\') {
        if (this.index >= this.text.length()) {
          throw error("Unterminated escape sequence");
        }
        char escaped = this.text.charAt(this.index++);
        switch (escaped) {
          case '\"': result.append('\"'); break;
          case '\\': result.append('\\'); break;
          case '/': result.append('/'); break;
          case 'b': result.append('\b'); break;
          case 'f': result.append('\f'); break;
          case 'n': result.append('\n'); break;
          case 'r': result.append('\r'); break;
          case 't': result.append('\t'); break;
          case 'u':
            if (this.index + 4 > this.text.length()) {
              throw error("Invalid unicode escape");
            }
            try {
              result.append((char)Integer.parseInt(this.text.substring(this.index, this.index + 4), 16));
            } catch (NumberFormatException ex) {
              throw error("Invalid unicode escape");
            }
            this.index += 4;
            break;
          default: throw error("Invalid escape character '" + escaped + "'");
        }
      } else {
        result.append(c);
      }
    }
    throw error("Unterminated string");
  }

  private Number readNumber() {
    int start = this.index;
    if (peek('-')) {
      this.index++;
    }
    readDigits();
    if (peek('.')) {
      this.index++;
      readDigits();
    }
    if (peek('e') || peek('E')) {
      this.index++;
      if (peek('+') || peek('-')) {
        this.index++;
      }
      readDigits();
    }
    String number = this.text.substring(start, this.index);
    try {
      return Double.valueOf(number);
    } catch (NumberFormatException ex) {
      throw error("Invalid number '" + number + "'");
    }
  }

  private void readDigits() {
    int start = this.index;
    while (this.index < this.text.length()) {
      char c = this.text.charAt(this.index);
      if (c < '0' || c > '9') {
        break;
      }
      this.index++;
    }
    if (this.index == start) {
      throw error("Expected a digit");
    }
  }

  private void readLiteral(String literal) {
    if (!this.text.regionMatches(this.index, literal, 0, literal.length())) {
      throw error("Expected '" + literal + "'");
    }
    this.index += literal.length();
  }

  private void skipWhitespace() {
    while (this.index < this.text.length()
        && Character.isWhitespace(this.text.charAt(this.index))) {
      this.index++;
    }
  }

  private void expect(char expected) {
    skipWhitespace();
    if (this.index >= this.text.length() || this.text.charAt(this.index) != expected) {
      throw error("Expected '" + expected + "'");
    }
    this.index++;
  }

  private boolean peek(char c) {
    return this.index < this.text.length() && this.text.charAt(this.index) == c;
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at character " + this.index);
  }
}

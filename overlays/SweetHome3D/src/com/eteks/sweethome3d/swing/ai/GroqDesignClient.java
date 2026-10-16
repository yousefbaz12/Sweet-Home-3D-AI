/*
 * GroqDesignClient.java
 * Direct Groq structured-output client for the local desktop experiment.
 */
package com.eteks.sweethome3d.swing.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/** Calls Groq without persisting the API key anywhere in Sweet Home 3D. */
public final class GroqDesignClient {
  public static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final String DESIGN_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
      + "\"summary\":{\"type\":\"string\"},"
      + "\"room\":{\"type\":\"object\",\"properties\":{"
      + "\"name\":{\"type\":\"string\"},"
      + "\"widthCm\":{\"type\":\"number\",\"minimum\":120,\"maximum\":5000},"
      + "\"depthCm\":{\"type\":\"number\",\"minimum\":120,\"maximum\":5000},"
      + "\"wallHeightCm\":{\"type\":\"number\",\"minimum\":200,\"maximum\":600},"
      + "\"wallThicknessCm\":{\"type\":\"number\",\"minimum\":5,\"maximum\":60},"
      + "\"floorTextureQuery\":{\"type\":\"string\"},"
      + "\"floorColorHex\":{\"type\":\"string\"}},"
      + "\"required\":[\"name\",\"widthCm\",\"depthCm\",\"wallHeightCm\",\"wallThicknessCm\",\"floorTextureQuery\",\"floorColorHex\"],"
      + "\"additionalProperties\":false},"
      + "\"furniture\":{\"type\":\"array\",\"maxItems\":24,\"items\":{"
      + "\"type\":\"object\",\"properties\":{"
      + "\"catalogQuery\":{\"type\":\"string\"},"
      + "\"xCm\":{\"type\":\"number\",\"minimum\":0,\"maximum\":5000},"
      + "\"yCm\":{\"type\":\"number\",\"minimum\":0,\"maximum\":5000},"
      + "\"angleDegrees\":{\"type\":\"number\",\"minimum\":-360,\"maximum\":360}},"
      + "\"required\":[\"catalogQuery\",\"xCm\",\"yCm\",\"angleDegrees\"],"
      + "\"additionalProperties\":false}}},"
      + "\"required\":[\"summary\",\"room\",\"furniture\"],\"additionalProperties\":false}";

  public AiDesignPlan generate(String prompt, String catalogContext) throws IOException {
    String apiKey = System.getenv("GROQ_API_KEY");
    if (apiKey == null || apiKey.trim().length() == 0) {
      throw new IOException("GROQ_API_KEY is not set. Use Mock mode or set a new key before starting the app.");
    }
    String model = environment("GROQ_MODEL", DEFAULT_MODEL);
    String endpoint = environment("GROQ_BASE_URL", "https://api.groq.com/openai/v1") + "/chat/completions";
    String systemPrompt =
        "You are an interior-design planning engine for Sweet Home 3D. "
        + "Convert the Arabic or English request into the exact JSON schema. "
        + "All dimensions and coordinates are centimeters. The room origin is its top-left inner corner. "
        + "Keep furniture centers inside the room, use only catalog names from the supplied list, "
        + "and leave floorTextureQuery empty if no close catalog texture exists. "
        + "Do not explain anything outside JSON.\n\nAvailable local catalog:\n" + catalogContext;

    String request = "{"
        + "\"model\":" + MiniJson.quote(model) + ","
        + "\"temperature\":0.1,\"reasoning_effort\":\"low\",\"max_completion_tokens\":3000,"
        + "\"messages\":["
        + "{\"role\":\"system\",\"content\":" + MiniJson.quote(systemPrompt) + "},"
        + "{\"role\":\"user\",\"content\":" + MiniJson.quote(prompt) + "}],"
        + "\"response_format\":{\"type\":\"json_schema\",\"json_schema\":{"
        + "\"name\":\"sweet_home_design\",\"strict\":true,\"schema\":" + DESIGN_SCHEMA + "}}}";

    HttpURLConnection connection = (HttpURLConnection)new URL(endpoint).openConnection();
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(60000);
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
    connection.setRequestProperty("Accept", "application/json");
    byte [] body = request.getBytes(UTF_8);
    connection.setFixedLengthStreamingMode(body.length);
    OutputStream output = connection.getOutputStream();
    try {
      output.write(body);
    } finally {
      output.close();
    }

    int status = connection.getResponseCode();
    String response = read(status >= 200 && status < 300
        ? connection.getInputStream() : connection.getErrorStream());
    if (status < 200 || status >= 300) {
      throw new IOException("Groq returned HTTP " + status + ": " + compact(response));
    }
    try {
      return AiDesignPlan.fromJson(extractMessageContent(response));
    } catch (RuntimeException ex) {
      throw new IOException("Groq returned an invalid design: " + ex.getMessage(), ex);
    } finally {
      connection.disconnect();
    }
  }

  @SuppressWarnings("unchecked")
  private static String extractMessageContent(String response) {
    Object rootValue = MiniJson.parse(response);
    if (!(rootValue instanceof Map)) {
      throw new IllegalArgumentException("API response isn't an object");
    }
    Object choicesValue = ((Map<String, Object>)rootValue).get("choices");
    if (!(choicesValue instanceof List) || ((List<Object>)choicesValue).isEmpty()) {
      throw new IllegalArgumentException("API response has no choices");
    }
    Object firstChoice = ((List<Object>)choicesValue).get(0);
    if (!(firstChoice instanceof Map)) {
      throw new IllegalArgumentException("API choice isn't an object");
    }
    Object messageValue = ((Map<String, Object>)firstChoice).get("message");
    if (!(messageValue instanceof Map)) {
      throw new IllegalArgumentException("API choice has no message");
    }
    Object content = ((Map<String, Object>)messageValue).get("content");
    if (!(content instanceof String)) {
      throw new IllegalArgumentException("API message has no text content");
    }
    return (String)content;
  }

  private static String read(InputStream input) throws IOException {
    if (input == null) {
      return "";
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(input, UTF_8));
    try {
      StringBuilder text = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        text.append(line);
      }
      return text.toString();
    } finally {
      reader.close();
    }
  }

  private static String compact(String text) {
    String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
    return value.length() > 500 ? value.substring(0, 500) + "\u2026" : value;
  }

  private static String environment(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.trim().length() == 0 ? defaultValue : value.trim();
  }
}

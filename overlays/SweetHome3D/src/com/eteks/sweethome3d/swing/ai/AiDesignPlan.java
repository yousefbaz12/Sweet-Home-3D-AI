/*
 * AiDesignPlan.java
 * Validated intermediate representation for prompt-to-design.
 */
package com.eteks.sweethome3d.swing.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small, deterministic design language.  The AI may propose
 * this data, but Sweet Home 3D remains responsible for validating and applying
 * it to the home model.
 */
public final class AiDesignPlan {
  public static final float MIN_ROOM_SIZE_CM = 120;
  public static final float MAX_ROOM_SIZE_CM = 5000;
  public static final int MAX_FURNITURE_COUNT = 24;

  public static final class FurniturePlacement {
    private final String catalogQuery;
    private final float xCm;
    private final float yCm;
    private final float angleDegrees;

    public FurniturePlacement(String catalogQuery, float xCm, float yCm, float angleDegrees) {
      this.catalogQuery = requiredText(catalogQuery, "catalogQuery");
      this.xCm = finite(xCm, "xCm");
      this.yCm = finite(yCm, "yCm");
      this.angleDegrees = finite(angleDegrees, "angleDegrees");
    }

    public String getCatalogQuery() { return this.catalogQuery; }
    public float getXCm() { return this.xCm; }
    public float getYCm() { return this.yCm; }
    public float getAngleDegrees() { return this.angleDegrees; }
  }

  private final String summary;
  private final String roomName;
  private final float widthCm;
  private final float depthCm;
  private final float wallHeightCm;
  private final float wallThicknessCm;
  private final String floorTextureQuery;
  private final String floorColorHex;
  private final List<FurniturePlacement> furniture;

  public AiDesignPlan(String summary, String roomName,
                      float widthCm, float depthCm,
                      float wallHeightCm, float wallThicknessCm,
                      String floorTextureQuery, String floorColorHex,
                      List<FurniturePlacement> furniture) {
    this.summary = requiredText(summary, "summary");
    this.roomName = requiredText(roomName, "room.name");
    this.widthCm = range(widthCm, MIN_ROOM_SIZE_CM, MAX_ROOM_SIZE_CM, "room.widthCm");
    this.depthCm = range(depthCm, MIN_ROOM_SIZE_CM, MAX_ROOM_SIZE_CM, "room.depthCm");
    this.wallHeightCm = range(wallHeightCm, 200, 600, "room.wallHeightCm");
    this.wallThicknessCm = range(wallThicknessCm, 5, 60, "room.wallThicknessCm");
    this.floorTextureQuery = floorTextureQuery == null ? "" : floorTextureQuery.trim();
    this.floorColorHex = normalizeColor(floorColorHex);
    if (furniture == null) {
      throw new IllegalArgumentException("furniture is required");
    }
    if (furniture.size() > MAX_FURNITURE_COUNT) {
      throw new IllegalArgumentException("Furniture limit is " + MAX_FURNITURE_COUNT);
    }
    ArrayList<FurniturePlacement> checked = new ArrayList<FurniturePlacement>(furniture.size());
    for (FurniturePlacement placement : furniture) {
      if (placement.getXCm() < 0 || placement.getXCm() > widthCm
          || placement.getYCm() < 0 || placement.getYCm() > depthCm) {
        throw new IllegalArgumentException("Furniture '" + placement.getCatalogQuery()
            + "' is outside the room bounds");
      }
      checked.add(placement);
    }
    this.furniture = Collections.unmodifiableList(checked);
  }

  public String getSummary() { return this.summary; }
  public String getRoomName() { return this.roomName; }
  public float getWidthCm() { return this.widthCm; }
  public float getDepthCm() { return this.depthCm; }
  public float getWallHeightCm() { return this.wallHeightCm; }
  public float getWallThicknessCm() { return this.wallThicknessCm; }
  public String getFloorTextureQuery() { return this.floorTextureQuery; }
  public String getFloorColorHex() { return this.floorColorHex; }
  public List<FurniturePlacement> getFurniture() { return this.furniture; }

  @SuppressWarnings("unchecked")
  public static AiDesignPlan fromJson(String json) {
    Object rootValue = MiniJson.parse(json);
    if (!(rootValue instanceof Map)) {
      throw new IllegalArgumentException("The design response must be a JSON object");
    }
    Map<String, Object> root = (Map<String, Object>)rootValue;
    String summary = text(root, "summary");
    Map<String, Object> room = object(root, "room");
    List<Object> furnitureValues = array(root, "furniture");
    ArrayList<FurniturePlacement> furniture = new ArrayList<FurniturePlacement>();
    for (Object value : furnitureValues) {
      if (!(value instanceof Map)) {
        throw new IllegalArgumentException("Each furniture entry must be an object");
      }
      Map<String, Object> item = (Map<String, Object>)value;
      furniture.add(new FurniturePlacement(
          text(item, "catalogQuery"), number(item, "xCm"), number(item, "yCm"),
          number(item, "angleDegrees")));
    }
    return new AiDesignPlan(summary, text(room, "name"),
        number(room, "widthCm"), number(room, "depthCm"),
        number(room, "wallHeightCm"), number(room, "wallThicknessCm"),
        nullableText(room, "floorTextureQuery"), nullableText(room, "floorColorHex"),
        furniture);
  }

  /**
   * Offline parser used for the complete local smoke test and as a safe demo.
   */
  public static AiDesignPlan mockFromPrompt(String prompt) {
    String normalized = normalizeDigits(prompt == null ? "" : prompt);
    String lower = normalized.toLowerCase(Locale.ENGLISH);
    float width = 400;
    float depth = 300;
    Pattern dimensions = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:x|\u00D7|\\*|\u0641\u064A|by)\\s*(\\d+(?:\\.\\d+)?)\\s*(\u0645\u062A\u0631|meters?|m|\u0633\u0645|cm)?", Pattern.CASE_INSENSITIVE);
    Matcher matcher = dimensions.matcher(normalized);
    if (matcher.find()) {
      width = Float.parseFloat(matcher.group(1));
      depth = Float.parseFloat(matcher.group(2));
      String unit = matcher.group(3);
      if (unit != null && (unit.startsWith("\u0645\u062A\u0631") || unit.toLowerCase(Locale.ENGLISH).startsWith("m"))
          || unit == null && width <= 30 && depth <= 30) {
        width *= 100;
        depth *= 100;
      }
    }
    width = clamp(width, MIN_ROOM_SIZE_CM, MAX_ROOM_SIZE_CM);
    depth = clamp(depth, MIN_ROOM_SIZE_CM, MAX_ROOM_SIZE_CM);

    String roomName = containsAny(lower, "\u062D\u0645\u0627\u0645", "bathroom") ? "Bathroom"
        : containsAny(lower, "\u0645\u0637\u0628\u062E", "kitchen") ? "Kitchen" : "AI room";
    String texture = containsAny(lower, "\u0631\u064A\u0645\u0627\u0633", "rimas", "\u0628\u064A\u062C", "beige") ? "Rimas beige"
        : containsAny(lower, "\u0631\u062E\u0627\u0645", "marble") ? "marble" : "";
    String color = containsAny(lower, "\u0631\u0645\u0627\u062F\u064A", "grey", "gray") ? "#B8B8B8"
        : containsAny(lower, "\u0627\u0632\u0631\u0642", "\u0623\u0632\u0631\u0642", "blue") ? "#A9C9E8"
        : containsAny(lower, "\u0627\u0628\u064A\u0636", "\u0623\u0628\u064A\u0636", "white") ? "#F1F1EC" : "#D8C4A5";

    ArrayList<FurniturePlacement> items = new ArrayList<FurniturePlacement>();
    if (containsAny(lower, "\u0628\u0627\u0646\u064A\u0648", "bath", "bathtub")) {
      items.add(new FurniturePlacement("Bath", width * .22f, 45, 0));
    }
    if (containsAny(lower, "\u062F\u0634", "shower")) {
      items.add(new FurniturePlacement("Shower", width - 45, 45, 0));
    }
    if (containsAny(lower, "\u062D\u0648\u0636", "washbasin", "sink")) {
      items.add(new FurniturePlacement("Washbasin", width * .28f, depth - 45, 0));
    }
    if (containsAny(lower, "\u062A\u0648\u0627\u0644\u064A\u062A", "\u0645\u0631\u062D\u0627\u0636", "toilet")) {
      items.add(new FurniturePlacement("Toilet unit", width * .72f, depth - 45, 180));
    }
    if ("Bathroom".equals(roomName) && items.isEmpty()) {
      items.add(new FurniturePlacement("Bath", width * .22f, 45, 0));
      items.add(new FurniturePlacement("Washbasin", width * .25f, depth - 45, 0));
      items.add(new FurniturePlacement("Toilet unit", width * .72f, depth - 45, 180));
    }

    return new AiDesignPlan("Offline mock design parsed from your prompt", roomName,
        width, depth, 270, 10, texture, color, items);
  }

  public String toDisplayString() {
    StringBuilder result = new StringBuilder();
    result.append(this.summary).append("\n\n")
        .append("Room: ").append(this.roomName).append(" \u2014 ")
        .append(format(this.widthCm)).append(" \u00D7 ").append(format(this.depthCm)).append(" cm\n")
        .append("Walls: ").append(format(this.wallHeightCm)).append(" cm high, ")
        .append(format(this.wallThicknessCm)).append(" cm thick\n")
        .append("Floor: ").append(this.floorTextureQuery.length() > 0
            ? this.floorTextureQuery : this.floorColorHex).append("\n")
        .append("Furniture: ").append(this.furniture.size());
    for (FurniturePlacement item : this.furniture) {
      result.append("\n  \u2022 ").append(item.getCatalogQuery())
          .append(" @ ").append(format(item.getXCm())).append(", ")
          .append(format(item.getYCm())).append(" cm / ")
          .append(format(item.getAngleDegrees())).append("\u00B0");
    }
    return result.toString();
  }

  private static String format(float value) {
    return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ENGLISH, "%.1f", value);
  }

  private static boolean containsAny(String text, String... values) {
    for (String value : values) {
      if (text.indexOf(value) >= 0) {
        return true;
      }
    }
    return false;
  }

  private static String normalizeDigits(String value) {
    StringBuilder result = new StringBuilder(value.length());
    String arabic = "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669";
    String eastern = "\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9";
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      int digit = arabic.indexOf(c);
      if (digit < 0) {
        digit = eastern.indexOf(c);
      }
      result.append(digit >= 0 ? (char)('0' + digit) : c);
    }
    return result.toString();
  }

  private static float clamp(float value, float minimum, float maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static String normalizeColor(String value) {
    if (value == null || value.trim().length() == 0) {
      return "#D8C4A5";
    }
    String color = value.trim().toUpperCase(Locale.ENGLISH);
    if (!color.matches("#[0-9A-F]{6}")) {
      throw new IllegalArgumentException("room.floorColorHex must look like #D8C4A5");
    }
    return color;
  }

  private static String requiredText(String value, String name) {
    if (value == null || value.trim().length() == 0) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.trim();
  }

  private static float finite(float value, String name) {
    if (Float.isNaN(value) || Float.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
    return value;
  }

  private static float range(float value, float minimum, float maximum, String name) {
    finite(value, name);
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
    return value;
  }

  private static String text(Map<String, Object> object, String name) {
    Object value = object.get(name);
    if (!(value instanceof String)) {
      throw new IllegalArgumentException(name + " must be a string");
    }
    return (String)value;
  }

  private static String nullableText(Map<String, Object> object, String name) {
    Object value = object.get(name);
    if (value == null) {
      return "";
    }
    if (!(value instanceof String)) {
      throw new IllegalArgumentException(name + " must be a string or null");
    }
    return (String)value;
  }

  private static float number(Map<String, Object> object, String name) {
    Object value = object.get(name);
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(name + " must be a number");
    }
    return ((Number)value).floatValue();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Map<String, Object> object, String name) {
    Object value = object.get(name);
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException(name + " must be an object");
    }
    return (Map<String, Object>)value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> array(Map<String, Object> object, String name) {
    Object value = object.get(name);
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(name + " must be an array");
    }
    return (List<Object>)value;
  }
}

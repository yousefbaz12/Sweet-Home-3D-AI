package com.eteks.sweethome3d.swing.ai;

import java.util.ArrayList;
import java.util.List;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.model.Selectable;

/** Dependency-free smoke tests runnable with the bundled JDK. */
public class AiDesignPlanSelfTest {
  public static void main(String [] args) {
    AiDesignPlan arabic = AiDesignPlan.mockFromPrompt(
        "صمم حمام ٤ في ٣ متر، بلاط ريماس بيج، بانيو وحوض وتواليت");
    assertNear(400, arabic.getWidthCm(), "Arabic width");
    assertNear(300, arabic.getDepthCm(), "Arabic depth");
    assertEquals(3, arabic.getFurniture().size(), "Arabic furniture count");
    assertEquals("Rimas beige", arabic.getFloorTextureQuery(), "Texture query");
    List<String> warnings = new ArrayList<String>();
    List<Selectable> homeItems = AiDesignPanel.buildHomeItems(
        arabic, new DefaultUserPreferences(), warnings);
    assertEquals(8, homeItems.size(), "Applied home item count");

    String json = "{\"summary\":\"test\",\"room\":{\"name\":\"Bathroom\","
        + "\"widthCm\":420,\"depthCm\":310,\"wallHeightCm\":270,"
        + "\"wallThicknessCm\":10,\"floorTextureQuery\":\"Rimas beige\","
        + "\"floorColorHex\":\"#D8C4A5\"},\"furniture\":[{"
        + "\"catalogQuery\":\"Bath\",\"xCm\":100,\"yCm\":50,\"angleDegrees\":0}]}";
    AiDesignPlan parsed = AiDesignPlan.fromJson(json);
    assertNear(420, parsed.getWidthCm(), "JSON width");
    assertEquals(1, parsed.getFurniture().size(), "JSON furniture count");

    boolean rejected = false;
    try {
      AiDesignPlan.fromJson(json.replace("\"xCm\":100", "\"xCm\":900"));
    } catch (IllegalArgumentException ex) {
      rejected = true;
    }
    if (!rejected) {
      throw new AssertionError("Out-of-bounds furniture wasn't rejected");
    }
    System.out.println("AI design self-test passed");
  }

  private static void assertNear(float expected, float actual, String name) {
    if (Math.abs(expected - actual) > .01f) {
      throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }
  }

  private static void assertEquals(Object expected, Object actual, String name) {
    if (expected == null ? actual != null : !expected.equals(actual)) {
      throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }
  }
}

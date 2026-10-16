/*
 * AiDesignPanel.java
 * Prompt-to-design sidebar for the isolated El Sallab experiment.
 */
package com.eteks.sweethome3d.swing.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.TexturesCategory;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeController3D;

/**
 * Side panel that previews a validated design before applying it as one
 * compound, undoable Sweet Home 3D operation.
 */
public final class AiDesignPanel extends JPanel {
  private static final String MODE_MOCK = "Mock \u2014 local / \u0628\u062F\u0648\u0646 API";
  private static final String MODE_GROQ = "Groq \u2014 " + GroqDesignClient.DEFAULT_MODEL;

  private final Home home;
  private final UserPreferences preferences;
  private final HomeController controller;
  private final JComboBox<String> modeCombo;
  private final JTextArea promptArea;
  private final JTextArea previewArea;
  private final JButton previewButton;
  private final JButton applyButton;
  private final JLabel statusLabel;
  private AiDesignPlan pendingPlan;

  public AiDesignPanel(Home home, UserPreferences preferences, HomeController controller) {
    super(new BorderLayout(8, 8));
    this.home = home;
    this.preferences = preferences;
    this.controller = controller;
    setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    setMinimumSize(new Dimension(280, 300));
    setPreferredSize(new Dimension(345, 650));

    JLabel title = new JLabel("El Sallab AI Designer");
    title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
    JLabel subtitle = new JLabel("Prompt \u2192 validated plan \u2192 3D");
    subtitle.setForeground(new Color(90, 90, 90));
    JPanel heading = new JPanel(new GridLayout(0, 1, 0, 3));
    heading.add(title);
    heading.add(subtitle);

    this.modeCombo = new JComboBox<String>(new String [] {MODE_MOCK, MODE_GROQ});
    this.promptArea = new JTextArea(
        "\u0635\u0645\u0645 \u062D\u0645\u0627\u0645 4 \u0641\u064A 3 \u0645\u062A\u0631\u060C \u0628\u0644\u0627\u0637 \u0631\u064A\u0645\u0627\u0633 \u0628\u064A\u062C\u060C \u0628\u0627\u0646\u064A\u0648 \u0648\u062D\u0648\u0636 \u0648\u062A\u0648\u0627\u0644\u064A\u062A", 7, 24);
    this.promptArea.setLineWrap(true);
    this.promptArea.setWrapStyleWord(true);
    this.previewArea = new JTextArea();
    this.previewArea.setEditable(false);
    this.previewArea.setLineWrap(true);
    this.previewArea.setWrapStyleWord(true);
    this.previewArea.setBackground(new Color(248, 248, 248));

    JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
    inputPanel.add(this.modeCombo, BorderLayout.NORTH);
    inputPanel.add(new JScrollPane(this.promptArea), BorderLayout.CENTER);

    this.previewButton = new JButton("Preview / \u0645\u0639\u0627\u064A\u0646\u0629");
    this.applyButton = new JButton("Apply to 3D / \u062A\u0646\u0641\u064A\u0630");
    this.applyButton.setEnabled(false);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 0));
    buttons.add(this.previewButton);
    buttons.add(this.applyButton);

    this.statusLabel = new JLabel("Ready \u2014 Mock mode works fully offline");
    this.statusLabel.setForeground(new Color(70, 90, 70));
    JPanel top = new JPanel(new BorderLayout(6, 8));
    top.add(heading, BorderLayout.NORTH);
    top.add(inputPanel, BorderLayout.CENTER);
    top.add(buttons, BorderLayout.SOUTH);

    JLabel privacy = new JLabel("Groq mode sends the prompt and catalog names to Groq.");
    privacy.setFont(privacy.getFont().deriveFont(privacy.getFont().getSize2D() - 1));
    privacy.setForeground(new Color(110, 90, 70));
    JPanel bottom = new JPanel(new BorderLayout(3, 3));
    bottom.add(this.statusLabel, BorderLayout.NORTH);
    bottom.add(privacy, BorderLayout.SOUTH);

    add(top, BorderLayout.NORTH);
    add(new JScrollPane(this.previewArea), BorderLayout.CENTER);
    add(bottom, BorderLayout.SOUTH);

    this.previewButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          previewPrompt();
        }
      });
    this.applyButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          applyPendingPlan();
        }
      });
  }

  /** Wraps an existing Sweet Home 3D view with this experimental right sidebar. */
  public static JSplitPane wrap(javax.swing.JComponent mainView, Home home,
                                UserPreferences preferences, HomeController controller) {
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
        mainView, new AiDesignPanel(home, preferences, controller));
    splitPane.setContinuousLayout(true);
    splitPane.setOneTouchExpandable(true);
    splitPane.setResizeWeight(1);
    splitPane.setDividerLocation(0.72);
    return splitPane;
  }

  private void previewPrompt() {
    final String prompt = this.promptArea.getText().trim();
    if (prompt.length() == 0) {
      showError("Write a room description first / \u0627\u0643\u062A\u0628 \u0648\u0635\u0641 \u0627\u0644\u063A\u0631\u0641\u0629 \u0627\u0644\u0623\u0648\u0644");
      return;
    }
    final boolean groq = MODE_GROQ.equals(this.modeCombo.getSelectedItem());
    setBusy(true, groq ? "Asking Groq\u2026" : "Parsing locally\u2026");
    new SwingWorker<AiDesignPlan, Void>() {
        @Override
        protected AiDesignPlan doInBackground() throws Exception {
          if (groq) {
            return new GroqDesignClient().generate(prompt, buildCatalogContext());
          } else {
            return AiDesignPlan.mockFromPrompt(prompt);
          }
        }

        @Override
        protected void done() {
          try {
            pendingPlan = get();
            previewArea.setText(pendingPlan.toDisplayString()
                + "\n\nNothing has been changed yet. Press Apply to execute this plan.");
            previewArea.setCaretPosition(0);
            applyButton.setEnabled(true);
            statusLabel.setForeground(new Color(38, 110, 60));
            statusLabel.setText("Validated \u2014 ready to apply");
          } catch (Exception ex) {
            pendingPlan = null;
            applyButton.setEnabled(false);
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            showError(cause.getMessage() == null ? cause.toString() : cause.getMessage());
          } finally {
            previewButton.setEnabled(true);
            modeCombo.setEnabled(true);
          }
        }
      }.execute();
  }

  private void applyPendingPlan() {
    if (this.pendingPlan == null) {
      return;
    }
    try {
      List<String> warnings = new ArrayList<String>();
      List<Selectable> items = buildHomeItems(this.pendingPlan, this.preferences, warnings);
      this.controller.getPlanController().addItems(items);
      focusGeneratedDesign(this.pendingPlan);
      StringBuilder message = new StringBuilder(this.pendingPlan.toDisplayString());
      message.append("\n\n\u2713 Applied as one Undo/Redo operation.");
      for (String warning : warnings) {
        message.append("\n\u26A0 ").append(warning);
      }
      this.previewArea.setText(message.toString());
      this.previewArea.setCaretPosition(0);
      this.statusLabel.setForeground(new Color(38, 110, 60));
      this.statusLabel.setText("Applied \u2014 3D camera reset; use Ctrl+Z to undo");
      this.applyButton.setEnabled(false);
      this.pendingPlan = null;
    } catch (RuntimeException ex) {
      showError("Couldn't apply design: " + ex.getMessage());
    }
  }

  static List<Selectable> buildHomeItems(AiDesignPlan plan, UserPreferences preferences,
                                         List<String> warnings) {
    ArrayList<Selectable> items = new ArrayList<Selectable>();
    float width = plan.getWidthCm();
    float depth = plan.getDepthCm();
    float thickness = plan.getWallThicknessCm();
    float height = plan.getWallHeightCm();

    Wall top = new Wall(0, 0, width, 0, thickness, height);
    Wall right = new Wall(width, 0, width, depth, thickness, height);
    Wall bottom = new Wall(width, depth, 0, depth, thickness, height);
    Wall left = new Wall(0, depth, 0, 0, thickness, height);
    join(top, right, bottom, left);
    items.add(top);
    items.add(right);
    items.add(bottom);
    items.add(left);

    float inset = thickness / 2;
    Room room = new Room(new float [][] {
        {inset, inset}, {width - inset, inset},
        {width - inset, depth - inset}, {inset, depth - inset}});
    room.setName(plan.getRoomName());
    room.setFloorVisible(true);
    CatalogTexture texture = findTexture(preferences, plan.getFloorTextureQuery());
    if (texture == null && "Bathroom".equalsIgnoreCase(plan.getRoomName())) {
      texture = findElSallabTexture(preferences);
      if (texture != null && plan.getFloorTextureQuery().length() > 0) {
        warnings.add("Requested texture '" + plan.getFloorTextureQuery()
            + "' wasn't found; used El Sallab tile '" + texture.getName() + "'");
      }
    }
    if (texture != null) {
      HomeTexture homeTexture = new HomeTexture(texture);
      room.setFloorTexture(homeTexture);
      // Apply the same catalog tile to both faces so the bathroom interior is
      // visibly tiled regardless of wall drawing direction.
      top.setLeftSideTexture(homeTexture);
      top.setRightSideTexture(homeTexture);
      right.setLeftSideTexture(homeTexture);
      right.setRightSideTexture(homeTexture);
      bottom.setLeftSideTexture(homeTexture);
      bottom.setRightSideTexture(homeTexture);
      left.setLeftSideTexture(homeTexture);
      left.setRightSideTexture(homeTexture);
    } else {
      room.setFloorColor(Integer.valueOf(Color.decode(plan.getFloorColorHex()).getRGB() & 0xFFFFFF));
      if (plan.getFloorTextureQuery().length() > 0) {
        warnings.add("Texture '" + plan.getFloorTextureQuery()
            + "' isn't installed; used fallback color " + plan.getFloorColorHex());
      }
    }
    items.add(room);

    for (AiDesignPlan.FurniturePlacement placement : plan.getFurniture()) {
      CatalogPieceOfFurniture catalogPiece = findFurniture(preferences, placement.getCatalogQuery());
      if (catalogPiece == null) {
        warnings.add("Furniture '" + placement.getCatalogQuery() + "' wasn't found and was skipped");
        continue;
      }
      HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
      float angle = (float)Math.toRadians(placement.getAngleDegrees());
      piece.setAngle(angle);
      double cos = Math.abs(Math.cos(angle));
      double sin = Math.abs(Math.sin(angle));
      float halfX = (float)(piece.getWidth() * cos + piece.getDepth() * sin) / 2;
      float halfY = (float)(piece.getWidth() * sin + piece.getDepth() * cos) / 2;
      float minimumX = inset + halfX;
      float maximumX = width - inset - halfX;
      float minimumY = inset + halfY;
      float maximumY = depth - inset - halfY;
      if (minimumX > maximumX || minimumY > maximumY) {
        warnings.add("Furniture '" + placement.getCatalogQuery() + "' is larger than the room and was skipped");
        continue;
      }
      piece.setX(clamp(placement.getXCm(), minimumX, maximumX));
      piece.setY(clamp(placement.getYCm(), minimumY, maximumY));
      items.add(piece);
    }
    return items;
  }

  /** Restores an accidentally detached 3D pane and shows the result obliquely. */
  private void focusGeneratedDesign(AiDesignPlan plan) {
    HomeController3D controller3D = this.controller.getHomeController3D();
    this.controller.attachView(controller3D.getView());
    controller3D.viewFromTop();
    Camera camera = this.home.getTopCamera();
    controller3D.rotateCameraYaw((float)Math.PI - camera.getYaw());
    controller3D.rotateCameraPitch((float)Math.PI / 4 - camera.getPitch());
    controller3D.moveCamera(Math.min(plan.getWidthCm(), plan.getDepthCm()) / 10);
  }

  private static void join(Wall top, Wall right, Wall bottom, Wall left) {
    top.setWallAtStart(left);
    left.setWallAtEnd(top);
    top.setWallAtEnd(right);
    right.setWallAtStart(top);
    right.setWallAtEnd(bottom);
    bottom.setWallAtStart(right);
    bottom.setWallAtEnd(left);
    left.setWallAtStart(bottom);
  }

  private static CatalogPieceOfFurniture findFurniture(UserPreferences preferences, String query) {
    String wanted = normalizeQuery(query);
    CatalogPieceOfFurniture best = null;
    int bestScore = 0;
    for (FurnitureCategory category : preferences.getFurnitureCatalog().getCategories()) {
      for (CatalogPieceOfFurniture piece : category.getFurniture()) {
        int score = score(wanted, normalizeQuery(piece.getName()));
        if (score > bestScore) {
          best = piece;
          bestScore = score;
        }
      }
    }
    return bestScore >= 60 ? best : null;
  }

  private static CatalogTexture findTexture(UserPreferences preferences, String query) {
    String wanted = normalizeQuery(query);
    if (wanted.length() == 0) {
      return null;
    }
    CatalogTexture best = null;
    int bestScore = 0;
    for (TexturesCategory category : preferences.getTexturesCatalog().getCategories()) {
      for (CatalogTexture texture : category.getTextures()) {
        int score = Math.max(score(wanted, normalizeQuery(texture.getName())),
            score(wanted, normalizeQuery(texture.getId())));
        if (score > bestScore) {
          best = texture;
          bestScore = score;
        }
      }
    }
    return bestScore >= 45 ? best : null;
  }

  private static CatalogTexture findElSallabTexture(UserPreferences preferences) {
    for (TexturesCategory category : preferences.getTexturesCatalog().getCategories()) {
      boolean elSallabCategory = normalizeQuery(category.getName()).indexOf("elsalab") >= 0;
      for (CatalogTexture texture : category.getTextures()) {
        String id = normalizeQuery(texture.getId());
        String creator = normalizeQuery(texture.getCreator());
        if (elSallabCategory || id.indexOf("elsalab") >= 0
            || creator.indexOf("ahmed el sallab") >= 0) {
          return texture;
        }
      }
    }
    return null;
  }

  private String buildCatalogContext() {
    StringBuilder catalog = new StringBuilder("FURNITURE\n");
    int count = 0;
    for (FurnitureCategory category : this.preferences.getFurnitureCatalog().getCategories()) {
      for (CatalogPieceOfFurniture piece : category.getFurniture()) {
        if (count++ >= 160) {
          break;
        }
        catalog.append("- [").append(category.getName()).append("] ")
            .append(piece.getName()).append('\n');
      }
      if (count >= 160) {
        break;
      }
    }
    catalog.append("TEXTURES\n");
    count = 0;
    for (TexturesCategory category : this.preferences.getTexturesCatalog().getCategories()) {
      for (CatalogTexture texture : category.getTextures()) {
        if (count++ >= 160) {
          break;
        }
        catalog.append("- [").append(category.getName()).append("] ")
            .append(texture.getName());
        if (texture.getId() != null) {
          catalog.append(" (id: ").append(texture.getId()).append(')');
        }
        catalog.append('\n');
      }
      if (count >= 160) {
        break;
      }
    }
    return catalog.toString();
  }

  private static String normalizeQuery(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.toLowerCase(Locale.ENGLISH).trim()
        .replace('-', ' ').replace('_', ' ');
    if (normalized.indexOf("\u0628\u0627\u0646\u064A\u0648") >= 0) normalized = "bath";
    if (normalized.indexOf("\u062D\u0648\u0636") >= 0) normalized = "washbasin";
    if (normalized.indexOf("\u0645\u0631\u062D\u0627\u0636") >= 0 || normalized.indexOf("\u062A\u0648\u0627\u0644\u064A\u062A") >= 0) normalized = "toilet";
    if (normalized.indexOf("\u062F\u0634") >= 0) normalized = "shower";
    return normalized;
  }

  private static int score(String wanted, String candidate) {
    if (wanted.length() == 0 || candidate.length() == 0) return 0;
    if (wanted.equals(candidate)) return 100;
    if (candidate.startsWith(wanted) || wanted.startsWith(candidate)) return 85;
    if (candidate.indexOf(wanted) >= 0 || wanted.indexOf(candidate) >= 0) return 65;
    String [] words = wanted.split("\\s+");
    int matching = 0;
    for (String word : words) {
      if (word.length() >= 3 && candidate.indexOf(word) >= 0) matching++;
    }
    return words.length > 0 ? matching * 55 / words.length : 0;
  }

  private static float clamp(float value, float minimum, float maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private void setBusy(boolean busy, String message) {
    this.previewButton.setEnabled(!busy);
    this.modeCombo.setEnabled(!busy);
    this.applyButton.setEnabled(false);
    this.pendingPlan = null;
    this.statusLabel.setForeground(new Color(80, 80, 110));
    this.statusLabel.setText(message);
  }

  private void showError(String message) {
    this.previewArea.setText("Error / \u062E\u0637\u0623\n\n" + message);
    this.previewArea.setCaretPosition(0);
    this.statusLabel.setForeground(new Color(160, 45, 45));
    this.statusLabel.setText("Needs attention");
  }
}

# El Sallab AI Prompt-to-Design Lab

![Platform](https://img.shields.io/badge/platform-Windows-0078D4?logo=windows)
![Java](https://img.shields.io/badge/Java-8-ED8B00?logo=openjdk&logoColor=white)
![Sweet Home 3D](https://img.shields.io/badge/Sweet_Home_3D-5.4-4C9BD6)
![AI](https://img.shields.io/badge/AI-Groq_optional-F55036)
![Status](https://img.shields.io/badge/status-working_MVP-brightgreen)

An isolated proof of concept that turns an Arabic or English room description into a validated,
editable Sweet Home 3D project. It combines a local Ahmed El Sallab product pipeline, importable
tile and furniture catalogs, and an AI sidebar that converts prompts into real 2D/3D home objects.

The experiment is intentionally separated from the main application and from the upstream Sweet
Home 3D source. A clean upstream snapshot is fetched during setup, then the tracked El Sallab source
overlay is applied locally. Generated binaries, downloaded tools, API keys, and website images are
not committed.

> Current scope: a working vertical slice for a rectangular bathroom or room. This is not yet a
> general-purpose apartment designer or a production configurator.

## Why this exists

Instead of asking a customer to learn a floor-plan editor, the target experience is:

```text
Customer prompt + dimensions + style
                 │
                 ▼
       Validated design plan
                 │
      ┌──────────┴──────────┐
      ▼                     ▼
El Sallab catalog      Geometry rules
      │                     │
      └──────────┬──────────┘
                 ▼
      Editable 2D + 3D design
                 │
                 ▼
 Product list, quantities and export
```

The AI never writes Sweet Home 3D XML directly. It proposes a restricted JSON plan; local Java code
validates every dimension, color, coordinate, count, and catalog lookup before creating model
objects. Sweet Home 3D remains responsible for saving `.sh3d` projects.

## Working features

### Prompt-to-design sidebar

- Arabic and English room prompts.
- Offline Mock mode for repeatable demos without any API or internet connection.
- Optional Groq mode using strict JSON Schema Structured Outputs.
- Preview-before-apply workflow: the generated plan is visible before it changes the project.
- Rectangular room generation with validated width, depth, wall height, and wall thickness.
- Four joined walls and a named floor area.
- Up to 24 catalog furniture placements.
- Furniture-boundary clamping and rejection of pieces that cannot fit.
- One compound operation, so a single `Ctrl+Z` / `Ctrl+Y` removes or restores the design.
- Automatic return from a detached 3D window and a useful oblique camera angle after generation.
- The complete AI feature can be disabled with `-Dsweethome3d.ai.disabled=true`.

### Ahmed El Sallab catalog proof of concept

- Focused category plan for ceramic, porcelain, bathroom, kitchen, mixers and sanitary products.
- Reviewable JSON and CSV output.
- A small, rate-limited sample scraper instead of an uncontrolled full-site crawl.
- Downloaded tile images converted into a Sweet Home 3D `.sh3t` texture library.
- Product records packaged as a `.sh3f` catalog when a model or proxy is available.
- Stable SKU/catalog IDs that can later join designs with ERP, pricing or manufacturing data.
- Local runtime catalogs are loaded automatically by the Groq launcher.
- Bathroom floor and wall surfaces use the selected El Sallab tile, with a catalog fallback.

The current reviewed sample contains 20 product records across 6 focused categories and 10 texture
entries. It is a functional test dataset, not a complete commercial catalog.

### Local and reproducible development

- Windows bootstrap for the pinned upstream source, JDK 8 and Apache Ant.
- No machine-wide Java or Ant installation is required by the Windows workflow.
- Source, tools, runtime data and generated distributions remain inside this lab.
- Furniture Library Editor and Textures Library Editor are included in the build workflow.
- Ubuntu build scripts are prepared for a future graphical Linux environment.
- Dependency-free AI plan smoke test.

## Windows quick start

### Requirements

- 64-bit Windows 10 or 11.
- Git for Windows.
- Internet access for the first source/tool download and for refreshing the product sample.
- A working graphics driver for Java 3D / OpenGL.

From a fresh repository clone, open this folder and run:

```text
BOOTSTRAP-WINDOWS.cmd
```

This downloads the pinned development tools, fetches only the compatible Sweet Home 3D modules,
applies the tracked AI overlay, builds the desktop programs, and packages the starter catalogs.

To refresh the Ahmed El Sallab evaluation sample and rebuild its libraries:

```text
UPDATE-ELSALLAB-SAMPLE.cmd
```

To run fully offline:

```text
START-SWEET-HOME-3D.cmd
```

Choose `Mock — local / بدون API` in the right sidebar.

## Optional Groq setup

Copy `.env.local.example` to `.env.local` and add a newly generated key:

```dotenv
GROQ_API_KEY=gsk_replace_with_your_new_key
GROQ_MODEL=openai/gpt-oss-120b
```

Then run:

```text
START-WITH-GROQ.cmd
```

`.env.local` is excluded from Git. Never put a real key in source code, a `.cmd` file, a screenshot,
or an issue. Revoke a key immediately if it is exposed.

Groq mode sends the prompt and a bounded list of local catalog names/IDs to Groq. It does not send
the `.sh3d` project, local product images, or the API key as prompt content.

## Five-minute acceptance test

1. Start the application and choose **File > New**.
2. Select **Mock — local / بدون API** or the configured Groq mode.
3. Enter:

   ```text
   صمم حمام مقاس 4 × 5 متر، واستخدم بلاط أرضيات وحوائط من منتجات أحمد السلاب،
   وأضف بانيو وحوض ومرحاض ودش مع توزيع مناسب.
   ```

4. Press **Preview / معاينة** and confirm the plan says `400 × 500 cm`.
5. Press **Apply to 3D / تنفيذ**.
6. Confirm that the 2D plan and oblique 3D view show walls, a tiled floor/walls, and the available
   bathroom pieces.
7. Press `Ctrl+Z` once, then `Ctrl+Y` once, to verify compound Undo/Redo.
8. Save the project, close it, and reopen it to verify persistence.

See [WINDOWS-TEST-CHECKLIST.md](WINDOWS-TEST-CHECKLIST.md) for the full manual test and
[AI-PROMPT-TO-DESIGN.md](AI-PROMPT-TO-DESIGN.md) for implementation and security details.

## Repository layout

```text
sweethome3d-lab/
├── overlays/SweetHome3D/       Tracked AI Java source layered over upstream
├── patches/                    Small reproducible upstream build fixes
├── custom-catalogs/            Catalog metadata and redistributable demo assets
├── manufacturing/              Sidecar product/BOM schema keyed by SKU
├── scraping/                   Focused category plan and sample scraper
├── scraped-data/               Reviewable JSON/CSV output; remote images ignored
├── windows/                    Bootstrap, build, packaging and launch scripts
├── ubuntu/                     Future JDK 8 build/run scripts
├── source/                     Generated upstream checkout; ignored
├── tools/                      Downloaded JDK/Ant; ignored
├── runtime-data/               Local Sweet Home 3D catalogs/state; ignored
└── dist/                       Generated SH3D/SH3F/SH3T artifacts; ignored
```

Important implementation files in the overlay:

- `AiDesignPanel.java`: sidebar, catalog matching, model creation and camera recovery.
- `AiDesignPlan.java`: validated design language and offline parser.
- `GroqDesignClient.java`: strict schema request and response parsing.
- `MiniJson.java`: dependency-free JSON parser for the legacy Java baseline.
- `patches/home-pane-ai-sidebar.patch`: the minimal upstream UI integration point.
- `AiDesignPlanSelfTest.java`: plan validation and model-building smoke test.

## Adding products

### Tiles and finishes

1. Add a seamless JPG/PNG texture to `custom-catalogs/textures/images/` or refresh the scraper.
2. Add its stable ID, display name, category, real repeat width and height to
   `PluginTexturesCatalog.properties`.
3. Run `REBUILD-AND-PACKAGE.cmd`.
4. The resulting `.sh3t` library is written to `dist/` and loaded by the local launcher.

### Furniture and sanitary products

1. Add a licensed `.obj`, `.dae`, `.3ds`, `.lws` or `.kmz` model and its companion files.
2. Add a PNG preview and a numbered catalog entry with a permanent SKU-like ID.
3. Keep pricing, finish, lead time, BOM and manufacturing fields in the sidecar CSV keyed by that ID.
4. Rebuild the `.sh3f` library.

Exact Ahmed El Sallab products cannot appear as faithful 3D objects until licensed models are
available. During development, clearly labelled parametric proxy models are the safest alternative.

## Roadmap

### P0 — harden the current MVP

- Automated catalog-loading and prompt-to-project integration tests.
- Friendly in-app error details and local diagnostic logs with secret redaction.
- Exact selected SKU/texture shown in the preview and placed-object metadata.
- Better Arabic normalization, units, dimension ambiguity handling and prompt examples.
- Before/after screenshots and repeatable visual regression scenes.

### P1 — better rooms and layout rules

- Doors, windows, openings and wall segments.
- L-shaped and polygonal rooms, then multiple connected rooms.
- Collision detection, walkable clearances and door-swing constraints.
- Bathroom plumbing zones, wet/dry zones and accessibility rules.
- Layout alternatives such as “budget”, “premium”, “more storage” and “wheelchair accessible”.

### P2 — product digital twins

- SKU-to-3D-model mapping with dimensions, materials, finish variants and model provenance.
- Parametric proxy generator for products without manufacturer CAD.
- Automated model optimization, thumbnails and level-of-detail variants.
- Separate wall/floor tile selection, grout width/color, rotation and laying pattern.
- Product substitutions when an item is unavailable or does not fit.

### P3 — conversational editing

- Follow-up commands such as “move the basin left”, “replace the tile” or “make the door wider”.
- Selection-aware edits instead of always generating a new room.
- Design history, named alternatives and side-by-side comparison.
- Retrieval/reranking over the complete local product catalog.
- Explanations of why each item was selected and which constraints affected the layout.

### P4 — quantities and commerce

- Tile coverage, cut/waste percentage, boxes required and grout estimates.
- Bill of quantities with SKU, quantity, unit price, VAT and project total.
- Live availability and price synchronization through an approved product API or ERP export.
- Budget limits, product bundles, saved carts and quotation export.
- Manufacturing/BOM and lead-time integration for made-to-order products.

### P5 — web and deployment

- Browser viewer based on SweetHome3DJS or a modern glTF/WebGL pipeline.
- Export to `.sh3d`, XML, OBJ/glTF, images and shareable project links.
- Local AI gateway so the desktop application never contains provider credentials.
- Ubuntu build service for catalog generation and exports.
- GPU-backed remote rendering or streamed interactive sessions where appropriate.

### P6 — production controls

- Projects, users, roles and customer/design permissions.
- Audit trail for prompts, catalog versions and generated changes.
- Consent, retention and deletion rules for customer room data.
- Evaluation dataset for dimensional accuracy, placement validity and product-match quality.
- CI/CD, signed releases, dependency scanning, backups and disaster recovery.

## Known limitations

- The pinned mirror is Sweet Home 3D 5.4 from 2017 and requires JDK 8.
- A desktop 3D view needs a graphical session and working OpenGL; a headless Ubuntu server cannot
  display the editor by itself.
- The current AI generator creates a new rectangular room and does not yet edit arbitrary geometry.
- Product imagery and text collected from a retailer site may have usage restrictions. Remote images,
  generated libraries and downloaded site assets are intentionally excluded from Git.
- Scraping must respect the site's terms, robots policy, rate limits and applicable law before any
  production-scale collection.
- AI output remains advisory and must be reviewed before purchasing, manufacturing or construction.

## Upstream and licensing

The source bootstrap pins `ralic/sweethome-3d` commit
`179a9a91a9fa9f2aff13b16cdc2e669885402af2` (Sweet Home 3D 5.4). Sweet Home 3D and files derived
from it remain subject to their upstream GPL notice. No license has yet been selected for the
original El Sallab scripts and documentation; choose an organization-approved license before public
redistribution.

Product names, images and trademarks belong to their respective owners and are not relicensed by
this repository.

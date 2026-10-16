# El Sallab prompt-to-design experiment

## What works now

The isolated Sweet Home 3D build has a right-hand **El Sallab AI Designer** sidebar. It turns an
Arabic or English prompt into a deliberately restricted design plan, validates it, previews it,
and only changes the home after **Apply to 3D** is pressed.

The current vertical slice supports:

- one rectangular room with validated width, depth, wall height, and wall thickness;
- four joined walls and a named floor area;
- an installed Sweet Home 3D texture selected by name or catalog ID, with a color fallback;
- up to 24 furniture placements selected from the installed catalog;
- furniture-boundary clamping and skipping pieces that cannot fit;
- one compound operation, so `Ctrl+Z` removes the whole generated design and `Ctrl+Y` restores it;
- a fully local Mock mode for repeatable testing without an API key;
- Groq Structured Outputs with a locally revalidated JSON plan.

The AI does **not** write `.sh3d` XML directly. XML remains Sweet Home 3D's persistence layer;
the model only proposes a small JSON plan and application code creates the model objects. This is
the important safety boundary for reliable prompt-to-design.

## Security first

Create a dedicated development key and never put it in a source file, `.cmd`, screenshot, or Git
commit. Revoke it immediately if it is ever exposed. This experiment reads the key only from the
local `.env.local` launcher environment.

For one PowerShell session:

```powershell
$env:GROQ_API_KEY = Read-Host 'Paste the NEW Groq key'
$env:GROQ_MODEL = 'openai/gpt-oss-120b'
.\START-SWEET-HOME-3D.cmd
```

Closing that PowerShell window clears the session variable. `GROQ_BASE_URL` may be set for a
compatible local gateway later; otherwise it defaults to Groq's official API.

## Five-minute offline acceptance test

1. Double-click `START-SWEET-HOME-3D.cmd`.
2. Choose **File > New** so the test starts on an empty home.
3. In the right sidebar keep **Mock — local / بدون API** selected.
4. Paste: `صمم حمام ٤ في ٣ متر، بلاط ريماس بيج، بانيو وحوض وتواليت`.
5. Press **Preview / معاينة**. Confirm the preview says `400 × 300 cm`, three furniture items,
   and `Rimas beige`.
6. Press **Apply to 3D / تنفيذ**. Confirm the 2D plan shows a rectangular room and the 3D view
   shows its walls, floor, and available bathroom models.
7. Press `Ctrl+Z` once. All generated walls, room, and furniture must disappear together.
8. Press `Ctrl+Y` once. The complete generated design must return together.
9. Save as `dist\ai-bathroom-mock.sh3d`, close it, and reopen it to verify persistence.

If the El Sallab texture library is not already installed, use **Furniture > Import textures
library** and select `dist\elsalab-textures.sh3t`, then repeat the test. If the exact texture is not
installed, the sidebar explicitly reports that it used the fallback color.

## Groq acceptance test

After setting a new `GROQ_API_KEY` and launching from the same PowerShell session:

1. Start with a new empty home.
2. Select **Groq — openai/gpt-oss-120b**.
3. Use the same Arabic prompt and press **Preview**.
4. Check dimensions, floor choice, and furniture positions before pressing **Apply**.
5. Repeat the single-step Undo/Redo check.

Groq mode sends the entered prompt and the names/IDs of up to 160 local furniture items and 160
local textures to Groq. It does not send the `.sh3d` file or the API key as prompt content.

## Model choice

The default is `openai/gpt-oss-120b`: Groq lists it as a production model and supports strict JSON
Schema Structured Outputs. Strict output is still validated locally for room bounds, counts,
colors, and finite numeric values before it can be applied. Change `GROQ_MODEL` only to another
Groq model that supports strict Structured Outputs with the same schema.

## Files to continue from

- UI integration: `source/SweetHome3D/src/com/eteks/sweethome3d/swing/HomePane.java`
- sidebar and deterministic application: `source/SweetHome3D/src/com/eteks/sweethome3d/swing/ai/AiDesignPanel.java`
- validated design language and offline parser: `source/SweetHome3D/src/com/eteks/sweethome3d/swing/ai/AiDesignPlan.java`
- Groq client and JSON Schema: `source/SweetHome3D/src/com/eteks/sweethome3d/swing/ai/GroqDesignClient.java`
- dependency-free JSON parser: `source/SweetHome3D/src/com/eteks/sweethome3d/swing/ai/MiniJson.java`
- smoke test: `source/SweetHome3D/test/com/eteks/sweethome3d/swing/ai/AiDesignPlanSelfTest.java`
- Sweet Home project XML: `source/SweetHome3D/src/com/eteks/sweethome3d/io/HomeXMLExporter.java`
  and `HomeXMLHandler.java`

Set `-Dsweethome3d.ai.disabled=true` when launching Java to show the untouched original layout.

## Honest current boundary

This is a working end-to-end MVP, not yet a general apartment designer. It creates a new rectangular
room rather than editing arbitrary existing geometry, and it can only place products that already
have a Sweet Home 3D 3D model. Scraped tiles can work as textures immediately. Ahmed El Sallab
bathroom and kitchen products still need licensed `.obj`/`.dae`/`.3ds` models (or parametric proxy
models) before the AI can place the exact SKU in 3D. The next reliable expansion is multi-room
geometry, doors/windows, collision rules, and an explicit product-SKU-to-model mapping.
# Local Groq key on Windows

1. Copy `.env.local.example` to `.env.local`.
2. Put a newly generated key in `GROQ_API_KEY`.
3. Double-click `START-WITH-GROQ.cmd`.

The real `.env.local` file is ignored by Git and should never be shared.

`START-WITH-GROQ.cmd` also prepares an isolated `runtime-data` folder and
loads the generated El Sallab textures / furniture libraries on every start.
After applying a design, the 3D pane is reattached and its camera is reset to
an oblique aerial view. Bathroom floor and wall surfaces use the selected El
Sallab catalog tile (or the first local El Sallab tile as a fallback).


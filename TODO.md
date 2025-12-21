# Garden App TODO

## Bugs / Issues Found

### LLM Tools

- [x] Missing `remove_plant` tool - Can't move/delete individual plants, LLM had to add duplicate lettuce instead of moving
- [x] Missing `remove_plants_in_area` tool - Can't clear all plants from a specific area
- [x] Limited plant species for flowers - Only sunflowers available, no roses/tulips/etc in plant database
- [x] Add zoom tool

### REPL

- [ ] Shadow-cljs REPL alias bug - `require :as` aliases don't persist between evals, need full namespace paths like `garden.llm/send-message!`

### Chat Behavior

- [ ] Clear garden didn't fully work - When asked to "clear and design", old plants remained initially

### UI Issues (from dogfooding)

- [x] Area type dropdown not responding - Can't change area type from "Garden Bed" to other types via UI
- [x] No "Clear All" button - Reset only resets the view, not the garden content
- [x] Mass plant placement is tedious - Added "Scatter" tool to UI for mass plant placement
- [x] Paths rendered as thin lines - Should be thicker/more visible areas
- [x] No undo/redo buttons visible in toolbar
- [x] Properties panel shows wrong type after programmatic change (shows "Garden Bed" even when area is water)

### Recreating from Reference Map (from dogfooding)

- [x] No reference image overlay - Added "Ref Image" button to load and overlay reference images
- [x] No way to move/reposition existing areas - Select tool allows dragging areas and vertices
- [x] No spatial planning grid with measurements - Added optional measurement labels to grid (toggle with "Labels" button)
- [ ] Initial placement errors are hard to fix - Need to delete and redraw
- [ ] No "import layout" feature - Would help recreate known gardens

## Completed

- [x] Added `remove_plant` tool - allows removing individual plants by ID
- [x] Added `remove_plants_in_area` tool - allows removing all plants in a rectangular area
- [x] Added 4 new flower species: Rose, Tulip, Lavender, Marigold
- [x] Added 10 Japanese garden plants: Cherry Blossom, Japanese Maple, Japanese Black Pine, Plum Tree, Bamboo, Azalea, Japanese Iris, Moss, Wisteria, Camellia
- [x] Successfully created Kenroku-en inspired garden via chat assistant
- [x] Added `scatter_plants` tool - randomly scatter many plants in an area for dense vegetation
- [x] Added `add_path` tool - create winding paths from waypoints
- [x] Added `add_water` tool - create water features (ponds, streams, lakes)
- [x] Recreated dense Kenroku-en garden with 725 plants using new tools
- [x] Performance optimization - render time reduced from ~500ms to ~5ms via:
  - Viewport culling for plants and areas (only render visible items)
  - Level-of-detail (LOD) for background, areas, plants, and grid
  - Simplified plant rendering (circles) when zoomed out
  - Skip textures (soil, stones, wood, grass) when zoomed out
- [x] Fixed area type dropdown - added "water" option and explicit handler
- [x] Fixed path rendering - thin paths now rendered as thick strokes, water features have ripple texture
- [x] Fixed properties panel sync - panel now re-reads area from state on each render
- [x] Added `set_zoom` and `pan_to` LLM tools - allows assistant to control viewport
- [x] Added "Clear All" button to toolbar with confirmation dialog
- [x] Added undo/redo functionality with history tracking (up to 50 states)
- [x] Added measurement labels to grid (optional, toggle with "Labels" button)
- [x] Added reference image overlay feature (load via "Ref Image" button, adjust opacity)
- [x] Added "Scatter" tool for mass plant placement via UI (draw rectangle to scatter plants)

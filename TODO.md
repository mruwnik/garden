# Garden App TODO

## Critical Architecture Issues

### State Management

- [ ] **Add input validation with malli/spec** — `state.cljs:276-280`
  - `add-area!`, `add-plant!` accept any map with no schema validation
  - Can create areas with <3 points (invalid polygons)
  - Can place plants with invalid positions
  - Selection can reference non-existent IDs after deletion

- [ ] **Fix history save order** — `state.cljs:276-280`
  - `save-history!` is called BEFORE mutation
  - If mutation throws, history is already polluted with pre-mutation state
  - Should: try mutation, then save history; or use transaction wrapper

- [ ] **Add spatial indexing for O(1) lookups** — `state.cljs:152-168`
  - `find-area`, `find-plant`, `find-plant-at` are O(n) linear scans
  - Called on every mouse move during selection/hover
  - Use `{id -> entity}` map alongside vector, or quadtree for spatial queries

### Performance

- [ ] **O(n) plant hit detection on every mouse move** — `tools/select.cljs:80-88`
  - `find-plant-at` scans all plants on every mouse move
  - With 1000+ plants, causes jank
  - Solution: quadtree, R-tree, or grid-based spatial bucketing

- [ ] **Cache contour line calculations** — `canvas/topo.cljs:411-479`
  - Marching squares runs on EVERY render when contours visible
  - Should cache and only recompute when elevation data or interval changes

- [ ] **O(n) area lookups during drag** — `tools/select.cljs:200-206`
  - `find-area` called on each drag tick
  - Compounds with multiple selected items

## Code Organization

### Monolithic Files

- [ ] **Split render.cljs (1000+ lines)** — `canvas/render.cljs`
  - Handles background, areas, plants, selection, tooltips, tool overlays
  - Split into: `render/background.cljs`, `render/areas.cljs`, `render/plants.cljs`, `render/selection.cljs`, `render/overlays.cljs`

- [ ] **Split fill.cljs (640+ lines)** — `tools/fill.cljs`
  - Mixes flood fill, contour tracing, RDP simplification, hole detection
  - Split algorithms into `util/flood-fill.cljs`, `util/contour-trace.cljs`

### Duplicated Logic

- [ ] **Centralize coordinate conversion** — scattered across files
  - `canvas->image-coords` duplicated in `fill.cljs`, `render.cljs`, `reference.cljs`
  - The "150 pixels = bar-meters" conversion repeated in 4+ places
  - Create `coordinates.cljs` namespace with unified conversion functions

- [ ] **Magic numbers should be constants** — various files
  - `bar-px 150` — hardcoded in multiple files
  - `3000` ms debounce — `canvas/topo.cljs:36`
  - `2048` max cache dimensions — `canvas/topo.cljs:39`
  - `12` vertex-hit-radius — `tools/select.cljs:19`

### Tool System

- [ ] **Auto-discover tools instead of manual require** — `ui/app.cljs:28-36`
  - Tools must be explicitly `:require`d to register
  - If you forget, tool silently doesn't exist
  - Use macro or explicit central registration

- [ ] **Isolate tool state properly** — `state.cljs:40-42`
  - All tools share single `[:tool :state]` bucket
  - Tools can't persist settings across deactivation
  - Consider per-tool state namespaced by tool-id

## Security & Robustness

### LLM Integration

- [ ] **Don't store API key in localStorage unencrypted** — `llm.cljs:23-24`
  - Readable via DevTools
  - For production: use backend proxy or at minimum obfuscate

- [ ] **Tool definitions duplicate execution logic** — `llm.cljs:84-234`
  - 150 lines of hand-written JSON schemas
  - Will drift from actual `execute-*` function signatures
  - Generate schemas from execution functions

### Error Handling

- [ ] **Silent failures in fill tool** — `tools/fill.cljs:571-574`
  - If `do-fill-work!` throws, loading state cleared but user gets no feedback
  - Add error reporting to UI

- [ ] **NaN values silently dropped** — `topo/core.cljs:30-31`
  - Callers don't know if `nil` means out-of-bounds or corrupted data
  - Return tagged union or error info

### Memory Management

- [ ] **Temporary canvases in hot paths** — `tools/fill.cljs:70-71, 100-101`
  - Multiple `createElement("canvas")` per fill operation
  - Should reuse or explicitly clean up

- [ ] **Render watcher never removed** — `canvas/core.cljs:141-144`
  - `defonce` means hot-reload accumulates watchers
  - Add cleanup mechanism for dev mode

## Naming & Consistency

- [ ] **Inconsistent naming conventions**
  - `:species-id` vs `species_id` (Clojure vs JSON style mixed)
  - `:bar-meters` — unclear what bar; use `:scale-bar-meters`

- [ ] **Unused requires** — various files
  - Run `clj-kondo` to clean up

- [ ] **No JSDoc/type hints for interop** — canvas, Three.js, GeoTIFF code
  - Add `^js` hints and parameter documentation

## Testing

- [ ] **Missing critical algorithm tests**
  - Flood fill edge cases (fill.cljs)
  - Contour tracing (fill.cljs)
  - RDP simplification (fill.cljs)
  - Hole detection (fill.cljs)
  - Select tool hit detection (select.cljs)

- [ ] **No integration tests**
  - Tool activation/deactivation lifecycle
  - Undo/redo with complex operations
  - Canvas rendering pipeline

- [ ] **No performance benchmarks**
  - Plant rendering at scale (1000+)
  - Topo overlay at various resolutions
  - Flood fill on large images

## Rendering

- [ ] **Proper contours** (original TODO item)

- [ ] **Canvas2D performance ceiling**
  - Consider WebGL for >10k objects
  - Current approach won't scale for large gardens

- [ ] **LOD system inconsistent**
  - Background texture has LOD (zoom > 0.3)
  - Plant rendering has LOD (zoom < 0.2)
  - Contours have LOD (zoom < 0.2)
  - Unify LOD thresholds and strategy

## 3D View

- [ ] **Three.js resources not fully cleaned** — `canvas/terrain3d.cljs:418-440`
  - `dispose-scene!` cleans most, but event listeners can leak
  - ResizeObserver cleanup is handled, but verify no edge cases

- [ ] **Camera state duplicated** — `terrain3d.cljs` vs `state.cljs`
  - `scene-state` atom duplicates what could be in app-state
  - Harder to debug, serialize, or persist

## Water Simulation

- [ ] **Tight coupling between simulation and rendering**
  - Physics recalculated based on graphics resolution
  - Should be independent with configurable sync

## LLM Tool Execution Issues

- [ ] **LLM batch operations don't batch history** — `llm.cljs:259-272, 313-346`
  - `execute-add-plants-row` and `execute-scatter-plants` create history per plant
  - AI adding 50 plants = 50 undo operations
  - Should use `add-plants-batch!` or wrap in single history save

- [ ] **Duplicate scatter logic** — `llm.cljs:313-346` vs `tools/scatter.cljs:37-58`
  - Same jitter calculation repeated
  - Extract to shared `util/scatter.cljs`

- [ ] **execute-clear-garden! same bug as toolbar** — `llm.cljs:284-289`
  - Each remove creates history entry
  - Calling "clear garden" via AI = hundreds of history entries

## Component Issues

- [ ] **close-threshold uses screen pixels not canvas units** — `tools/area.cljs:19`
  - `(def close-threshold 15)` is in pixels
  - Should scale with zoom like other hit detection

- [ ] **on-deactivate auto-saves incomplete areas** — `tools/area.cljs:52-57`
  - Switching tools with 3+ points auto-creates area
  - User may not want this behavior
  - Should confirm or discard

- [ ] **Properties panel doesn't debounce updates** — `ui/panels/properties.cljs:69-91`
  - Every keystroke in name/notes creates history + state update
  - Type "Garden Bed" = 10 history entries
  - Debounce text input or use local state then commit

- [ ] **Multi-delete doesn't batch** — `ui/panels/properties.cljs:173-181`
  - Same bug: each remove is separate history entry

## API & Async Issues

- [ ] **GeoTIFF loading has no error display to user** — `topo/geotiff.cljs:297-303, 368-370`
  - `.catch` logs to console but doesn't show user what went wrong
  - Should display error in UI

- [ ] **No timeout on GeoTIFF fetch** — `topo/geotiff.cljs:315`
  - Large files over slow connections could hang forever
  - Add timeout with AbortController

- [ ] **Promise chains not using async/await** — throughout
  - Deep `.then` nesting is hard to read/debug
  - Consider `promesa` library or `core.async` for cleaner async

## Minor Issues

- [ ] **Zoom buttons use hardcoded center point** — `ui/toolbar.cljs:62-67`
  - `state/zoom-at! [400 300]` assumes viewport size
  - Should use actual viewport center

- [ ] **Clear All button doesn't batch history** — `ui/toolbar.cljs:150-158`
  - Each `remove-area!` and `remove-plant!` creates separate history entry
  - Undo requires many clicks to restore
  - Should batch into single history entry

- [ ] **Tool button uses label as display** — `ui/toolbar.cljs:20-30`
  - No icons, just text
  - Consider adding icon support

- [ ] **sample-plants accessed directly in scatter** — `tools/scatter.cljs:41`
  - `library/sample-plants` is global mutable state
  - Should go through state accessor

- [ ] **Hardcoded default species fallback** — `tools/scatter.cljs:90`
  - `(first (map :id library/sample-plants))` could be nil
  - Should handle empty library case

- [ ] **Chat local-state is defonce** — `ui/panels/chat.cljs:16-19`
  - Hot reload won't reset pending images
  - Use `r/atom` initialized in component if needed

- [ ] **sample-plants is hardcoded, not in state** — `ui/panels/library.cljs:17-144`
  - 144 lines of static data in code
  - Should be in state, loaded from JSON, or configurable
  - Makes testing and customization difficult

- [ ] **area-types duplicated** — `ui/panels/properties.cljs:15-26` vs `ui/panels/library.cljs:217-227`
  - Same exact map defined twice
  - Extract to `data/area-types.cljs` or similar

- [ ] **Water cache creates new canvas every frame** — `canvas/water.cljs:34-36`
  - `js/document.createElement "canvas"` in render loop
  - Should reuse cached canvas

- [ ] **Plant search uses runtime regex construction** — `ui/panels/library.cljs:448-451`
  - `(re-pattern (str "(?i)" search-term))` on every keystroke
  - Invalid regex characters could crash
  - Should escape input or use simple `lower-case` + `includes?`

---

## Priority Order

### P0 — Correctness Bugs
1. **Fix history save order** — mutation should happen before history save
2. **Batch history for bulk operations** — toolbar clear, AI scatter, properties panel
3. **Properties panel debounce** — every keystroke creates history entry

### P1 — Performance
4. **Add spatial indexing** — O(n) plant hit detection is unacceptable
5. **Cache contour lines** — marching squares on every render is expensive
6. **Reuse water cache canvas** — creating DOM elements in render loop
7. **Memoize plant library search** — regex construction on every keystroke

### P2 — Architecture
8. **Add input validation (malli/spec)** — prevent corrupted state
9. **Split render.cljs** — 1000+ line file is unmaintainable
10. **Centralize coordinate conversion** — duplicated magic numbers
11. **Extract shared data (area-types, sample-plants)** — duplicated definitions

### P3 — Robustness
12. **Add error reporting to UI** — silent failures in fill, geotiff
13. **Add timeout to async operations** — geotiff fetch can hang
14. **Handle empty/nil edge cases** — empty library, missing IDs

### P4 — Testing
15. **Test flood fill algorithm** — complex, untested
16. **Test geometry utilities edge cases** — used everywhere
17. **Add performance benchmarks** — catch regressions

# Garden App TODO

## Critical Architecture Issues

### State Management

- [ ] **Add input validation with malli/spec** — `state.cljs:276-280`
  - `add-area!`, `add-plant!` accept any map with no schema validation
  - ~~Can create areas with <3 points (invalid polygons)~~ FIXED: `add-area!` now validates ≥3 points
  - Can place plants with invalid positions
  - Selection can reference non-existent IDs after deletion

- [x] **Fix history save order** — `state.cljs:276-280`
  - ~~`save-history!` is called BEFORE mutation~~
  - FIXED: Created `with-history*` wrapper that captures state, runs mutation, then saves
  - Also added `with-batch-history` for bulk operations

- [ ] **Add spatial indexing for O(1) lookups** — `state.cljs:152-168`
  - `find-area`, `find-plant`, `find-plant-at` are O(n) linear scans
  - Called on every mouse move during selection/hover
  - Use `{id -> entity}` map alongside vector, or quadtree for spatial queries

### Performance

- [ ] **O(n) plant hit detection on every mouse move** — `tools/select.cljs:80-88`
  - `find-plant-at` scans all plants on every mouse move
  - With 1000+ plants, causes jank
  - Solution: quadtree, R-tree, or grid-based spatial bucketing

- [x] **Cache contour line calculations** — `canvas/topo.cljs:411-479`
  - ~~Marching squares runs on EVERY render when contours visible~~
  - FIXED: Added contour-cache atom with segments, interval, data-hash, lod-factor
  - Only recomputes when elevation data, interval, or LOD changes

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

- [x] **Magic numbers should be constants** — various files
  - FIXED: Created `garden.constants` namespace with shared constants:
    - `bar-image-pixels` (150) - used in fill.cljs, state.cljs, reference.cljs, ground.cljs
    - `default-bar-meters` (50) - default scale bar length
    - `vertex-hit-radius` (12) - used in select.cljs
    - `point-hit-radius` (15) - used in elevation_point.cljs
    - `topo-cache-debounce-ms` (3000) and `max-cache-dimension` (2048) - defined but not yet used
  - Remaining: topo.cljs still has local constants (low priority)

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

- [x] **NaN values silently dropped** — `topo/core.cljs:30-31`
  - ~~Callers don't know if `nil` means out-of-bounds or corrupted data~~
  - FIXED: Added one-time console warning when NaN values are encountered

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

- [x] **LLM batch operations don't batch history** — `llm.cljs:259-272, 313-346`
  - ~~`execute-add-plants-row` and `execute-scatter-plants` create history per plant~~
  - FIXED: Now uses `add-plants-batch!` for all LLM plant additions

- [x] **Duplicate scatter logic** — `llm.cljs:313-346` vs `tools/scatter.cljs:37-58`
  - ~~Same jitter calculation repeated~~
  - FIXED: llm.cljs now imports and uses `scatter/calculate-grid-dimensions` and `scatter/generate-grid-positions`

- [x] **execute-clear-garden! same bug as toolbar** — `llm.cljs:284-289`
  - ~~Each remove creates history entry~~
  - FIXED: Now uses `state/clear-all!` for single undo operation

## Component Issues

- [x] **close-threshold uses screen pixels not canvas units** — `tools/area.cljs:19`
  - ~~`(def close-threshold 15)` is in pixels~~
  - FIXED: Renamed to `close-threshold-px` and now divides by zoom for consistent screen-space feel

- [ ] **on-deactivate auto-saves incomplete areas** — `tools/area.cljs:52-57`
  - Switching tools with 3+ points auto-creates area
  - User may not want this behavior
  - Should confirm or discard

- [x] **Properties panel doesn't debounce updates** — `ui/panels/properties.cljs:69-91`
  - ~~Every keystroke in name/notes creates history + state update~~
  - FIXED: Created `debounced-text-input` and `debounced-textarea-input` components
  - Uses local reagent atom + 500ms debounce + commit on blur

- [x] **Multi-delete doesn't batch** — `ui/panels/properties.cljs:173-181`
  - ~~Same bug: each remove is separate history entry~~
  - FIXED: Now uses `remove-areas-batch!` and `remove-plants-batch!`

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

- [x] **Zoom buttons use hardcoded center point** — `ui/toolbar.cljs:62-67`
  - ~~`state/zoom-at! [400 300]` assumes viewport size~~
  - FIXED: Now reads viewport size and calculates center dynamically

- [x] **Clear All button doesn't batch history** — `ui/toolbar.cljs:150-158`
  - ~~Each `remove-area!` and `remove-plant!` creates separate history entry~~
  - FIXED: Now uses `state/clear-all!` which is a single batched operation

- [ ] **Tool button uses label as display** — `ui/toolbar.cljs:20-30`
  - No icons, just text
  - Consider adding icon support

- [x] **sample-plants accessed directly in scatter** — `tools/scatter.cljs:41`
  - FIXED: Now uses `plants/get-plant` which goes through plants-by-id index

- [x] **Hardcoded default species fallback** — `tools/scatter.cljs:90`
  - FIXED: Now uses `plants/plant-library` instead of `library/sample-plants`
  - Note: Still falls back to first plant if none selected, but from correct source

- [ ] **Chat local-state is defonce** — `ui/panels/chat.cljs:16-19`
  - Hot reload won't reset pending images
  - Use `r/atom` initialized in component if needed

- [x] **sample-plants is hardcoded, not in state** — `ui/panels/library.cljs:17-144`
  - ~~144 lines of static data in code~~
  - FIXED: Plants moved to `garden.data.plants` with 100+ plants, O(1) lookup via `plants-by-id`
  - Still hardcoded in code (not external JSON), but now centralized and well-organized

- [x] **area-types duplicated** — `ui/panels/properties.cljs:15-26` vs `ui/panels/library.cljs:217-227`
  - ~~Same exact map defined twice~~
  - FIXED: Extracted to `garden.data.area-types` namespace with accessor functions

- [x] **Water cache creates new canvas every frame** — `canvas/water.cljs:34-36`
  - ~~`js/document.createElement "canvas"` in render loop~~
  - FIXED: Added `get-or-create-canvas!` that reuses if dimensions match

- [x] **Plant search uses runtime regex construction** — `ui/panels/library.cljs:448-451`
  - ~~`(re-pattern (str "(?i)" search-term))` on every keystroke~~
  - FIXED: Replaced with simple `str/lower-case` + `str/includes?`
  - Faster and won't crash on invalid regex characters

## Tool-Specific Issues

### Plant Tool

- [x] **default-spacing is hardcoded** — `tools/plant.cljs:11`
  - ~~`(def ^:private default-spacing 40)` ignores species-specific spacing~~
  - FIXED: Added `get-species-spacing` that looks up `spacing-cm` from plant library

- [x] **"generic" fallback species** — `tools/plant.cljs:59, 83`
  - ~~Falls back to `"generic"` if no species selected~~
  - FIXED: Now uses `get-default-species-id` which returns first plant from library

- [x] **distance function duplicated** — `tools/plant.cljs:13-16`
  - ~~Same as `geom/points-distance` in util/geometry~~
  - FIXED: Removed local function, now uses `geom/points-distance`

### Trace Tool

- [x] **area-type-info duplicated again** — `tools/trace.cljs:32-43`
  - ~~Third copy of area types~~
  - FIXED: Now uses `garden.data.area-types` namespace

- [x] **simplify-points duplicated** — `tools/trace.cljs:13-30` vs `tools/contour_trace.cljs:56-72`
  - ~~Same algorithm in two files~~
  - FIXED: Extracted to `garden.util.simplify/simplify-by-distance`

- [x] **Hardcoded 50cm simplification** — `tools/trace.cljs:48`
  - ~~Not configurable based on zoom level or user preference~~
  - FIXED: Moved to `const/trace-simplify-tolerance-cm` constant (still static but centralized)

### Contour Trace Tool

- [ ] **snap-to-contour is O(n²)** — `tools/contour_trace.cljs:36-54`
  - Searches 10x10 grid around each point
  - With fine resolution, very slow
  - Should use gradient descent or march along contour

- [x] **area-type-info duplicated fourth time** — `tools/contour_trace.cljs:74-84`
  - ~~Now four copies of same map~~
  - FIXED: Now uses `garden.data.area-types` namespace

- [x] **Keyboard shortcuts inconsistent** — `tools/contour_trace.cljs:158-168` vs `tools/trace.cljs:98-114`
  - ~~contour_trace: 1=bed, 2=path, 3=water~~
  - ~~trace: 1=water, 2=bed, 3=path~~
  - FIXED: Both tools now use 1=water, 2=bed, 3=path, etc. Also added letter shortcuts (w/b/p/l/h)

### Elevation Point Tool

- [x] **point-hit-radius doesn't match select tool** — `tools/elevation_point.cljs:26-28`
  - ~~Uses 15px, select tool uses 12px~~
  - NOT AN ISSUE: Intentionally different - vertices (12px) are small corners, elevation points (15px) are larger standalone markers

- [ ] **find-topo-point-at is O(n)** — `tools/elevation_point.cljs:33-41`
  - Same linear scan pattern
  - Should share spatial index with plants/areas

### Pan Tool

- [x] **Cursor update on every mouse-up** — `tools/pan.cljs:41`
  - ~~Calls `state/set-cursor!` on every mouse release~~
  - NOT AN ISSUE: Cursor state must be explicitly set as canvas reads from state, not from tool's cursor method

## Grid Rendering

- [x] **Grid doesn't respect viewport bounds check** — `canvas/grid.cljs:54`
  - ~~`(when (and (< num-v-lines 500) (< num-h-lines 500))` — magic numbers~~
  - FIXED: Extracted to `const/max-grid-lines` constant

- [ ] **Label spacing multiplier duplicates LOD logic** — `canvas/grid.cljs:23-30`
  - Similar thresholds to `calculate-lod-spacing`
  - Should share constants or unify

## Viewport Issues

- [ ] **viewport.cljs depends on global state** — `canvas/viewport.cljs:13-18`
  - `screen->canvas` reads from `state/viewport` directly
  - Makes it impossible to test without global state
  - Should take viewport as parameter

## Slope Analysis

- [x] **point-in-polygon duplicated** — `topo/slope.cljs:114-130`
  - ~~Already exists in `util/geometry.cljs`~~
  - FIXED: Removed duplicate, now uses `geom/point-in-polygon?` from util.geometry

- [x] **Circular mean formula complex** — `topo/slope.cljs:179-182`
  - ~~No comments explaining the math~~
  - FIXED: Added comments explaining why regular averaging fails and how circular mean works

## Water Simulation

- [ ] **Worker uses js* macro for hot loop** — `simulation/water_worker.cljs:56-144`
  - 90 lines of raw JS embedded in ClojureScript
  - Hard to debug, no source maps
  - Consider typed arrays with direct aget/aset or move to pure JS file

- [ ] **Worker state atom accessed from multiple threads** — `simulation/water_worker.cljs:10-27`
  - Atom is single-threaded but worker runs in separate thread
  - Actually fine since worker is separate JS context, but confusing

- [ ] **setTimeout for simulation loop** — `simulation/water_worker.cljs:197`
  - `js/setTimeout` for game loop, not `requestAnimationFrame`
  - Actually correct for worker (no RAF), but interval is hardcoded 50ms

- [x] **simulation-interval-ms duplicated** — `physics.cljc:10-11` vs `water_worker.cljs:197`
  - ~~50ms defined in both places~~
  - FIXED: water_worker.cljs now imports and uses `physics/simulation-interval-ms`

- [ ] **Grid resampling on every start** — `simulation/water/core.cljs:36-55`
  - `send-elevation-data!` resamples entire grid each time
  - Should cache if dimensions unchanged

## Physics Constants

- [ ] **No units in variable names** — `physics.cljc`
  - `base-flow-rate` — rate per what?
  - `min-flow-threshold` — threshold in what units?
  - Should use naming like `base-flow-rate-per-step`

## Build & Config

- [x] **Worker build not connected to main build** — `shadow-cljs.edn`
  - ~~`:dev` and `:water-worker` are separate builds~~
  - ~~Must manually start both for development~~
  - FIXED: Lein aliases `dev` and `build` now include both builds
  - Note: Still separate shadow-cljs builds, but aliases handle it automatically

- [x] **No source maps in production** — `shadow-cljs.edn:45`
  - ~~`:min` build has no `:source-map` option~~
  - FIXED: Added `:source-map true` to compiler-options for production build

- [ ] **No externs for Three.js** — `shadow-cljs.edn`
  - Using Three.js with advanced compilation
  - May cause runtime errors if not properly annotated with `^js`

## HTML & Assets

- [x] **Favicon points to external URL** — `index.html:7`
  - ~~Uses `clojurescript.org` favicon~~
  - FIXED: Now uses inline SVG data URI with 🌱 emoji

- [x] **No meta description or OG tags** — `index.html`
  - ~~Missing basic SEO/sharing tags~~
  - FIXED: Added title, description, theme-color, and lang attribute

- [ ] **Single CSS file** — `style.css` (842 lines)
  - All styles in one file
  - No CSS modules or scoped styles
  - Hard to maintain as app grows

## Water Panel UI

- [x] **Resolution change note is unclear** — `ui/panels/water.cljs:48`
  - ~~"Changes apply when restarting simulation or switching to 3D view"~~
  - FIXED: Simplified to "Restart simulation to apply resolution changes"

- [ ] **Preset buttons don't disable input while applying** — `ui/panels/water.cljs:152-171`
  - Rapid clicking could cause race conditions
  - Should debounce or disable during update

## Worker Communication

- [ ] **Worker URL is hardcoded** — `simulation/water/worker.cljs:46`
  - `(js/Worker. "/js/compiled/water-worker.js")`
  - Should be configurable for different deployment paths

- [ ] **No message queue for worker** — `simulation/water/worker.cljs:69-73`
  - Messages sent directly without buffering
  - If worker not ready, messages lost silently

- [ ] **on-message handlers never cleaned up** — `simulation/water/worker.cljs:90-91`
  - Returns cleanup function but never called
  - Could cause memory leaks with hot reload

## Test Infrastructure

- [x] **Tests exist and pass** — 67 tests, 467 assertions, 0 failures
  - Good coverage of geometry, state, tools, topo, simulation, LLM
  - Missing: integration tests, canvas rendering tests (need browser)

- [ ] **No CI configuration**
  - No `.github/workflows/` or similar
  - Tests should run automatically on push

- [ ] **No test for select tool** — missing `tools/select_test.cljs`
  - Most complex tool with drag, vertex editing, multi-select
  - High risk of regressions

## CSS Issues

- [ ] **No dark mode support** — `style.css`
  - All colors are hardcoded light theme
  - No `prefers-color-scheme` media queries

- [ ] **Magic pixel values throughout** — `style.css`
  - 380px chat width, 500px height, etc.
  - Should use CSS variables or responsive units

- [ ] **No responsive breakpoints** — `style.css`
  - Fixed widths for panels
  - Won't work well on mobile/tablet

- [ ] **z-index values arbitrary** — `style.css:474, 498, 756, 819`
  - 99, 100, 200, 9999 scattered throughout
  - Should define z-index scale as CSS variables

## Project Configuration

- [x] **Placeholder URL in project.clj** — `project.clj:3`
  - ~~`:url "http://example.com/FIXME"`~~
  - FIXED: Removed placeholder URL, updated description to match index.html

- [x] **core.async included but may not be used** — `project.clj:10`
  - ~~`[org.clojure/core.async "1.6.681"]`~~
  - FIXED: Verified not used anywhere, removed dependency

- [x] **Build alias doesn't include worker** — `project.clj:19`
  - ~~`"build"` only builds `:min`, not `:water-worker`~~
  - FIXED: Both `dev` and `build` aliases now include `water-worker`

- [ ] **No version pinning for Three.js** — npm dependencies
  - JavaScript deps not listed in project.clj
  - Presumably installed via npm but not version-locked

## Documentation

- [ ] **No README.md or documentation**
  - New developers can't onboard easily
  - No architecture overview
  - No development setup instructions

- [ ] **No CHANGELOG**
  - Can't track what changed between versions

- [ ] **No inline documentation for complex algorithms**
  - Flood fill, marching squares, contour tracing
  - RDP simplification
  - Water simulation physics
  - Should have algorithm explanations

## Console Logging

- [ ] **27 console.log/warn/error calls in production code**
  - Spread across 8 files
  - `geotiff.cljs` has 12 alone
  - Should use proper logging library with log levels
  - Or at minimum wrap in `when ^boolean js/goog.DEBUG`

## Atom Proliferation

- [ ] **18 `defonce atom` declarations across codebase**
  - State is scattered: `state.cljs`, `core.cljs`, `topo.cljs`, `terrain3d.cljs`, `worker.cljs`, etc.
  - Some are truly global (app-state), some are module-private
  - Consider consolidating into single state tree or using explicit dependency injection

- [ ] **Camera state split across 3 atoms** — `terrain3d.cljs:63-65`
  - `camera-yaw`, `camera-pitch`, `keys-pressed` are separate atoms
  - Should be single atom with map, or in main app-state

## Test Duplication

- [ ] **Test files duplicate private functions** — `fill_test.cljs:6-43`, `llm_test.cljs:8-29`
  - `colors-similar?`, `simplify-contour`, `trim-chat-messages` reimplemented in tests
  - If implementation changes, test copies won't
  - Should use `with-redefs` or expose functions via `^:export` for testing

## Ground Panel Issues

- [x] **bar-px 150 hardcoded again** — `ui/panels/ground.cljs:172`
  - FIXED: Now uses `const/bar-image-pixels` and `const/default-bar-meters`

- [x] **set-state! called 4 times in sequence** — `ui/panels/ground.cljs:27-31`
  - ~~Each `set-state!` triggers render~~
  - FIXED: Batched into single `update-state!` with merge (in both `load-image-file!` and `extract-rgb-from-geotiff!`)

- [ ] **GeoTIFF loading doesn't show error to user** — `ui/panels/ground.cljs:127-129`
  - `.catch` only logs to console
  - Should display error in UI

- [x] **File reader error not caught** — `ui/panels/ground.cljs:130-133`
  - ~~Only logs to console, loading state cleared but no user feedback~~
  - FIXED: Added onerror handlers for FileReader and Image in `load-image-file!`

## LLM Issues Found

- [ ] **Tool descriptions have hardcoded coordinate hints** — `llm.cljs:92-97`
  - "Use coordinates like 100-800 for x and y"
  - These are arbitrary and won't match actual garden bounds

- [ ] **Model hardcoded** — `llm.cljs:26`
  - `"claude-haiku-4-5"` — should be configurable in settings

- [ ] **API URL hardcoded** — `llm.cljs:25`
  - No way to change for proxies or testing

- [x] **remove_plants_in_area doesn't batch** — `llm.cljs:299-310`
  - ~~Same history-per-operation issue~~
  - FIXED: Now uses `state/remove-plants-batch!`

## Reference Image Issues

- [x] **nice-distances array doesn't include small values** — `canvas/reference.cljs:43-45`
  - ~~Starts at 1m, but at high zoom you might want 0.5m, 0.25m~~
  - FIXED: Now includes `[0.25 0.5 1 2 5 10 20 50 100 200 500 1000]`

- [x] **bar-image-pixels constant** — `canvas/reference.cljs:11`
  - FIXED: Removed local constant, now imports from `garden.constants`

## Accessibility Issues

- [ ] **No ARIA labels on buttons** — throughout UI
  - Screen readers can't understand icon-only buttons
  - Need `aria-label` attributes

- [ ] **No keyboard navigation** — tool selection, panels
  - Can't tab through tools
  - No focus indicators styled

- [ ] **Color alone conveys information** — plant types, area types
  - Colorblind users may struggle
  - Need patterns or icons in addition to color

- [ ] **No skip-to-content link** — `index.html`
  - Screen reader users must navigate entire toolbar

## Internationalization

- [ ] **All strings hardcoded in English** — throughout
  - "Vegetable Bed", "Start Rain", "Loading..."
  - Should use i18n library or at minimum extract to constants
  - Would also help with testing

## Reference Panel Issues

- [x] **Sequential set-state! calls in reference image load** — `ui/panels/reference.cljs:13-17`
  - ~~4 `set-state!` calls in a row, each triggers re-render~~
  - FIXED: Batched into single `update-state!` with `merge`

- [x] **bar-px 150 hardcoded in reference panel** — `ui/panels/reference.cljs:66`
  - FIXED: Now uses `const/bar-image-pixels` and `const/default-bar-meters`

- [x] **FileReader has no error handler** — `ui/panels/reference.cljs:5-19`
  - ~~If file read fails, user gets no feedback~~
  - FIXED: Added error handlers for both FileReader and Image loading with user alerts

## Topo Panel Issues

- [ ] **File object stored in state** — `ui/panels/topo.cljs:34`
  - `(state/set-state! [:topo :source-file] file)`
  - File objects are ephemeral and may not serialize/persist correctly
  - Consider storing file name and re-prompting user if needed

## Water Simulation API

- [x] **Deprecated alias still exported** — `simulation/water.cljs:19`
  - ~~`sim-running?` marked as "Deprecated alias" in comment~~
  - FIXED: Removed deprecated alias after updating terrain3d.cljs to use `running?`

## App Component Issues

- [ ] **get-canvas-point duplicates viewport logic** — `ui/app.cljs:41-47`
  - Manual rect calculation duplicates viewport functions
  - Should use existing viewport utilities

- [x] **Multiple set-state! calls on mouse-leave** — `ui/app.cljs:129-130`
  - ~~Two sequential `set-state!` calls~~
  - FIXED: Batched into single swap! with -> threading

- [x] **name can throw on nil** — `ui/app.cljs:230`
  - ~~`(name active-tool)` will throw if `active-tool` is nil~~
  - FIXED: Now uses `(if active-tool (name active-tool) "--")`

- [x] **Drop uses text/plain for species ID** — `ui/app.cljs:148`
  - ~~Using generic "text/plain" mime type~~
  - FIXED: Now uses `const/species-drag-mime-type` ("application/x-garden-species")

## Date Handling

- [ ] **js/Date. used inconsistently** — 6 locations
  - `llm.cljs:254, 267, 335`, `ui/app.cljs:154`, `tools/plant.cljs:62, 90`
  - No timezone handling, no date utilities
  - Should create `util/date.cljs` with consistent ISO date generation

## Tool Protocol Issues

- [ ] **No on-wheel handler in ITool protocol** — `tools/protocol.cljs:14-27`
  - Tools can't intercept scroll events
  - Some tools (like select) might want scroll-to-zoom behavior

- [ ] **No tool ordering mechanism** — `tools/protocol.cljs:32-38`
  - Tools appear in arbitrary order in toolbar
  - Should have explicit ordering or priority

- [x] **No duplicate ID check in register-tool!** — `tools/protocol.cljs:34-37`
  - ~~If two tools register same ID, silent override~~
  - FIXED: Now logs console warning when overwriting existing tool

## Render Loop Issues

- [ ] **5 atoms for render state** — `canvas/core.cljs:21-38`
  - `last-render-state`, `render-scheduled?`, `last-render-time`, `render-times`
  - Plus `render-watcher` defonce
  - Should consolidate into single render-state atom

- [x] **No error boundary in render!** — `canvas/core.cljs:50-119`
  - ~~If any layer throws, entire render fails silently~~
  - FIXED: Added try-catch-finally around render layers with console.error logging
  - `finally` ensures `.restore ctx` is always called to prevent canvas state leakage

- [ ] **needs-render? compares full state** — `canvas/core.cljs:29-32`
  - Creates snapshot of state on every check
  - Consider using `identical?` check or dirty flag

## Inline Styles

- [ ] **115 inline :style maps across UI** — throughout panels
  - Inline styles harder to maintain and can't use CSS features
  - Consider extracting common styles to CSS classes
  - Or use a CSS-in-JS solution consistently

## Private Function Exposure

- [ ] **199 defn- functions, some needed for testing** — throughout
  - Test files duplicate private functions (fill_test.cljs, llm_test.cljs, trace_test.cljs)
  - Consider `^:private` metadata instead, or expose via test namespace
  - Or use `with-redefs` for testing

## parseFloat/parseInt without NaN check

- [ ] **23 parseFloat/parseInt calls without NaN validation** — UI panels
  - Most convert to number but don't check for NaN
  - Invalid input silently produces NaN
  - Should validate: `(let [v (js/parseFloat x)] (when-not (js/isNaN v) ...))`

## Water Simulation Issues (additional)

- [x] **Sequential set-state! in water control functions** — `simulation/water/core.cljs:95-96, 107-108`
  - ~~`stop!` calls `set-state!` twice (running?, raining?)~~
  - FIXED: Both `stop!` and `start-rain!` now batch into single `update-state!` with merge

- [x] **Inconsistent Date.now usage** — throughout
  - FIXED: Standardized on `(js/Date.now)` - updated water/core.cljs

- [x] **when-not ready? sends elevation data** — `simulation/water/core.cljs:86-87, 102-103`
  - ~~`(when-not (worker/ready?) (send-elevation-data!))` sends when NOT ready~~
  - CLARIFIED: Logic is correct - sends elevation data to INITIALIZE worker when it's not yet ready
  - Added clarifying comments to both start! and start-rain!

- [ ] **Worker message handlers never unregistered** — `simulation/water/core.cljs:76-77`
  - `on-message` returns cleanup fn but callers ignore it
  - Hot reload accumulates handlers

- [ ] **Worker event listeners not cleaned up** — `simulation/water/worker.cljs:47-48`
  - addEventListener for message/error but no removeEventListener on terminate
  - Worker termination might not clean up properly

## UI State Management

- [ ] **defonce r/atom for UI state** — `ui/panels/ground.cljs:8`
  - `(defonce ^:private ui-state (r/atom {:advanced-open? false}))`
  - defonce prevents hot-reload reset
  - For UI state that should reset, use `def` or component-local state

- [ ] **defonce chat-local-state** — `ui/panels/chat.cljs:17`
  - Already noted in TODO but using r/atom correctly
  - Just needs to be component-local if hot reload reset is desired

## Array Handling

- [ ] **array-seq used inconsistently** — UI file inputs
  - 4 places use `(first (array-seq (.. e -target -files)))`
  - Should extract to helper: `(defn get-first-file [e] ...)`
  - Or use `(aget (.-files (.-target e)) 0)`

## Missing Validation

- [x] **No file type validation on drop** — `ui/app.cljs:148-155`
  - ~~Accepts any species-id string from drop~~
  - FIXED: Now validates species exists in library via `plants/get-plant` before creating plant

- [x] **No bounds checking on zoom** — `ui/app.cljs:55-57`
  - ~~Zoom factor applied without limits~~
  - NOT AN ISSUE: `state/zoom-at!` already clamps zoom to range [0.001, 10.0] (0.1% to 1000%)

## Area Type Colors Still Duplicated

- [x] **Area type colors hardcoded in render.cljs** — `canvas/render.cljs:342-345, 934-945`
  - ~~Despite `data/area_types.cljs` existing, colors still duplicated in `case` statements~~
  - FIXED: Now uses `(area-types/get-color area-type)` in both locations

- [x] **Area type colors hardcoded in fill.cljs** — `tools/fill.cljs:543-544`
  - ~~Same issue - `type-colors` map duplicates area-types colors~~
  - FIXED: Now imports from `garden.data.area-types`

- [x] **Area type colors hardcoded in llm.cljs** — `llm.cljs:383, 393`
  - FIXED: Now uses `(area-types/get-color :path)` and `(area-types/get-color :water)`

## Canvas Context State

- [x] **Many .save/.restore calls but no try-finally** — throughout canvas rendering
  - ~~If render code throws, canvas context state leaks~~
  - FIXED: Added try-finally in main render! function (canvas/core.cljs)
  - Note: Sub-render functions still have direct save/restore but errors bubble up to main handler

## Duplicated Bounding Box Calculation

- [ ] **apply min/max pattern repeated instead of using bounding-box** — multiple files
  - `(apply min xs) (apply max xs)` pattern in render.cljs (7 times), slope.cljs (2 times)
  - `util/geometry.cljs` has `bounding-box` function but not used
  - Should use: `(let [{:keys [min max]} (geom/bounding-box points)] ...)`

## Two Point-In-Polygon Implementations

- [x] **Ray casting vs winding number algorithms** — `util/geometry.cljs` vs `topo/slope.cljs`
  - ~~`geometry.cljs:79` uses winding number rule~~
  - ~~`slope.cljs:114` uses ray casting algorithm~~
  - FIXED: slope.cljs now uses `geom/point-in-polygon?` from geometry.cljs (single implementation)

## No Transient Collections for Performance

- [ ] **Batch operations use persistent collections** — throughout
  - No `conj!`, `assoc!`, `dissoc!` transient operations found
  - For operations building large collections, transients would be faster
  - Particularly in flood fill, contour tracing, water simulation

## Multimethod Duplication

- [ ] **render-plant! methods all repeat same let binding** — `canvas/render.cljs:639-701`
  - Every method does: `(let [plant-data (get-plant-data ...) [x y] (:position plant) radius (plant-radius plant) color ...]`
  - Should extract common data lookup into helper: `(defn- plant-render-data [plant] {...})`
  - Then methods just destructure: `(let [{:keys [x y radius color]} (plant-render-data plant)]`

## Message Handler Pattern

- [x] **handle-message defmulti has no fallback logging** — `simulation/water_worker.cljs:254`
  - ~~`:default` method does nothing~~
  - NOT AN ISSUE: The `:default` method already has `(js/console.warn "Unknown message type:" ...)`

---

## Performance Issues (Deep Dive)

### Critical Hot Path Issues

- [x] **get-plant-data is O(n) and called 13+ times per plant per render** — `canvas/render.cljs:452-455`
  - ~~`(first (filter #(= (:id %) species-id) library/sample-plants))` scans entire list~~
  - FIXED: Created `plants-by-id` map in `garden.data.plants` for O(1) lookup
  - Updated `get-plant` function to use the index
  - Also fixed scatter.cljs and llm.cljs to use `plants/get-plant` and `plants/plant-library`

- [ ] **find-plant-at is O(n) on every mouse move** — `state.cljs:158-168`, `ui/app.cljs:119`
  - Called on EVERY mouse move to check hover
  - For each plant, calls `render/get-plant-radius` which calls `get-plant-data` (now O(1))
  - Total: O(plants) per mouse move (improved from O(n²) with plant lookup fix)
  - **Fix**: Spatial index (quadtree) for O(log n) hit detection

- [ ] **elevation->color does 2 filter operations per pixel** — `canvas/topo.cljs:76-89`
  - For 2048×2048 topo cache: ~8 million filter iterations
  - `(last (filter ...))` and `(first (filter ...))` over color ramp
  - **Fix**: Pre-compute color lookup table for 256 values, use array index

- [ ] **visible-bounds calculated 3+ times per render** — `canvas/viewport.cljs:36-42`
  - Called from grid.cljs, render.cljs (twice)
  - Each call reads state and does coordinate conversion
  - **Fix**: Calculate once at start of render!, pass as parameter

### Per-Render Calculations

- [ ] **polygon-area recalculated for sort on every render** — `canvas/render.cljs:442`
  - `(sort-by #(- (polygon-area (:points %))) visible-areas)`
  - Calculates area of every visible area, every frame
  - Area is constant unless points change
  - **Fix**: Cache area in area map, recalculate only on point edit

- [ ] **render-to-cache! creates new canvas every time** — `canvas/topo.cljs:193`
  - `(js/document.createElement "canvas")` allocates DOM element
  - Should reuse existing canvas if dimensions match

### Linear Scans (O(n)) That Should Be O(1)

- [ ] **find-area is O(n)** — `state.cljs:153`
  - `(first (filter #(= (:id %) id) (areas)))`
  - Called during drag operations
  - **Fix**: Maintain `{id -> area}` index

- [ ] **find-plant is O(n)** — `state.cljs:156`
  - `(first (filter #(= (:id %) id) (plants)))`
  - Called during selection, property edits
  - **Fix**: Maintain `{id -> plant}` index

- [ ] **find-area-at is O(n) with expensive point-in-polygon** — `tools/select.cljs:75-78`
  - For each area, calls `point-in-polygon?` which is O(vertices)
  - Total: O(areas × avg_vertices)
  - **Fix**: Use bounding box pre-filter, then detailed check

- [x] **scatter tool searches sample-plants linearly** — `tools/scatter.cljs:41`
  - FIXED: Now uses `plants/get-plant` for O(1) lookup

- [x] **LLM tool execution searches sample-plants** — `llm.cljs:310`
  - FIXED: Now uses `plants/get-plant` for O(1) lookup and `plants/plant-library` for listing

### Missing Memoization

- [ ] **No memoization anywhere in codebase** — throughout
  - No `memoize`, no `reagent/track`, no manual caching
  - Pure functions like `polygon-area`, `plant-radius` recalculate every time
  - **Fix**: Add memoization for expensive pure functions

- [ ] **No component-level memoization** — Reagent components
  - No `reagent.core/memo` usage
  - All components re-render when parent re-renders
  - **Fix**: Wrap expensive components in `r/memo`

### Allocation in Hot Paths

- [ ] **New vector created per coordinate conversion** — `canvas/viewport.cljs:17-18, 25-26`
  - `[(/ (- sx ox) zoom) (/ (- sy oy) zoom)]` allocates new vector
  - Called many times during mouse move, render
  - **Fix**: Pass x,y separately or use mutable point object

- [ ] **Nested doseq creates intermediate sequences** — `canvas/render.cljs:153-154, 163-164, etc.`
  - Multiple `(doseq [x (range ...) y (range ...)]` for area textures
  - `range` creates lazy seq, `doseq` realizes it
  - Area textures drawn every render for visible areas
  - **Fix**: Use `dotimes` with arithmetic instead of `range`

### Contour/Marching Squares

- [ ] **Marching squares iterates entire grid** — `canvas/topo.cljs:433-481`
  - Even with caching, recomputes when interval/zoom changes
  - Grid can be 2048×2048 = 4 million cells
  - **Fix**: Only compute for visible viewport area, use worker thread

### Three.js / 3D View

- [ ] **Terrain mesh rebuilds on every resize** — `canvas/terrain3d.cljs`
  - ResizeObserver triggers geometry rebuild
  - Should only rebuild when terrain data changes

- [ ] **No LOD for 3D terrain** — `canvas/terrain3d.cljs`
  - Same mesh detail at all camera distances
  - Far terrain could use lower resolution

### State Subscription Granularity

- [ ] **render-keys extracts 8 top-level keys for dirty check** — `canvas/core.cljs:27`
  - `(select-keys state [:areas :plants :viewport :tool :selection :ui :topo :topo-points])`
  - Any change in these triggers potential re-render
  - `:ui` changes frequently (mouse pos, hover state)
  - **Fix**: More granular dirty tracking, separate UI state

### Area Texture Rendering

- [ ] **4 texture functions with nested loops redraw every frame** — `canvas/render.cljs:141-334`
  - `draw-soil-texture!`: 3 nested doseq loops (~1300 draw calls per 500x500 area)
  - `draw-stone-path-texture!`: Similar nested loops
  - `draw-wood-texture!`: Multiple nested loops
  - `draw-water-texture!`: Ripples + shimmer loops
  - All redrawn every frame for visible areas when zoom > 0.25
  - **Fix**: Pre-render textures to offscreen canvas, blit from cache

- [ ] **rand() called in render loop** — `canvas/render.cljs:330`
  - `(when (< (rand) 0.3) ...)` in water shimmer
  - Non-deterministic rendering, textures flicker each frame
  - **Fix**: Use deterministic noise based on position

### Math in Hot Loops

- [ ] **Math/sin, Math/cos, Math/abs called per texture element** — `canvas/render.cljs:156-158, etc.`
  - Trig functions are expensive
  - Called thousands of times per area per frame
  - **Fix**: Pre-compute or use lookup tables

### Topo Rendering

- [ ] **topo/bilinear-interpolate called per pixel** — `canvas/topo.cljs:226`
  - Full bilinear interpolation for each cache pixel
  - 4 array lookups + lerp calculations per pixel
  - **Fix**: Use WebGL for GPU-accelerated interpolation

### State Access Patterns

- [ ] **state/topo-data called repeatedly** — `topo/core.cljs:100, 108, 117`
  - `get-elevation-at`, `get-elevation-at-nearest`, `in-bounds?` each call `state/topo-data`
  - If called in a loop (e.g., contour lines), derefs atom many times
  - **Fix**: Pass topo-data as parameter, or cache locally

- [ ] **Direct @state/app-state access** — `tools/scatter.cljs:69, 89`, `canvas/core.cljs:55, 124`
  - Bypasses accessor functions, no batching
  - Each `@state/app-state` is a full deref
  - **Fix**: Use accessor functions or pass state as parameter

- [ ] **No reagent cursors for granular subscriptions** — throughout
  - Components subscribe to entire app-state
  - Any change triggers all component re-evaluations
  - **Fix**: Use `reagent.core/cursor` for path-specific subscriptions

### Water Simulation Worker

- [ ] **Math.sqrt in inner loop** — `simulation/water_worker.cljs:125`
  - `Math.sqrt(slope)` called for every cell with water
  - Could use linear approximation or lookup table

- [ ] **Array allocation in inner loop** — `simulation/water_worker.cljs:89-99`
  - `ni`, `nd`, `neighbors` arrays created fresh each cell iteration
  - Should pre-allocate outside loop

### General ClojureScript Performance

- [ ] **No use of ^:const metadata** — throughout
  - Constants like `elevation-colors`, `max-full-cache-pixels` could use `^:const`
  - Allows compiler to inline values

- [ ] **Keyword creation in hot paths** — throughout
  - `(or (:type area) :bed)` creates keyword objects
  - Already interned but still a lookup

---

## Memory Leak Risks

- [ ] **add-watch without remove-watch** — `canvas/core.cljs:141-144`
  - `(add-watch state/app-state :render ...)` added but never removed
  - Hot reload accumulates watchers
  - **Fix**: Track watcher in atom, remove before re-adding

- [ ] **Worker message handlers accumulate** — `simulation/water/core.cljs:76-77`
  - `on-message` returns cleanup fn but callers ignore it
  - Each init! adds new handlers without removing old ones
  - **Fix**: Store cleanup fns and call before re-registering

- [ ] **Only 2 components have unmount cleanup** — `ui/app.cljs:84`, `canvas/terrain3d.cljs:903`
  - Other components with side effects don't clean up
  - Event listeners, timers may leak
  - **Fix**: Add `:component-will-unmount` lifecycle where needed

## Inline Styles Hardcoding

- [ ] **117 hardcoded pixel values in inline styles** — throughout UI panels
  - `:style {:width "80px" :padding "8px"}` patterns everywhere
  - Inconsistent spacing (4px, 6px, 8px, 10px, 12px, 16px used randomly)
  - **Fix**: Use CSS custom properties or theme constants

- [ ] **Font sizes hardcoded in inline styles** — throughout
  - "10px", "11px", "12px", "14px" scattered in panels
  - No consistent type scale
  - **Fix**: Define type scale CSS variables

- [ ] **Colors hardcoded in inline styles** — throughout panels
  - `#666`, `#888`, `#ddd`, `#f5f5f5` repeated inline
  - No semantic color names
  - **Fix**: Use CSS custom properties for theme colors

## Canvas Font Rendering

- [ ] **Font set multiple times per render** — `canvas/topo.cljs:568`, `canvas/water.cljs:124`
  - `(set! (.-font ctx) "11px sans-serif")` etc.
  - Font parsing is expensive
  - **Fix**: Set font once at render start, reset only when needed

## Console Logging in Production

- [ ] **27 console.log/warn/error calls in production code** — throughout
  - `topo/geotiff.cljs` has 12 console logs
  - `simulation/water/core.cljs` has 2 logs
  - `llm.cljs` has 4 logs (including tool execution debug)
  - `ui/panels/ground.cljs` has 5 logs
  - **Fix**: Use conditional logging with debug flag, or remove entirely

- [ ] **Tool execution logged unconditionally** — `llm.cljs:454`
  - `(js/console.log "Executing tool:" tool-name tool-input)`
  - Exposes potentially sensitive data to console
  - **Fix**: Remove or gate behind debug mode

## Event Handler Issues

- [ ] **Keyboard shortcuts not documented** — `ui/app.cljs`
  - Undo/redo shortcuts mentioned in titles but not in help
  - No keyboard shortcut overlay or help panel

- [ ] **No keyboard navigation for tools** — `ui/toolbar.cljs`
  - Tools only accessible via mouse
  - **Fix**: Add keyboard shortcuts (1-9 for tools, etc.)

---

## Worker Cleanup Issues

- [ ] **Worker terminate! doesn't remove event listeners** — `simulation/water/worker.cljs:53-59`
  - `(.terminate w)` is called but event listeners are not removed first
  - `handle-worker-message` and `handle-worker-error` remain attached
  - May cause issues if worker is re-initialized later
  - **Fix**: Remove listeners before terminating: `(.removeEventListener w "message" ...)`

- [ ] **Self message listener never removed** — `simulation/water_worker.cljs:263`
  - `(js/self.addEventListener "message" ...)` added globally
  - Worker script never removes this listener
  - Not a leak (worker terminates), but inconsistent with other cleanup patterns

## Performance Anti-Patterns (Additional)

- [ ] **Atom used inside nested doseq loop** — `tools/contour_trace.cljs:44-56`
  - `(let [best (atom ...)] (doseq [dx...] (doseq [dy...] (reset! best ...))))`
  - Atom deref/reset! on every iteration adds overhead
  - **Fix**: Use `reduce` with accumulator or mutable JS object

- [ ] **Many #js material objects created per plant** — `canvas/terrain3d.cljs:333-650`
  - `(THREE/MeshLambertMaterial. #js {:color ...})` created fresh for every plant
  - Same colors used repeatedly but never cached
  - With 100+ plants, creates 300+ duplicate materials
  - **Fix**: Create materials once in an indexed map, reuse by color

- [ ] **setLineDash called with new arrays every frame** — `canvas/render.cljs:465,605,649`
  - `(.setLineDash ctx #js [5 3])` allocates new array each render
  - **Fix**: Define dash pattern arrays as module-level constants

## Component Lifecycle Issues

- [ ] **Only 2 components have lifecycle cleanup** — `ui/app.cljs:68,84`, `canvas/terrain3d.cljs:885,903`
  - Most components use Form-1 (just render function) with no unmount cleanup
  - Components with timers or local state may not clean up properly
  - **Fix**: Audit components for hidden state that needs cleanup

- [ ] **File input ref leaks** — UI panels with file inputs
  - File inputs created but never explicitly cleaned up
  - Browser handles this, but pattern inconsistent with explicit cleanup elsewhere

## Default Value Patterns

- [ ] **60+ `(or (:key x) default)` patterns** — throughout codebase
  - Same defaults repeated at every call site
  - If default changes, must update many places
  - **Fix**: Define defaults in data schema, use `get` with default at accessor level
  - Examples: `(or (:spacing-cm plant-data) 50)` in 5+ places
            `(or (:opacity topo) 0.3)` in 3+ places
            `(or (:mode tool-state) :single)` in 2+ places

- [ ] **No schema for entity defaults** — `state.cljs`
  - `add-area!`, `add-plant!` accept any map
  - Defaults scattered across code instead of in one place
  - **Fix**: Use malli schema with default value specs

## Three.js Specific Issues

- [ ] **Material not disposed on scene cleanup** — `canvas/terrain3d.cljs`
  - Plants create many MeshLambertMaterial objects
  - `dispose-scene!` may not dispose all materials
  - **Fix**: Track all created materials, dispose in cleanup

- [ ] **Geometry created per plant, never pooled** — `canvas/terrain3d.cljs`
  - BoxGeometry, CylinderGeometry, SphereGeometry created fresh for each plant
  - Same geometry types could be shared (instancing)
  - **Fix**: Use InstancedMesh for plants with same geometry

## Reagent Optimization Gaps

- [ ] **No r/cursor usage anywhere** — throughout UI
  - All components read from full app-state atom
  - Any state change causes all subscribed components to re-evaluate
  - `r/cursor` would give granular subscription paths
  - **Fix**: Use `(r/cursor state/app-state [:viewport :zoom])` for specific paths

- [ ] **No r/with-let for local state** — throughout components
  - Some components use `let` bindings that get recalculated on every render
  - `r/with-let` persists bindings across re-renders
  - **Fix**: Use for expensive local computations that shouldn't recalculate

- [ ] **139 event handlers, many inline** — throughout UI
  - Most `:on-change`, `:on-click` handlers are inline anonymous functions
  - Creates new function objects on every render
  - Prevent React memo optimizations
  - **Fix**: Extract handlers to named functions or use `react/useCallback` equivalent

## Canvas Drawing Patterns

- [ ] **Canvas created but not removed** — `tools/fill.cljs:72,102`
  - `(js/document.createElement "canvas")` in `flood-fill-image` and `get-reference-image-data`
  - Canvas created but never appended to DOM (just used for context)
  - These orphaned canvases may not be GC'd immediately
  - **Fix**: Reuse a pool of canvases or explicitly set to nil after use

- [ ] **Multiple canvas context setups per draw** — `canvas/render.cljs`, `canvas/topo.cljs`
  - `(set! (.-font ctx) ...)`, `(set! (.-fillStyle ctx) ...)` called many times per frame
  - Canvas context mutations are relatively expensive
  - **Fix**: Batch similar draw operations, minimize context switches

## try/catch Patterns

- [ ] **10 try blocks but no structured error handling** — throughout
  - Each try/catch handles errors differently
  - Some log, some ignore, some return error maps
  - **Fix**: Create consistent error handling utilities

- [ ] **No error boundary at component level** — React/Reagent
  - If a component throws, entire app crashes
  - **Fix**: Add error boundary component to wrap major sections

## Tool System Extensibility

- [ ] **Tool execution case statement is not extensible** — `llm.cljs:456-471`
  - Adding new tool requires modifying central case statement
  - **Fix**: Use multimethod or protocol dispatch keyed on tool name

- [ ] **LLM tools tightly coupled to execution** — `llm.cljs:84-234, 451-473`
  - Tool schemas and execution are in same file
  - Adding a tool requires editing 3+ places
  - **Fix**: Each tool should define its own schema + executor

## Clojure Idiom Issues

- [ ] **`(first (filter ...))` used instead of `(some ...)`** — multiple files
  - Already noted for performance, but also idiomatic issue
  - `(some #(when (pred %) %) coll)` is more idiomatic for "find first matching"
  - Or use `(reduce ...)` for early termination with result

- [ ] **No threading macros for nested gets** — throughout
  - `(get-in @app-state [:topo :elevation-data])` instead of `(-> @app-state :topo :elevation-data)`
  - Not a bug but less readable for deep access patterns

## Set Creation in Hot Paths

- [ ] **`(set ids)` created inside filterv predicate** — `state.cljs:360,367`
  - `(filterv (fn [a] (not (contains? (set ids) ...))) areas)`
  - Recreates set for every item in collection
  - **Fix**: Create set once outside filterv: `(let [id-set (set ids)] (filterv #(not (contains? id-set ...)) ...))`

- [ ] **`(contains? #{:water :path} ...)` inline** — `ui/panels/library.cljs:162,171`
  - Set literal created on every evaluation
  - Not a major issue but could be extracted to constant

## Missing Validation/Guards

- [ ] **No bounds check on set-zoom!** — `state.cljs:377`
  - `(max 0.001 (min 10.0 new-zoom))` but no check for NaN
  - If `new-zoom` is NaN, result is NaN (NaN propagates through min/max)
  - **Fix**: Add `(when-not (js/isNaN new-zoom) ...)`

- [ ] **No validation on pan! deltas** — `state.cljs:372-374`
  - `dx`, `dy` not validated
  - Could pan to infinity with bad input

## Data Conversion Overhead

- [ ] **clj->js in postMessage hot path** — `simulation/water/worker.cljs:73,80`
  - `(clj->js ...)` converts Clojure data on every message
  - Worker messages sent frequently during simulation
  - **Fix**: Use pre-structured JS objects for performance-critical messages

- [ ] **js->clj on every LLM JSON parse** — `llm.cljs:601,641`
  - Full conversion from JS to CLJ with keywordization
  - Could be expensive for large responses
  - **Fix**: Access JS objects directly with goog.object/get where possible

## Inefficient Collection Operations

- [ ] **`mapv` where `map` would suffice** — throughout
  - Many uses of `mapv` where result is immediately consumed lazily
  - `mapv` forces full vector realization upfront
  - **Fix**: Use `map` when lazy sequence is fine, `mapv` only when vector needed

- [ ] **`filterv` where `filter` would suffice** — throughout
  - Same issue as mapv
  - Forces full vector realization

## Additional Performance Wins

- [ ] **state/find-area called in render loop** — `canvas/render.cljs:522`
  - `(when-let [area (state/find-area id)] ...)` inside `doseq` for selected IDs
  - O(n) lookup per selected item, called every frame
  - **Fix**: Pass areas map to render function, lookup by ID directly

- [ ] **state/find-plant called in render loop** — `canvas/render.cljs:562,702`
  - Same O(n) lookup pattern for plant selection/tooltip rendering
  - **Fix**: Build `{id -> plant}` lookup once at start of render

- [ ] **plants/get-plant called 3x per plant** — `canvas/plant_render.cljs:531,574,591`
  - `render-plant!`, `render-plant-simple!`, `get-plant-radius` each call it
  - Even though O(1) now, still 3 hash lookups per plant
  - **Fix**: Pass plant-data as parameter from caller, or compute once per plant

- [ ] **Render time tracking allocates every frame** — `canvas/core.cljs:115-118`
  - `(vec (rest new-times))` creates new vector from sequence when buffer full
  - **Fix**: Use ring buffer or subvec: `(subvec new-times 1)`

- [ ] **Each render layer re-reads full state** — `canvas/core.cljs:50-107`
  - State passed to each layer, but each layer does its own `get-in`
  - **Fix**: Destructure once in `render!`, pass specific values to layers

- [ ] **area-in-view? and plant-in-view? repeat bounds destructuring** — `canvas/render.cljs`
  - Each function destructures bounds again
  - **Fix**: Pass pre-destructured min-x, min-y, max-x, max-y as args

- [ ] **concat used in vertex insertion** — `tools/select.cljs:145,256`
  - `(vec (concat ...))` allocates intermediate lazy seq then realizes to vector
  - **Fix**: Use `into` with subvec or direct vector operations

- [ ] **render-selection! iterates selected-ids with find-area/plant each** — `canvas/render.cljs:508-575`
  - For each selected ID, does O(n) lookup
  - With 10 selected items and 100 areas, that's 1000 comparisons per frame
  - **Fix**: Build lookup map once, use for all renders

- [ ] **Texture functions recalculate bounding box** — `canvas/render.cljs:146-151`
  - `(apply min xs)`, `(apply max xs)` for every area, every frame
  - Also creates lazy seqs with `(map first points)` just to find bounds
  - **Fix**: Cache bounds in area map, update only when points change

- [ ] **Math.sin/cos called per texture element** — `canvas/render.cljs:157-158,167-168`
  - Trig functions in nested loops for pattern generation
  - Same pattern positions every frame, results identical
  - **Fix**: Pre-compute texture to offscreen canvas, blit from cache

- [ ] **Plant render functions compute same values** — `canvas/plant_render.cljs:530-545`
  - `radius`, `base-radius`, `stage` calculated in `render-plant!`
  - Same calculations repeated in `render-plant-simple!` and `get-plant-radius`
  - **Fix**: Compute once in caller, pass as struct

- [ ] **69 Math/PI references in render code** — `canvas/plant_render.cljs`, `canvas/render.cljs`
  - `(* 2 Math/PI)` computed repeatedly
  - **Fix**: Define `TWO-PI` constant, use throughout

- [ ] **Color manipulation in render loops** — `canvas/plant_render.cljs`
  - `(darken color 0.2)`, `(lighten color 0.3)` called per shape
  - Same color manipulated multiple times per plant
  - **Fix**: Pre-compute color variants once per species

## Dependency Issues

- [ ] **React 17.0.2 is outdated** — `package.json:21`
  - React 18 has been stable for years
  - Missing out on concurrent features, automatic batching, useId hook
  - **Fix**: Upgrade to React 18.x, update ReactDOM.render to createRoot

- [ ] **No package-lock.json or yarn.lock** — project root
  - Dependency versions not locked
  - Different installs may get different versions
  - **Fix**: Commit lock file to ensure reproducible builds

- [ ] **create-react-class still in dependencies** — `package.json:18`
  - Deprecated package for ES5 React components
  - ClojureScript/Reagent doesn't need this (uses React.createElement)
  - **Fix**: Remove if truly unused

## Focus Management

- [ ] **Canvas focus requires click** — `ui/app.cljs:100,106`
  - Canvas has `tab-index 0` and manual `.focus` call
  - Focus management could be more explicit
  - Users may not realize they need to click canvas for keyboard shortcuts

- [ ] **No focus trap for modal dialogs** — throughout
  - If any modal dialogs exist, focus isn't trapped
  - Tab key would cycle through elements behind modal

## Input Validation in UI

- [ ] **Form inputs accept any text** — throughout UI panels
  - Number inputs don't prevent non-numeric input
  - Range inputs have min/max but can be bypassed via DevTools
  - Should validate server-side (or in state layer)

- [ ] **No character limits on text inputs** — properties panel
  - Name and notes fields can be infinitely long
  - Could cause rendering issues with very long text
  - **Fix**: Add maxLength or truncate on display

---

## Priority Order

### P0 — Correctness Bugs
1. ~~**Fix history save order**~~ — DONE: with-history* wrapper saves after mutation
2. ~~**Batch history for bulk operations**~~ — DONE: with-batch-history, clear-all!, batch remove/add
3. ~~**Properties panel debounce**~~ — DONE: debounced-text-input components

### P1 — Performance
4. **Add spatial indexing** — O(n) plant hit detection is unacceptable
5. ~~**Cache contour lines**~~ — DONE: Added contour-cache atom, recomputes only when data/interval/LOD changes
6. ~~**Reuse water cache canvas**~~ — DONE: Added get-or-create-canvas! that reuses if dimensions match
7. ~~**Memoize plant library search**~~ — DONE: Replaced regex with simple str/includes? (faster + safer)

### P2 — Architecture
8. **Add input validation (malli/spec)** — prevent corrupted state
9. **Split render.cljs** — 1000+ line file is unmaintainable
10. **Centralize coordinate conversion** — duplicated magic numbers
11. ~~**Extract shared data (area-types)**~~ — DONE: garden.data.area-types, garden.util.simplify

### P3 — Robustness
12. **Add error reporting to UI** — silent failures in fill, geotiff
13. **Add timeout to async operations** — geotiff fetch can hang
14. **Handle empty/nil edge cases** — empty library, missing IDs

### P4 — Testing
15. **Test flood fill algorithm** — complex, untested
16. **Test geometry utilities edge cases** — used everywhere
17. **Add performance benchmarks** — catch regressions

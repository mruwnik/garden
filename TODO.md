# Garden App TODO

## Bugs / Issues Found

### LLM Tools

- [x] Missing `remove_plant` tool - Can't move/delete individual plants, LLM had to add duplicate lettuce instead of moving
- [x] Missing `remove_plants_in_area` tool - Can't clear all plants from a specific area
- [x] Limited plant species for flowers - Only sunflowers available, no roses/tulips/etc in plant database

### REPL

- [ ] Shadow-cljs REPL alias bug - `require :as` aliases don't persist between evals, need full namespace paths like `garden.llm/send-message!`

### Chat Behavior

- [ ] Clear garden didn't fully work - When asked to "clear and design", old plants remained initially

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

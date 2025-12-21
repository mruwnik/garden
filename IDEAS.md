## Usage

* ~~some way to genericaly select an area and add notes. The idea being that you can then ask an agent to do something or other in that area~~ ✅ Added Notes field to area properties panel
* ~~properly snapping when drawing, so you can have nice proper borders~~ ✅ Added "Snap" toggle - points snap to grid when drawing areas
* ~~a chat interface to talk with an LLM agent~~ ✅ Chat panel with streaming responses, image attachments, settings UI - click "💬 Ask AI"
* a very well defined way of specifying the garden. Also a way to introduce diffs - so agents can easily modify stuff
* a way to send a picture/map and get a proper garden plan back
* a good prompt that generates nice gardens - you define the basic thngs (house, drive, water source, etc.) and the model returns a garden suggestion

## UI

* ~~Sowing seeds should work somehow. Both marking where they are, and how they're displayed~~ ✅ Added "Life Stage" field (seed/seedling/mature) with distinct visual rendering for each stage
* ~~Random circles don't seem the best way to show things?~~ ✅ Plants now render with type-specific icons (trees with layered crowns, vegetables with leaves, flowers with petals, herbs with leaf clusters)
* maybe also add pictures of some kind?
* ~~better rendering would be nice? Some simple textures or something?~~ ✅ Added rich textures: soil with organic matter for beds, cobblestone pattern for paths, wood grain for structures
* ~~Maybe start by defining general layout? Or by default have soil everywhere?~~ ✅ Added toggleable grass background texture
* ~~Spacing circles to show footprint/mature size. Should be toggleable~~ ✅ Added "Spacing" toggle button in toolbar
* ~~allow editing of drawn areas. Currnently you can just add more points? What about breaking lines, and dragging points?~~ ✅ Complete vertex editing: drag vertices, click edges to insert points, Delete key removes hovered vertices

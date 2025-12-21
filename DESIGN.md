# Garden Planning & Simulation Tool - Design Document

## Overview

A visual garden planning tool that allows users to design garden layouts and simulate plant growth over time. The tool provides value at two levels:
1. **Static planning** - layout, spacing, companion planting
2. **Dynamic simulation** - growth visualization, survival modeling, multi-year projections

---

## Part 1: User Interface

### 1.1 Canvas & Viewport

- **Infinite canvas** with pan/zoom controls
- **Grid overlay** (toggleable) - configurable units (feet, meters)
- **Scale indicator** showing real-world dimensions
- **Layers**: ground/soil, plants, structures, annotations

### 1.2 Core Tools

| Tool | Function |
|------|----------|
| **Select** | Click/drag to select plants or areas |
| **Area** | Draw polygons/rectangles for beds, paths, structures |
| **Plant** | Click to place individual plants, drag for rows/grids |
| **Measure** | Distance and area measurement |
| **Annotate** | Labels, notes, markers |

### 1.3 Plant Placement Modes

- **Single** - click to place one plant
- **Row** - define start/end, auto-space based on species requirements
- **Grid** - rectangular planting pattern
- **Scatter** - naturalistic random placement within an area
- **Seed vs Sapling** - affects initial size and growth timeline

### 1.4 Side Panels

**Left Panel - Plant Library**
- Searchable catalog of plants
- Filter by: type (vegetable, flower, tree), sun needs, zone, spacing
- Drag-and-drop onto canvas
- Custom plant definitions

**Right Panel - Properties**
- Selected item properties
- For plants: species, planted date, current stage
- For areas: soil type, sun exposure, irrigation

**Bottom Panel - Timeline**
- Scrubber to view garden at different dates
- Play/pause simulation
- Speed controls (days/weeks/months per second)
- Key date markers (frost dates, harvest windows)

### 1.5 Environment Configuration

- **Location input**: lat/lon or address lookup
  - Auto-populates: hardiness zone, frost dates, sun angles, avg temps
- **Manual overrides**: soil type, microclimate adjustments
- **Weather data**: option to import historical or use averages

### 1.6 Visualization Modes

| Mode | Description |
|------|-------------|
| **Plan view** | Top-down, schematic (default) |
| **Realistic** | Rendered plant imagery at current growth stage |
| **Spacing** | Shows plant footprints and overlap warnings |
| **Sunlight** | Heatmap of sun exposure through the day/year |
| **Harvest** | Highlights what's ready to harvest at current date |

---

## Part 2: Modeling

### 2.1 Static Model (No Simulation)

Useful on its own for garden planning without running simulations.

**Plant Data (per species)**
```
- mature_size: { width_cm, height_cm }
- spacing_min_cm: distance needed from other plants
- sun_hours_min: minimum direct sun hours per day
- sun_hours_ideal: optimal direct sun hours per day
- companions: [species that grow well nearby]
- antagonists: [species to avoid placing nearby]
- root_depth_cm: typical root system depth
- water_needs_mm_per_week: water requirement
```

**Static Analysis Features**
- **Spacing validation** - warn when plants are too close
- **Companion planting hints** - suggest beneficial pairings
- **Sun conflict detection** - tall plants shading sun-lovers
- **Bed utilization** - percentage of area used

### 2.2 Dynamic Model (Simulation)

**Growth Model (per species)**
```
- germination_days: time from seed to sprout
- growth_curve: function(age, conditions) → size
- maturity_days: time to full size
- lifespan: annual | biennial | perennial(years)
- growth_rate_mm_per_day: size increment under ideal conditions
```

**Survival Model**
Simple probabilistic model:
```
daily_survival_probability = base_rate
  × temperature_factor(current_temp, ideal_range)
  × water_factor(soil_moisture, needs)
  × competition_factor(neighbor_density)
  × establishment_factor(age)  // young plants more vulnerable
```

When a plant "dies" (random check fails), it's marked as dead and stops growing.

**Environmental Factors**
```
temperature_factor:
  - in ideal range: 1.0
  - outside range: degrades linearly to 0 at lethal temps

water_factor:
  - soil_moisture_mm vs water_needs_mm_per_week
  - deficit calculated, survival degrades with deficit magnitude

competition_factor:
  - based on root zone overlap percentage
  - 0% overlap: 1.0
  - 100% overlap: species-specific competition_tolerance (0.7-0.95)
```

### 2.3 Simulation Engine

**Time Steps**
- Internal: daily calculations
- Display: configurable (show every day, week, month)

**Per Time Step**
1. Update environmental conditions (temp, moisture from weather data or model)
2. For each living plant:
   - Calculate survival probability → random check → potentially mark dead
   - If alive, calculate growth increment → update size
3. Recalculate shading/competition maps
4. Record state for timeline scrubbing

**Monte Carlo Mode**
- Run N simulations with different random seeds
- Show probability distributions: "80% of simulations, this tomato survives"
- Visualize as opacity or confidence bands

### 2.4 Plant Database Schema

```
species:
  id: string
  common_name: string
  scientific_name: string

  # Physical (all lengths in cm)
  mature_height_cm: number
  mature_spread_cm: number
  root_depth_cm: number
  growth_habit: bush | vine | tree | ground_cover

  # Requirements
  hardiness_zone_min: number (1-13)
  hardiness_zone_max: number (1-13)
  sun_hours_min: number (hours/day)
  sun_hours_ideal: number (hours/day)
  water_mm_per_week: number
  soil_ph_min: number
  soil_ph_max: number

  # Timing
  days_to_germination: number
  days_to_maturity: number
  lifespan_type: annual | biennial | perennial
  lifespan_years: number (if perennial)

  # Simulation params
  base_daily_survival: number (0.0-1.0, e.g., 0.999)
  temp_ideal_min_c: number
  temp_ideal_max_c: number
  temp_lethal_min_c: number
  temp_lethal_max_c: number
  drought_days_tolerated: number (days without water before stress)
  growth_rate_mm_per_day: number (under ideal conditions)
  competition_tolerance: number (0.0-1.0, survival factor at full overlap)

  # Relationships
  companions: [species_id]
  antagonists: [species_id]
```

### 2.5 Unit Conventions

All stored values use metric base units for consistency:
- **Length**: centimeters (cm) for plant dimensions, millimeters (mm) for small increments
- **Temperature**: Celsius (°C)
- **Water**: millimeters (mm) - equivalent to liters per square meter
- **Time**: days for growth, hours for sun exposure
- **Area**: square centimeters (cm²) or square meters (m²)

Display layer converts to user-preferred units (imperial/metric).

### 2.6 Future Enhancements (Out of Scope for V1)

- Pest/disease modeling
- Pollination requirements
- Yield estimation (kg per plant)
- Soil nutrient depletion (N/P/K in g/m²)
- Irrigation planning
- 3D visualization
- AI-suggested layouts based on goals

---

## Implementation Priority

**Phase 1: Static Planning Tool**
- Canvas with pan/zoom
- Area drawing tools
- Plant placement (single, row)
- Basic plant library
- Spacing validation

**Phase 2: Basic Simulation**
- Timeline UI
- Simple growth model (size over time)
- Binary survival (alive/dead)
- Location-based climate defaults

**Phase 3: Rich Simulation**
- Full survival probability model
- Monte Carlo runs
- Weather data integration
- Visualization modes

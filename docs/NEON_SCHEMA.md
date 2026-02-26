# Neon UI schema (v1)

This repo supports a **schema-driven Neon UI** via a JSON file named:

- `assets/bassshakertelemetry/neon/neon_schema.json`

The schema is shipped in the mod JAR (built-in), and may be overridden by a UI bundle on disk:

- `config/bassshakertelemetry/ui_bundle/` (manual override)
- `config/bassshakertelemetry/ui_bundle_remote/` (auto-updated cache)

## Top-level structure

```json
{
  "version": 1,
  "screens": {
    "telemetry_config": {
      "titleKey": "...",
      "root": { "type": "panel", "layout": "vertical", "children": [ /* ... */ ] }
    },
    "advanced_settings": {
      "titleKey": "...",
      "root": { "type": "panel", "layout": "vertical", "children": [ /* ... */ ] }
    }
  }
}
```

- `version`: schema version (currently `1`).
- `screens`: map of screen id -> screen definition.
- `titleKey`: optional translation key used for the screen title.
- `root`: required root node for the screen.

## Nodes

Every node must contain:

- `type`: one of the node types listed below.

Common optional layout hints (v1):

- `padding` (panel only in practice): integer px.
- `spacing` (panel only in practice): integer px between children.
- `width`, `height`: integer px (height is respected for layout in v1).

### `panel`

Container node.

Fields:

- `layout`: `"vertical"` or `"horizontal"`.
- `padding`: integer px (optional).
- `spacing`: integer px between children (optional).
- `children`: array of nodes.

### `label`

Static text.

Fields:

- `textKey`: translation key (preferred)
- `text`: literal fallback text

### `button`

Clickable action.

Fields:

- `id`: optional stable id.
- `textKey` / `text`: label.
- `action`: string action id.
- `bind`: optional config field name associated with this button.

### `toggle`

Boolean on/off control.

Fields:

- `id`: optional stable id.
- `textKey` / `text`: label.
- `bind`: config field name.
- `value`: optional default.

### `slider`

Numeric range slider.

Fields:

- `id`: optional stable id.
- `textKey` / `text`: label.
- `bind`: config field name.
- `min`, `max`, `step`: numeric range.
- `format`: optional display format.
- `value`: optional default.

Supported `format` values (v1):

- `percent`: treat the value as 0..1 and display as 0..100%.
- `percentRange`: display the slider’s normalized position as 0..100% (works for ranges like 0..2).
- `pct`: display the value as an integer with `%` (works for ranges like 10..90).
- `ms`, `hz`, `db`: display as an integer with unit suffix.
- `int`: display as an integer.

### `cycle`

Cycles through discrete options.

Fields:

- `id`: optional stable id.
- `textKey` / `text`: label.
- `bind`: config field name.
- `options`: array of strings.
- `value`: optional selected index.

### `spacer`

Vertical/horizontal spacing block.

Fields:

- `size`: integer px.

## Layout behavior (v1)

The v1 layout engine is intentionally simple:

- `panel (vertical)`: lays children top-to-bottom using each child’s preferred height.
- `panel (horizontal)`: divides available width evenly across children.
- `height` on a node overrides its preferred height.

If the schema is missing/invalid, the mod falls back to hardcoded screens.

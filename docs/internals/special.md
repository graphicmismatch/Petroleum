# Special

Nodes that don't belong to any other family. The package maps to the
"Special" category in the editor's node menu (`Gui.NodeCatalog`).

- **TurretNode** - fires a burst of pulses per trigger. Ports: `trigger`,
  `count` (default `4`), `interval` (seconds, default `0.05`). Output `out`.

  A **rising edge** on `trigger` (convention 8, via a `Dsp.EdgeDetector`)
  arms `round(count)` shots and fires the first one on that same sample.
  Each later shot lands `interval` seconds after the previous one, rounded to
  samples and floored at `2` so consecutive shots stay distinct rising edges
  (a 1-sample spacing would hold `out` high and read downstream as one
  edge). `interval` is read when each shot fires, so it can be modulated
  mid-burst. A new edge mid-burst restarts the burst rather than queueing.

  Scheduling uses absolute sample numbers (`nextShot`, `NEVER =
  Long.MAX_VALUE`), the same style as `Conditionals.ClockDividerNode`, so per
  convention 9 it tracks `expectedBlockStart` and drops any in-flight burst
  when `context.sampleIndex()` doesn't match where the previous block ended.

# Patch (`Petroleum.Patch`)

The wiring/execution engine that [Graph](graph.md) deliberately leaves out:
`Graph` defines what a `Node` is, but nothing there connects nodes together
or runs them in order. `Patch` is that missing piece, built only against the
existing `Node`/`Port`/`NodeContext` contract.

## `Connection`

An immutable record: `Connection(Node source, String sourceOutput, Node
destination, String destinationInput)`. Ports are named here (not indexed)
so callers - in practice the [Gui](gui.md) package - can wire nodes without
knowing port order; `Patch` resolves every name to an index once, at
construction, the same "resolve once, at wiring time" approach `graph.md`
prescribes for `Node.inputIndexOf`/`Node.outputIndexOf`.

## `Patch`

Constructed from a node list and a connection list:

- Topologically sorts the nodes with a DFS (three-colour, white/grey/black),
  validating each connection's source and destination against the node set
  in the same pass that builds the DFS's `outgoing` adjacency map
  (`requireInPatch`, shared by both endpoints) - a `Connection` referencing a
  node outside the patch's node list throws `IllegalArgumentException`
  (naming the offending node and whether it was the source or destination)
  before the sort itself runs, rather than either NPEing on the lookup or
  silently inserting a stray, never-processed node. The wiring loop below
  trusts this validation completely: it looks up each connection's endpoints
  in the resulting order-index without a second null check, since anything
  that could have failed already threw here.
- **Cycles are allowed.** A connection whose destination is still on the DFS
  stack when it's visited (a "grey" node, i.e. an ancestor of the node doing
  the visiting) is a *back edge* - it closes a cycle - and is recorded as a
  `feedbackConnection` instead of being treated as an ordering dependency;
  `visit` skips recursing into it, so it plays no part in the topological
  order and can never itself cause a further cycle. Removing exactly the
  back edges DFS finds is what makes the rest of the graph a DAG, so this
  handles nested/overlapping cycles and self-loops (a node wired to its own
  input) without special-casing them. See "Feedback connections" below for
  how a cut edge is actually executed.
- Requires exactly one [`AudioSink`](sinks.md) among the nodes - either an
  `AudioOutNode` (mono) or a `StereoAudioOutNode` (stereo) - or throws
  `IllegalStateException`. Which concrete type was found is recorded once,
  at construction (`isStereo()`), and determines which pair of render
  methods below a caller must use.
- Resolves every `Connection`'s port names to indices immediately
  (`Node.inputIndexOf` for the destination, `Node.outputIndexOf` for the
  source - both symmetric `Node` interface defaults, see
  [Graph](graph.md)), throwing `IllegalArgumentException` on an unknown port
  name, and again if the same node/index pair was already looked up (both
  checked against the node list before the port lookup even runs).
- Rejects two connections that drive the same destination input: construction
  tracks a local `boolean[][] connected` flag per node/input slot (separate
  from the block arrays themselves, since two different-looking float
  arrays can't otherwise be told apart as "default" vs. "wired"); if a
  connection's resolved `(destIndex, destInputIndex)` slot is already
  flagged, the constructor throws `IllegalStateException` naming the
  destination node and the input port - an input can only ever be fed by
  one source, and silently letting the second connection overwrite the
  first would hide a wiring mistake instead of failing loudly.

## Execution model: block aliasing, zero-copy

`Patch` processes audio in blocks, not one sample at a time - see
[Graph](graph.md) for the `Node.process(float[][] inputs, float[][]
outputs, int frameCount, NodeContext context)` contract this is built
against.

`Patch(List<Node>, List<Connection>)` delegates to a **private**
`Patch(List<Node>, List<Connection>, int blockCapacity)` with `blockCapacity
= DEFAULT_BLOCK_CAPACITY` (1024 frames) - every `Patch` in the codebase is
built through the public two-argument form, so every `Patch` instance,
nested or not, shares the same capacity (see `modules.md` for why this is
exactly what lets `PatchNode.process` skip chunking). `blockCapacity` sizes
every array the patch will ever use for `renderBlock` calls up to that many
frames; a `renderBlock` call requesting more than `blockCapacity` frames
throws `IllegalArgumentException` rather than silently truncating or
reallocating.

All buffers are allocated once, at construction, and then wired by aliasing
rather than copying:

- Every node gets one `float[blockCapacity]` per output port
  (`outputBlocks[nodeIndex][portIndex]`).
- Every node also gets one `float[blockCapacity]` per input port
  (`inputBlocks[nodeIndex][portIndex]`), initially a dedicated array filled
  (`Arrays.fill`) with that port's `Port.defaultValue()` across its full
  capacity.
- For each validated `Connection`, the destination's input slot is
  **replaced** with a direct reference to the source's output array:
  `inputBlocks[destIndex][destInputIndex] = outputBlocks[srcIndex][srcOutputIndex]`.
  An unconnected input keeps its own dedicated default-filled array forever;
  a connected input becomes a second reference to the producer's output
  array - no per-block copying, ever.

This aliasing is only safe because of two invariants the rest of the system
upholds: nodes never write into their `inputs` arrays (see
[Graph](graph.md)), and the topological order guarantees a producer's
`process()` runs (and finishes writing its output block) before any
consumer that aliases it runs. Each output block therefore has exactly one
writer (its owning node, once per `renderBlock` call) and any number of
readers.

`order` is stored as a plain `Node[]` (not a `List`) so the render loop
indexes it directly rather than paying `ArrayList.get` overhead once per
node per block.

### Feedback connections

A back edge found during the DFS (see above) can't be wired by aliasing -
its source hasn't run yet relative to its destination in `order` (that's
what made it a back edge), so there is no "current" output block to alias.
Sample-accurate feedback is also not on the table: `process()` runs one
node's whole block at a time ([convention 2](conventions.md)), so
there is no per-sample interleaving of a feedback loop's two halves within a
single `renderBlock` call - the same reason block-based hosts like Pure Data
or Max/MSP need a delay somewhere in a feedback path.

So a feedback connection gets its own dedicated `float[blockCapacity]`
buffer (a `FeedbackTap`, holding the source node/output index and the
buffer), default-filled with the destination port's `Port.defaultValue()`
exactly like an unconnected input - the destination reads this buffer as its
input for the whole block, same as any other input. After every node in
`order` has run for that block, `renderInto` walks the `feedbackTaps` array
once and `System.arraycopy`s each tap's *source* output block into its
buffer - so the destination sees the source's previous-block output, not a
copy made mid-render. That is one block of latency (1024 frames at
`DEFAULT_BLOCK_CAPACITY`, ~23 ms at 44.1 kHz) inserted exactly on the back
edge DFS chose to cut, and nowhere else in the graph - every other
connection is still the zero-copy alias described above.

`renderBlock(float[] outBuffer, int numSamples, NodeContext context)` then
runs the whole graph forward exactly once per call, with no allocation:
a private `process(numSamples, context)` walks the topo-sorted `order`
array, calling `nodes[i].process(inputBlocks[i], outputBlocks[i], numSamples,
context)` for each node in turn (aliasing means a downstream node's
`inputBlocks` already contain whatever its upstream producer just wrote,
with no copy step in between) and applies the feedback taps; `renderBlock`
then `System.arraycopy`s the sink's `in` block into `outBuffer[0,
numSamples)` and calls `context.advance(numSamples)` once for the whole
block (not once per sample). Calling `renderBlock` (or `renderNested`)
against a `Patch` whose sink is a `StereoAudioOutNode` throws
`IllegalStateException` instead of silently reading only the left channel -
`requireMonoSink()`/`requireStereoSink()` guard both pairs of render
methods so calling the wrong one fails immediately rather than producing a
half-wired render.

### Stereo rendering

`renderBlockStereo(float[] left, float[] right, int numSamples,
NodeContext context)` and `renderNestedStereo(float[] left, float[] right,
int outOffset, int numSamples, NodeContext context)` are the stereo
counterparts of `renderBlock`/`renderNested`, valid only when `isStereo()`
is true: they run the identical shared `process(...)` step, then copy
`StereoAudioOutNode`'s `inL`/`inR` blocks into `left`/`right` instead of a
single mono block into one buffer. Everything about block aliasing, feedback
taps, and one-`process()`-call-per-node above applies unchanged - stereo
only changes which sink port(s) get copied out at the end, not how the graph
in between is built or executed. See [Audio](audio.md) for how `AudioEngine`
picks between the mono and stereo render methods based on `isStereo()`.

### `renderNested`

`renderNested(float[] outBuffer, int outOffset, int numSamples, NodeContext
context)` is the same render with two differences: it writes at an offset into
the caller's buffer, and it does **not** advance the context.

It exists for [`Modules.PatchNode`](modules.md), which runs a whole patch as one
node inside another patch. The clock belongs to the outermost `Patch`, which
advances it once per block after every node has run. If a nested patch advanced
it too, the graph would step several samples per block and every node keying off
`sampleIndex` would drift. Passing the outer context through unchanged also
means an inner node sees exactly the clock a top-level node sees, which is what
keeps one-shot session detection working across the module boundary.

`PatchNode` only ever builds its embedded patch around a plain `AudioOutNode`
- a patch used as a node stays mono internally, even inside an outer patch
that is itself stereo - so only `renderNested` sees use today;
`renderNestedStereo` exists for symmetry with `renderBlockStereo` and for any
future caller that embeds a stereo subpatch, but `Modules` does not call it.

`hasOneShots()` exposes the same array `isFinished()` scans, so a caller can
tell "no one-shots, so never finishes" apart from "one-shots, none idle yet".

## Knowing when a sound has ended: `isFinished()`

`isFinished()` reports whether a one-shot patch has run to completion, so a
caller can render or play until the sound stops instead of guessing a
duration.

At construction, `Patch` collects every node implementing
[`Graph.OneShot`](graph.md) into a flat array. `isFinished()` returns true when
all of them are idle. The block path is untouched - the scan happens once, and
`renderBlock` only sets a `rendered` flag.

Two cases deliberately return `false`:

- **Before the first `renderBlock`.** Envelopes start idle and only become
  active once a trigger reaches them, which cannot happen until a block runs.
  Without this guard the natural `while (!patch.isFinished()) renderBlock(...)`
  loop would exit before producing a sample.
- **A patch with no `OneShot` nodes.** A graph of oscillators and filters has
  no defined end, so "finished" is not a state it can reach. Reporting `true`
  would cut off a drone patch immediately.

A [module](modules.md) is a `OneShot` only when the patch inside it holds one,
decided once at construction. That keeps this scan honest: a module wrapping a
drone stays out of the array entirely rather than having to answer `true` or
`false`, either of which would be wrong.

`isFinished()` is a non-realtime query, meant for the render loop and the GUI.
It reads `volatile` fields written by the audio thread and needs no lock, in
the same one-writer/one-reader arrangement `ConstantNode` uses.

## Visualization taps: `copyOutputBlock` / `copyInputBlock`

`copyOutputBlock(Node node, int outputIndex, float[] dest)` and
`copyInputBlock(Node node, int inputIndex, float[] dest)` copy a snapshot of
one node's most recently rendered output (or input) block into a
caller-supplied array, returning the number of samples copied
(`min(dest.length, blockCapacity)`), or `0` if the node isn't in this patch
or the port index is out of range. The [Gui](gui.md) package uses these to
drive per-node waveform displays; `copyInputBlock` exists mainly so the
`AudioOutNode` (which has no outputs) can be visualized via its `in` block.

These taps are deliberately unsynchronized. The render thread may be
mid-`renderBlock` while the JavaFX thread copies, so a snapshot can contain
a mix of the previous and current block. That is acceptable for display and
only for display: `float` reads/writes are atomic in Java (no torn values,
only stale ones), the copy never blocks or slows the render path, and the
snapshot must never be fed back into audio processing. The alternative - a
lock or a triple-buffer handoff - would put synchronization on the realtime
path to fix a purely cosmetic, sub-frame artifact.

## Multithreading considered and rejected

The patch graph is a DAG with genuine branch-level parallelism available -
independent chains feeding a mixer could, in principle, run on separate
threads. This was considered for `renderBlock` and rejected:

- Per-node, per-block work is on the order of microseconds (a block of 1024
  frames at 44.1 kHz is already a ~23 ms real-time budget for the *entire*
  graph, not one node), so the actual work being parallelized is tiny
  relative to any thread handoff.
- Thread handoff and synchronization (queuing work, waiting on a barrier
  before the mixer/`AudioOutNode` can run) costs on the order of the work
  itself at this granularity - splitting work across threads would spend as
  much time coordinating as computing.
- Typical patches built in the [Gui](gui.md) editor are 5-20 mostly-chained
  nodes, not wide fan-out graphs - the critical path (longest chain) is
  close to the total work across all nodes, so even free parallelism would
  buy close to 1x speedup in the common case.
- Nodes with stateful recurrences - filter history (`BiquadFilterNode`,
  `OnePoleFilterNode`), delay-line/comb-filter ring buffers, phase
  accumulators (`PhasorNode`) - are inherently sequential *within* a node
  across samples; there is no valid way to parallelize a single node's
  block processing across its own samples, only across independent nodes.
- Confining all node state and all `process()` calls to a single render
  thread means every node can stay lock-free by construction - no node
  needs to reason about concurrent access to its own fields. Introducing
  cross-node parallelism would reintroduce exactly that reasoning burden
  for a return that the three points above say isn't there.

If render throughput ever does become a bottleneck, the productive next
step is pipelining, not intra-graph parallelism: a render-ahead thread that
double-buffers blocks (rendering block N+1 while block N is being written
to the audio device) overlaps rendering with I/O without touching the
single-threaded, lock-free execution of the graph itself.

## Immutability and rebuilding while playing

A `Patch` is treated as an immutable snapshot once built. The [Gui](gui.md)
and [Audio](audio.md) packages both lean on this: editing the graph in the
UI builds a *new* `Patch` from the current node/connection set and hands it
to `AudioEngine.play(...)` again, which hot-swaps it into the running
playback thread rather than tearing anything down - see `audio.md` for the
swap mechanics.

Rebuilds are expected to **reuse the existing `Node` instances** rather than
reconstructing them - `Petroleum.Gui.GraphModel` holds the same long-lived
node objects across edits and only the `Connection` list (and therefore the
`Patch` wrapper) changes. Constructing fresh nodes on every edit would reset
`DelayNode`'s buffer, noise generators' seeded state, and `PhasorNode`'s
running phase, causing an audible glitch on every wiring change made while
sound is playing.

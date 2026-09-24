# Engineering conventions

The rules the engine is built on. Several of them exist because breaking them
caused a real defect; the rationale is recorded with each one.

## 1. `Graph` holds no node implementations

`Petroleum.Graph` contains the node abstraction only: `Node`, `AbstractNode`,
`NodeContext`, `Port`, `OneShot`, `Stateful`, `NodeFactory`. New filter, effect,
mixer and conditional types belong in their own sibling package, or as new
files in the existing `Filters`/`Effects`/`Mixers`/`Conditionals` packages,
never inside `Graph` itself or a `Graph.Nodes.*` subpackage.

## 2. `Node.process()` never allocates

Nodes process audio in blocks, not one sample at a time:

```java
process(float[][] inputs, float[][] outputs, int frameCount, NodeContext context)
```

Inputs and outputs are primitive arrays indexed first by port position, then by
sample index within the block (`inputs[portIndex][sampleIndex]`). No `Map`, no
boxed `Float`, no per-block or per-sample object creation.

A node must never write into its `inputs` arrays. An input port's backing array
may be aliased directly to the producing node's output array - zero-copy wiring
set up once, at `Patch` construction - so mutating it would corrupt the
producer's own output and every other consumer sharing that array. Nodes read
`inputs` and write `outputs`, never the reverse.

Unconnected input ports are filled with that port's `Port.defaultValue()`
across the block's full capacity by `Patch` at construction time. Port name to
index resolution happens once, at patch-wiring time, never per block or per
sample. `NodeContext.advance` is called once per block by `Patch`.

Parameter inputs are still read one sample at a time inside a node's per-sample
loop, since a parameter can be modulated within a block. Convention 3 covers
how that coexists with coefficient caching.

## 3. Cache expensive per-sample coefficients

Any node whose parameters require trigonometry, `pow` or `exp` to become usable
coefficients checks, inside its per-sample loop, whether the relevant inputs or
the sample rate actually changed since the last sample, and recomputes only
when they did. Most patches hold a knob steady far more often than they sweep
it. See `BiquadFilterNode`, `OnePoleFilterNode`, `CompressorNode` and
`BitcrusherNode`.

## 4. Avoid `%` in circular-buffer indexing

Ring-buffer wraparound - delay lines, comb and allpass filters - uses
`if (++index >= length) index = 0;` rather than `index = (index + 1) % length`.

## 5. `IOscillator.getSample(phase)` expects pre-wrapped phase

Callers pass phase already wrapped to a single cycle in `[0, 2*PI)`.
Implementations such as `Sawtooth` and `Triangle` do not re-wrap it themselves.

## 6. Factories register a name against a self-contained supplier

`OscillatorFactory` and `NodeFactory` are name-to-`Supplier` registries. Most
entries wrap a no-arg constructor; some bake in a fixed default argument for a
type that needs one, such as `BiquadFilterNode`'s filter type, `MixNode`'s
default four-input weight map, and the default arity of `SwitchNode` and
`SplitterNode`. The factory supplies a sane default; the class itself does not
change.

Types needing a value with no sane universal default stay unregistered and are
constructed directly: `Wavetable`, `DualOscillator` and `Wavefolder` need a
base oscillator, and `OscillatorNode` needs an `IOscillator`.

## 7. Explanatory documentation lives in this directory

The source carries no Javadoc and no inline comments. Design rationale,
algorithm references, grammars and port lists are recorded in the documents
alongside this one, one per package. Code changes and its document changes
together.

## 8. A trigger is an ordinary signal, never an event object

Nodes that start on an event, such as `EnvelopeNode` and `RampNode`, take an
ordinary `float` input port carrying `1.0` for exactly one sample and `0.0`
otherwise. They detect a rising edge (`previousTrigger <= 0 && trigger > 0`)
rather than any non-zero value, so a trigger held high across a whole block
fires once rather than every sample.

`TriggerNode` produces such a pulse, and so does any `SelectNode`,
phasor-through-threshold or `ConstantNode` the user wires up, with no
special-casing.

A side-channel event queue on `NodeContext` or `Patch` would put allocation, or
a preallocated ring, in the realtime path, and would split signal data into two
kinds that only some nodes understand. Keeping triggers as plain samples is
what lets convention 2 hold unchanged.

## 9. Per-session state keys off `NodeContext`, never node lifetime

`Node` instances are long-lived and shared across every `Patch` built from a
graph, while `AudioEngine` creates a fresh `NodeContext` per playback loop and
per `renderToWav`. Anything meaning "once per sound" or "once per render" must
therefore be driven by `context.sampleIndex()`, not by a flag on the node.

`TriggerNode` detects a new session by comparing `context.sampleIndex()`
against where its previous block ended, not against zero, so consecutive
single-block renders still read as two sessions.

Getting this wrong is what made Render-to-WAV write silence after a Play: the
node had already spent its one-shot flag. A manual re-arm or fire API is not
the fix.

## 10. A macro may only target a node that is really in the document

`Gui.NodeView`'s shadow `ConstantNode`s are a GUI implementation detail with no
`NodeView`, so they never reach the node list produced by
`GraphEditor.toDocument()`. Binding a macro to one produced a macro that was
dropped on save and drove nothing, so promoting a port knob materializes the
shadow into a real `Constant` node first, via `GraphEditor.materializeKnob`.

`PatchCodec.save` throws on an unreachable macro target rather than skipping
it; a silent `continue` is what hid the original defect.

The other half of the invariant is deletion: `GraphEditor.removeNode` must drop
every macro bound to a knob that node owned, via `NodeView.ownedKnobs()`, or
the next save fails on a macro whose target is gone.

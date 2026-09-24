# Modules (`Petroleum.Modules`)

Lets a saved patch be used as a **node** inside another patch. This is how a
user builds a custom instrument or a custom effect: save it once as an ordinary
`.ptr` file, then drop that file into any other graph as a single box.

There is no separate "instrument format". A module is a patch file, and a patch
file is a module - the same file the editor saves is the file the catalog loads.

## The two-port rule

A patch becomes a node by a fixed mapping:

- its **`AudioOutNode`** becomes the module's single output port, `out`;
- each of its **macros** becomes an input port, named after the macro.

Nothing else about the inner graph is visible from outside. A patch with no
macros is a pure generator with one output. A patch with a macro named `in`,
wired into the head of its chain, is an effect - the parent drives that port
with a signal and reads the processed result from `out`.

The port's `defaultValue()` is the macro's current knob value, so a module
dropped on the canvas sounds the way it sounded when it was saved, before the
user touches anything.

## `PatchNode`

Holds the loaded `PatchDocument`, plus an inner `Patch` built from it once at
construction. `process` renders that inner patch straight into its own output
block:

```java
inner.renderNested(outputs[OUT], 0, frameCount, context);
```

Three things make this safe on the audio thread:

- **The inner patch is built once**, at construction, not per block. It is a
  normal `Patch`, so it is topologically sorted with its buffers preallocated,
  exactly like the outer one.
- **`renderNested` does not advance the `NodeContext`**, and the module passes
  the *outer* context straight through rather than keeping one of its own. So
  every inner node sees the same `sampleIndex` as every outer node, and the
  outer `Patch` still advances the clock exactly once per block. This is what
  keeps [convention 9](conventions.md) working through a module boundary: an
  inner `TriggerNode` detects a new session against the same clock a top-level
  one would, so Play and Render-to-WAV both re-fire it.
- **`process` allocates nothing.** The only per-block work is binding the
  bridges and one `arraycopy` per port.

There is no chunking loop: `Patch`'s three-argument constructor (the one that
takes an explicit `blockCapacity`) is private, so every `Patch` in the
codebase - inner or outer - is built through the public two-argument form and
therefore always uses `DEFAULT_BLOCK_CAPACITY`. `process`'s `frameCount` can
never exceed the outer patch's capacity (`Patch.renderInto` enforces that),
which is always the inner patch's capacity too, so a single `renderNested`
call always covers the whole block. An earlier version chunked defensively in
case a parent were ever built with a larger capacity; since that constructor
path doesn't exist, the chunking was dead weight around a call that always
ran its loop body exactly once, and was removed along with it.

### `PortBridgeNode`

Each macro's target `ConstantNode` is **replaced** inside the inner patch by a
`PortBridgeNode`, through the substitution map `PatchDocument.buildPatch(...)`
now accepts. The bridge is a source node whose `process` copies the parent's
input block for that port into its own output block.

The replacement is what makes an input port carry **audio**, not just a knob
value. Reading `inputs[i][0]` once per block and calling `setValue` would have
been simpler, but it would quantize every input to the block rate - about 23 ms
- which is fine for a knob and useless for an effect's signal input. Going
through a bridge, a module input is sample-accurate: feeding a sine through a
pass-through module returns the sine bit-identically.

The substitution happens in the built `Patch` only. The `PatchDocument` keeps
its original `ConstantNode`s and its macro list untouched, so re-saving a module
writes back the same patch that was loaded.

### `MutedNode`

`PatchNode`'s constructor adds one more entry to the same substitution map for
every `Sources.MidiInputNode` and every `Sinks.VirtualLineOut`
(`VirtualLineOutNode`/`StereoVirtualLineOutNode`) it finds anywhere in the
loaded document's node list, mapping each to a `MutedNode` built with that
node's exact input/output port lists. `MutedNode.process` does nothing, so its
output blocks stay at their construction-time zero and its input blocks are
simply never read - the same "empty `process`" shape `Sinks.AudioOutNode`
already uses, just reused for a node that isn't the module's own terminal.

This is what makes "import patch as node ignores MIDI inputs and virtual line
outs" true structurally rather than by convention: a live MIDI device
connection or a virtual-cable connection made while editing the original,
standalone patch has no business continuing to drive audio once that patch
becomes one node embedded inside a bigger one - the same reasoning that keeps
`MidiInputNode`'s and `VirtualLineOutNode`'s chosen device out of
`Stateful`/serialization entirely (see [Sources](sources.md),
[Sinks](sinks.md)). Substituting rather than deleting keeps every wire to and
from the muted node resolvable - `outputIndexOf`/`inputIndexOf` only need the
replacement to expose the same port *names*, not the same behavior - so a
`MidiInputNode`'s dependents still see a well-formed (silent) `pitch`/`gate`/
`velocity` triple instead of a broken connection, and nothing else in the
loaded graph needs to change shape around the substitution.

### `OneShotPatchNode`

`OneShot` marks a node with a defined end, and `Patch.isFinished()` is the AND
of every one of them. A module cannot answer that question for itself: it has an
end only if its *contents* do.

So the decision is made once, at construction. `PatchNode.create(...)` scans the
document and returns `OneShotPatchNode` - the subclass that implements `OneShot`
and delegates `isIdle()` to the inner patch - only when the document holds a
one-shot node. A module built from a continuous patch is not a `OneShot` at all,
so it stays out of the finish decision entirely, exactly like a bare oscillator.

Returning `true` or `false` from a single always-`OneShot` class was tried first
and is wrong either way: `true` lets a still-sounding module be declared
finished, and `false` stops a parent that has its own envelope from ever
finishing, which silently breaks playback stop.

`create(...)` is the only public entry point; the constructor is
package-private, so a caller cannot bypass the classification.

## Editing a subpatch in place

A `PatchNode` already dropped into a graph - loaded from a file or placed
empty via `Gui.NodeCatalog`'s "New Subpatch" entry (an `AudioOutNode` and
nothing else, the minimum a `PatchDocument` needs to pass `buildPatch`'s
validation) - gets an "Edit" header button in `Gui.NodeView`, next to the
"?" docs button, following the same `showEditButton`/`GraphEditor.setOnOpen*`
pattern already used for `TrackerNode`/`InstrumentNode`.

There is no separate nested-editor screen. `Gui.SynthApp` reuses the single
graph editor: clicking Edit pushes the *parent* document (`editor.toDocument()`)
and the clicked `PatchNode` instance onto a `Deque<SubpatchFrame>`, then loads
the subpatch's own `getDocument()` straight into the same `GraphEditor`,
replacing what's on screen. A label and a "Done Editing Subpatch" button
appear above the canvas whenever that stack is non-empty. Because it's the
same editor, `Save Patch...`/`Open Patch...` work unmodified while nested -
that's what makes a subpatch independently saveable (write it out to its own
`.ptr` file) and independently editable (or swap a different saved patch
in as its content), without either operation needing to know about nesting
at all.

Clicking Done pops the stack, calls `editor.toDocument()` to capture whatever
is now on screen, and passes that straight to `PatchNode.create(...)` -
**a new node instance**, never a mutation of the old one, because `PatchNode`'s
input ports are derived from the document's macros at construction and are
`final`; if editing added, removed, or renamed a macro, the port list has to
change, and a `Node`'s ports can't change out from under callers holding a
reference to it. `PatchDocument.replaceNode(old, new)` then swaps that new
instance into the popped parent document - by identity in the node list, in
`positions`, and in `portValues`, and by rewriting the `source`/`destination`
of every `Connection` that touched the old instance - before the parent is
reloaded into the editor. A wire to a port the edit removed simply has
nothing to reconnect to and is dropped silently by `GraphEditor.loadDocument`,
the same degradation an outright macro deletion already causes elsewhere.

`GraphEditor.loadDocument` always clears undo history and the dirty flag,
since it's also used for "open an unrelated file"; both descending into a
subpatch and returning from one go through it, so **entering or leaving a
subpatch edit clears the parent's undo stack**, and `SynthApp` calls the
editor's `markDirty()` explicitly after restoring the parent - otherwise the
freshly-loaded parent would read as saved even though the subpatch inside it
just changed.

Switching away from the graph editor entirely (Quick Sounds) is refused while
the stack is non-empty, so the parent document captured on the stack can't be
abandoned by navigating around it instead of clicking Done.

## Nesting and cycles

A module may contain modules. The inner document is embedded **by value** in the
file (see [Serialization](serialization.md)), so loading recurses through the
JSON and terminates. There is no path to resolve, so a patch cannot reference
itself, and no cycle guard is needed.

Two modules loaded from the same file are two independent `PatchNode`s with
their own node instances and their own inner state. Sharing them would make one
voice's delay line and envelope leak into the other.

## Errors

`PatchNode.create` throws `IllegalArgumentException`, naming the module, when:

- the patch has no `AudioOutNode`, or more than one (raised by `Patch` and
  rewrapped, so the message says which module failed);
- the inner graph has a cycle;
- two macros share a name, or two macros target the same knob - either would
  produce two input ports that cannot be told apart.

`NodeMenu` already shows a `RuntimeException` from a catalog entry as an error
dialog, so a bad file reports itself and adds no node.

## Package placement

`Modules` is a sibling package of `Graph`, `Filters`, `Effects` and the rest -
concrete node implementations never live inside `Graph`
([convention 1](conventions.md)).

It is the one node package that depends on [`Serialization`](serialization.md),
for `PatchDocument` and `Macro`, and `Serialization`'s `PatchCodec` depends on
it in turn, for `PatchNode`. That two-way edge is deliberate. A patch-as-node
*is* a document embedded in a graph, so the dependency cannot be removed, only
moved - and `PatchCodec` already imports every other concrete node package to
rebuild their construction-time parameters, so the alternative was duplicating
the document model to break a cycle that costs nothing.

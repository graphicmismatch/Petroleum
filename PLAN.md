# Realtime collaboration (plan)

Status: **planned, not implemented.** This is the working plan for letting
several people edit one patch in the graph editor at the same time. Once the
code lands, the design part of it moves into `docs/internals/collaboration.md`
and this file is deleted.

## Directives

1. **Offline stays the default.** Nothing collaboration-related runs until the
   user explicitly hosts or joins a session from the editor. Petroleum makes no
   network connection at startup and never polls for updates. `petroleum render`
   stays untouched. If the connection drops, the user simply keeps working on
   an ordinary unsaved local document.
2. **The core stays as it is.** `Graph`, `Patch`, `Audio`, `Serialization` and
   the node packages need no changes. The `.ptr`/`.dsl` format is unchanged and
   `PatchCodec.FORMAT_VERSION` is not bumped. Session state lives in memory
   only and is never written to a file.
- [ ] 3. **No new dependencies.** The transport is plain `java.net.Socket` with
   newline-delimited JSON, which needs only `java.base`. That keeps the
   jpackage runtimes working, since `pom.xml` passes no `--add-modules`, and
   `java.net.http` would be a module to worry about there. The server is plain
   Java 17 with no dependencies either.
- [ ] 4. **Saving stays local.** Every participant holds the full document and saves
   it with the normal Save button. The server is a relay, not a file store, so
   losing the server never loses work.

## Shape of the solution

The editor document is flattened into a **key -> value map**, and a session
keeps that map identical on every client:

| Key                       | Value                                                             |
|---------------------------|-------------------------------------------------------------------|
| `node/<id>`               | the node's body: `Diesel.write` of a one-node document at (0, 0) with no port values |
| `pos/<id>`                | `"x,y"` canvas position                                           |
| `knobs/<id>`              | JSON object of the node's shadow port values (`NodeView.getShadowValues`) |
| `wire/<toId>/<toPort>`    | `"<fromId>/<fromPort>"`, so one input can hold one wire (the editor's own rule, `wireByTargetPort`) |
| `macro/<name>`            | JSON `{target, min, max}`, where `target` is a node id            |
| `name`                    | document name                                                     |

Every edit is either `set key value` or `del key`. The **server is an ordering
relay that does not understand patches.** For each room it stamps every
incoming op with the next sequence number, applies it to its own copy of the
map, and broadcasts it to every client, the sender included. Clients apply ops
in server order, so they converge without a CRDT. A late joiner receives the
current map as a batch of `set` ops, so the protocol needs only one
vocabulary.

Conflict semantics follow from the keys: **last writer wins per key.** Two
people moving different nodes, turning different knobs, or wiring different
inputs never conflict. When two people edit the same node's internals at the
same time, for example both in its tracker, the later body wins. Splitting the
body out from `pos/` and `knobs/` is what keeps a drag or a knob turn from
resending a whole body, base64 samples included.

### Why Diesel for node bodies

Diesel is the codec's own lossless text form, built on `PatchCodec.saveRoot`.
It inlines samples and data blobs, so a one-node document becomes a
self-contained string with no codec changes needed. A `.ptr` zip would not
work here, because its entry timestamps make the bytes differ on every save.

This has been checked: a throwaway smoke test encoded every one of the 151
`NodeFactory` types twice, decoded the result and encoded it again. All 151
encodings were byte-identical. Diffing by string equality is therefore sound
for default state. Phase 2 repeats the check with sample-bearing nodes.

## Node identity

`PatchCodec` assigns positional ids (`n0`, `n1`, ...) at save time, so they
mean nothing across edits. The client keeps a **session-local
`IdentityHashMap<Node, String>`** plus its reverse map. Ids come from three
places:

- Nodes present when the host starts a session get ids minted by the host.
- Nodes a client creates get `<clientId>:<counter>`, where the server assigns
  `clientId` on join, so ids are unique without coordination.
- Nodes received from a peer keep the id they arrive with.

When a body changes, the receiver builds a new `Node` instance. The map is
updated to point the same id at the new instance.

## Components

### Server: `../Petroleum-Server`

This is a separate repository and a single-module Maven project with no
dependencies.

- `Server` runs the `ServerSocket` accept loop with one thread per client. It
  takes `--port` (default `7777`) and `--bind`.
- `Room` holds the `Map<String, String>` state, the `long seq` counter and the
  client list, behind one lock. Rooms live in memory and are evicted 10
  minutes after the last client leaves.
- `Json` is a copy of `Petroleum.Serialization.Json`. Copying it keeps the
  server independent of the engine jar.

Protocol (one JSON object per line):

```
C->S  {"t":"create","name":"Ray"}                     -> new room, server picks the code
C->S  {"t":"join","room":"K7QM2XPA","name":"Sam"}
S->C  {"t":"welcome","room":"...","client":"c3","seq":41,"ops":[{"k":..,"v":..}, ...]}
C->S  {"t":"op","k":"pos/c1:4","v":"120,80"}          v:null = delete
S->C  {"t":"op","seq":42,"from":"c3","k":"pos/c1:4","v":"120,80"}
S->C  {"t":"peers","names":["Ray","Sam"]}             on every join or leave
```

Validation at the trust boundary:

- Lines are capped at 32 MB, which covers a body with an imported sample.
- Room state is capped at 512 MB.
- Rooms are capped per server.
- Room codes are 8 random base32 characters from `SecureRandom`.
- A malformed line disconnects that client and leaves the room untouched.

### Client protocol: `Petroleum.Collab.CollabClient`

This class has no JavaFX dependency, so it can be smoke-tested headlessly
against a real server. It owns the socket, a reader thread, and the local
copy of the map.

**Pending-key rule.** This is what makes optimistic local edits converge.
Suppose A and B both set `k`, and the server orders A's op first. B must not
apply A's op, because B's own later op will win once the server processes it.
So the client counts unacknowledged local ops per key. Remote ops for a key
with a non-zero count are recorded in the map but not surfaced. The client's
own echo decrements the count, and when the count reaches zero the map's
server value becomes current. Without this rule, A and B swap values and end
up diverged.

Remote ops reach the GUI through a callback. The bridge marshals that callback
onto the FX thread with `Platform.runLater`.

### Editor bridge: `Petroleum.Gui.CollabBridge`

The bridge lives in `Gui` because the silent mutators it needs are
package-private there.

**Outgoing.** An FX `Timeline` runs `GraphEditor.toDocument()`, flattens the
result into the key map, diffs it against the last-synced baseline, and sends
the differences.

- `pos/`, `knobs/`, `wire/` and `macro/` are diffed every 100 ms.
- `node/` bodies are re-encoded every 500 ms, because encoding is the
  expensive part.

Polling is deliberate. The dedicated editors (tracker, piano roll, instrument,
sound clip, waveform draw) mutate node internals without notifying anyone, so
polling the document is the only hook that catches every edit path without
touching those editors. Its known ceiling is that re-encoding sample-heavy
bodies twice a second costs CPU in large patches. The upgrade path is a
per-node mutation counter, so only changed nodes are re-encoded.

**Incoming.** Remote ops are applied with the existing silent primitives:

| Op                     | Applied with                                                         |
|------------------------|----------------------------------------------------------------------|
| new `node/`            | `Diesel.read` -> `addNodeSilently` at its `pos/`, then `applyShadowValues` |
| changed `node/`        | the `EditCommands.convertChannels` pattern: `removeNodeByReference`, `addNodeSilently`, shadow values, reconnect every `wire/` touching the id, and **rebind macros** that targeted the old `ConstantNode` via `replaceMacros` (convention 10, or the next save throws) |
| deleted `node/`        | `removeNodeByReference`; its `wire/`, `pos/`, `knobs/` and `macro/` keys are deleted by the sender in the same tick |
| `pos/`                 | `placeNode`                                                          |
| `knobs/`               | `NodeView.applyShadowValues`                                         |
| `wire/`                | `connectSilently` / `disconnectSilently`. A wire whose endpoint is not here yet stays in the map and is retried after every node add |
| `macro/`               | `replaceMacros` with the rebuilt list, skipping any target that is not present |

After applying, the bridge writes the applied values into its baseline, so the
next tick does not send them back as an echo.

Remote input is untrusted. A `SerializationException` or any other decode
failure drops that op with a status-bar warning. It never reaches the editor
as an exception.

Playback needs no special handling. Applies go through `notifyGraphChanged`,
which already rebuilds the playing patch.

### Editor states that pause sync

- **Subpatch editing** (`SynthApp.subpatchStack` non-empty). The editor is
  showing the inner document, not the root, so the outgoing diff pauses and
  incoming ops are queued. On "Done Editing Subpatch" the queue is applied
  first, then diffing resumes. The edited `PatchNode` body then goes out as an
  ordinary `node/` op, and last-writer-wins applies.
- **A dedicated editor open on node X.** Outgoing changes keep flowing, so
  collaborators see tracker edits live. An incoming `node/X` body is deferred
  until that screen closes, because replacing the instance underneath the
  editor would orphan it. Other ops apply normally.

### Undo

Undo stays local. A local undo just changes the document, and the next tick
sends that change like any other edit. `EditCommand`s hold `Node` references,
though, so an applied remote op that **removes or replaces** a node clears the
local undo history (`clearHistory` becomes package-private). Remote position,
knob, wire and macro ops leave the history intact.

## Changes to existing code

The changes are kept small:

- `GraphEditor`: `clearHistory` changes from private to package-private.
  Nothing else changes, because `toDocument`, `getPatchCodec` and every silent
  mutator already exist.
- `SynthApp`:
  - "Host Session..." and "Join Session..." actions, which open dialogs for
    the server address, display name and room code.
  - "Leave Session".
  - A status label showing the room code and peers.
  - Starting and stopping the bridge.
  - The bridge needs to know about subpatch and dedicated-editor states, so
    `SynthApp` gives it two booleans.
- Hosting a session sends the current document as the initial `set` ops.
  Joining replaces the local document, after the existing unsaved-changes
  confirmation.
- Docs: `docs/internals/collaboration.md` (moved from this file) and its
  `docs/internals/README.md` index entry, and a short
  `documentation/` user guide once the feature ships.

## Phases

- [x] 1. Check that per-node Diesel encoding is deterministic: 151/151
  factory types.
- [ ] 2. Server in `../Petroleum-Server`, plus a headless smoke test: two raw socket
  clients converge on concurrent sets of the same key. Repeat the
  determinism check with sample-bearing nodes (granular player, instrument,
  sound clip).
- [ ] 3. `CollabClient` with the pending-key rule, smoke-tested against the real
  server.
- [ ] 4. Outgoing half of `CollabBridge` (diff and send).
- [ ] 5. Incoming half (apply, echo suppression, macro rebinding).
- [ ] 6. `SynthApp` actions, subpatch and dedicated-editor pausing, undo clearing.
- [ ] 7. Docs and user guide.

## Deliberately left out of v1

- **Presence (cursors, selections, who is dragging what).** Add it once
  sessions work. It would be ephemeral `{"t":"presence"}` messages the server
  relays without storing.
- **TLS.** For now, use a LAN or put the server behind a TLS-terminating
  proxy. `SSLSocket` is in `java.base`, so native TLS needs no dependency when
  it is wanted.
- **Auth beyond the room code.** Add accounts or per-room passwords if
  sessions ever run on a public server.
- **Merging finer than one node body.** Two people editing the same tracker
  pattern at once means the later one wins. Add per-pattern or per-row keys
  only if that turns out to hurt in practice.
- **Server-side persistence.** Every client already holds the full document,
  so the server does not need to store anything.

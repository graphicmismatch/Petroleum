# Realtime collaboration

Opt-in realtime co-editing of one patch in the graph editor, through the
`Petroleum-Server` relay (`../Petroleum-Server`). Client code lives in
`Petroleum.Collab.CollabClient` (headless protocol) and
`Petroleum.Gui.CollabBridge` (editor diff/apply); the user starts it from the
**Session** menu.

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
- [ ] 3. **No new dependencies.** The transport is plain `java.net.Socket` (or
   `SSLSocket` for TLS) with newline-delimited JSON, which needs only
   `java.base`. That keeps the
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
| `knobs/<id>`              | JSON object (keys sorted) of the node's shadow port values, minus ports that have a wire |
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
for default state, and the same holds for a granular player and a sound clip
carrying a 48000-sample buffer and an instrument with a sample zone.

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
  takes `--port` (default `7777`) and `--bind`. With `--keystore <PKCS12>`
  (password from `--keystore-password`, else `PETROLEUM_KEYSTORE_PASSWORD`,
  else empty) it listens with an `SSLServerSocket` instead and prints the
  certificate's SHA-256 fingerprint at startup. The TLS handshake runs on the
  peer thread's first read, so the accept loop never blocks on it. Without a
  keystore it stays plain TCP.
- `Room` holds the `Map<String, String>` state, the `long seq` counter and the
  client list, behind one lock. Rooms live in memory and are evicted 10
  minutes after the last client leaves.
- `Json` is a copy of `Petroleum.Serialization.Json`. Copying it keeps the
  server independent of the engine jar.

Protocol (one JSON object per line):

```
C->S  {"t":"create","name":"Ray"}                     -> new room, server picks the code
C->S  {"t":"join","room":"K7QM2XPA","name":"Sam"}
S->C  {"t":"welcome","room":"...","client":"c3","seq":41}
S->C  {"t":"state","k":"node/c1:0","v":"..."}        one line per entry
S->C  {"t":"ready"}
C->S  {"t":"op","k":"pos/c1:4","v":"120,80"}          v:null = delete
S->C  {"t":"op","seq":42,"from":"c3","k":"pos/c1:4","v":"120,80"}
S->C  {"t":"peers","names":["Ray","Sam"]}             on every join or leave
```

Validation at the trust boundary:

- Lines are capped at 32 MB, which covers a body with an imported sample.
  The welcome state is streamed one entry per line, so a room bigger than
  that can still be joined. The client refuses to send a value that would not
  fit and says so in the session label, rather than being disconnected.
- Room state is capped at 128 MB.
- Each peer's outbound queue is capped at 192 MB (room state plus headroom);
  a peer that stops reading is dropped instead of buffering forever.
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

**Transport.** The Server field is parsed by `CollabClient.Endpoint`:
`tls://host:port` selects TLS, while `host:port` or `tcp://host:port` stays
plain TCP. `[ipv6]:port` works too, and the port defaults to `7777`. TLS wraps
the plain socket in an `SSLSocket` with endpoint identification set to
`HTTPS`, so the hostname is checked against the certificate.
`PinningTrustManager` tries the system trust store first, which covers a
Let's Encrypt certificate, and falls back to a trust-on-first-use pin, the
SHA-256 fingerprint of the leaf certificate. When neither accepts, the
connection fails with `UntrustedServerException` carrying the fingerprint
and whether a different pin was already stored. `SynthApp` then shows the
fingerprint, with a stronger warning if it changed, and on confirmation
stores it under `Preferences` node `collabPins`, keyed by `Endpoint.key()`,
then retries. The hostname check is part of the system validation, so a
pinned certificate is accepted by fingerprint alone, which lets a self-signed
server be reached by IP.

Remote ops reach the GUI through a callback. The bridge marshals that callback
onto the FX thread with `Platform.runLater`.

### Editor bridge: `Petroleum.Gui.CollabBridge`

The bridge lives in `Gui` because the silent mutators it needs are
package-private there.

**Outgoing.** An FX `Timeline` runs `GraphEditor.toDocument()`, flattens the
result into the key map, diffs it against the last-synced baseline, and sends
the differences. Each tick **sends before it polls**: local edits made since
the last tick must be pending before remote ops are applied, or the
pending-key rule cannot protect them (a remote body for a node deleted
locally would otherwise re-add it).

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
| changed `node/`        | same class and `Stateful`: copy the decoded state into the existing instance with `saveState`/`loadState`, and keep it if re-encoding now reproduces the body (then `NodeView.syncControlsFromNodes`) - this keeps a dragged `Constant` from restarting playback and clearing undo. Otherwise the `EditCommands.convertChannels` pattern: `removeNodeByReference`, `addNodeSilently`, shadow values, reconnect every `wire/` touching the id, and **rebind macros** that targeted the old `ConstantNode` via `replaceMacros` (convention 10, or the next save throws) |
| deleted `node/`        | `removeNodeByReference`; its `wire/`, `pos/`, `knobs/` and `macro/` keys are deleted by the sender in the same tick |
| `pos/`                 | `placeNode`                                                          |
| `knobs/`               | `NodeView.applyShadowValues`                                         |
| `wire/`                | `connectSilently` / `disconnectSilently`. A wire whose endpoint is not here yet stays in the map and is retried after every node add |
| `macro/`               | `replaceMacros` with the rebuilt list, skipping any target that is not present |

After applying, the bridge writes the applied values into its baseline, so the
next tick does not send them back as an echo. This only works if flattening a
just-applied value reproduces it exactly, so every value must be normalized:
knob keys are sorted, and knob values of wired ports are left out, because
`NodeView.getShadowValues` reports a driven port depending on its local
`materialized` history. Without that, two clients bounced `{}` and `{"in": 0}`
back and forth forever. The saved `.ptr` can still differ between clients in
such a stale driven-port knob value; it is ignored at render time.

Remote input is untrusted. A `SerializationException` or any other decode
failure drops that op with a warning in the session label. It never reaches the editor
as an exception.

Playback needs no special handling. Applies go through `notifyGraphChanged`,
which already rebuilds the playing patch.

### Editor states that pause sync

- **Subpatch editing** (`SynthApp.subpatchStack` non-empty). The editor is
  showing the inner document, not the root, so the outgoing diff pauses and
  incoming ops are queued. On "Done Editing Subpatch" the parent is reloaded
  with a new `PatchNode` instance, so the next tick sends it as a delete of the
  old id plus an add under a new one; queued remote ops for the old id are
  then pending and ignored.
- **A dedicated editor screen is open** (tracker, piano roll, waveform, sound
  clip, instrument). Outgoing changes keep flowing, so collaborators see
  tracker edits live. An incoming body change or deletion of any existing node
  is deferred until the screen closes, because replacing the instance
  underneath the editor would orphan it; the screen does not say which node it
  holds, so the check is screen-wide. A deferred op is dropped if the local
  user changes that key first, since the local op is later in server order.
  Other ops apply normally.

### Undo

Undo stays local. A local undo just changes the document, and the next tick
sends that change like any other edit. `EditCommand`s hold `Node` references,
though, so an applied remote op that **removes or replaces** a node clears the
local undo history (`GraphEditor.clearHistory`). Remote position,
knob, wire and macro ops leave the history intact.

## Hooks in existing code

- `GraphEditor.clearHistory` is package-private; everything else the bridge
  uses (`toDocument`, `getPatchCodec`, `viewOf` and the silent mutators)
  already existed.
- `SynthApp` adds the **Session** menu (Host Session..., Join Session...,
  Leave Session), a session label at the end of the editor header, and hands
  the bridge two suppliers: "in a subpatch" and "on a dedicated editor
  screen". Connecting runs off the FX thread. Hosting sends the current
  document; joining replaces it, after the usual unsaved-changes prompt.
  Server address and name are remembered in `Preferences`.
- `Dialogs.session` is the host/join dialog.

## Verification

Throwaway smoke tests, run against a real server:

- Two raw `CollabClient`s firing 2000 random concurrent sets/deletes over five
  keys converge, and a late joiner's welcome state equals both.
- Two real `GraphEditor`s under `xvfb-run` stay identical through join, add,
  move, concurrent move of one node, a `Constant` body change, wire
  replacement, a knob change, a deferred body change while a dedicated screen
  is open, a delete, a local delete made while paused racing a remote edit of
  the same node (it stays deleted on both), and an instrument with a sample
  zone - the remote `Constant` stays the same instance, and the room goes
  silent once idle.
- A room holding 40 MB of state can be joined.
- Against a self-signed TLS server: an unpinned connect reports the same
  fingerprint the server printed, a wrong pin reports "changed", the right
  pin connects and relays ops, plain TCP still works, and mismatched modes
  (plain client to TLS port and the reverse) fail cleanly.

Not yet exercised by hand in the running app; no user guide in
`documentation/` yet.

## Deliberately left out of v1

- **Presence (cursors, selections, who is dragging what).** Add it once
  sessions work. It would be ephemeral `{"t":"presence"}` messages the server
  relays without storing.
- **Auth beyond the room code.** Add accounts or per-room passwords if
  sessions ever run on a public server.
- **Merging finer than one node body.** Two people editing the same tracker
  pattern at once means the later one wins. Add per-pattern or per-row keys
  only if that turns out to hurt in practice.
- **Server-side persistence.** Every client already holds the full document,
  so the server does not need to store anything.

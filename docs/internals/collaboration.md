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
   `PatchCodec.FORMAT_VERSION` is not bumped. Client session state lives in
   memory; only member tokens (`collabTokens`) and certificate pins
   (`collabPins`) go to `Preferences`.
- [ ] 3. **No new dependencies.** The transport is plain `java.net.Socket` (or
   `SSLSocket` for TLS) with newline-delimited JSON, which needs only
   `java.base` (on JDK 17-21 the EC crypto provider is the separate
   `jdk.crypto.ec` module, so an installer built on those JDKs may need
   `--add-modules jdk.crypto.ec` for EC certificates; from JDK 22 it is in
   `java.base`). That keeps the
   jpackage runtimes working, since `pom.xml` passes no `--add-modules`, and
   `java.net.http` would be a module to worry about there. The server is plain
   Java 17 with no dependencies either.
- [ ] 4. **Saving stays local.** Every participant holds the full document and saves
   it with the normal Save button. The server's copy of a room is the single
   source of truth while a session runs (and survives restarts with `--data`),
   but it is never the only copy, so losing the server never loses work: the
   host's copy is the backup a lost room is restored from.

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
- Nodes a client creates get `<clientId>:<counter>`. The server issues a fresh
  random `clientId` (`c` plus 8 base32 characters) on every connection, not a
  counter, so ids stay unique without coordination even after a server restart
  or a restored room, where a counter would hand out an id that is already in
  the state.
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
- `Room` holds the `Map<String, String>` state, the `long seq` counter, the
  members (id, SHA-256 of their token, name, role) and the connected peers,
  behind one lock. It enforces roles (below).
- **Persistence.** With `--data <dir>`, a flusher thread rewrites each dirty
  room as `<code>.json` (temp file plus atomic move) every 2 seconds, and once
  more from a shutdown hook. A room leaves memory 10 minutes after its last
  client and is read back from disk on the next join; files untouched for 30
  days are swept. Closing a room or rerolling its code deletes the file and
  writes a `<code>.gone` tombstone (kept in memory too, without `--data`), so
  that code can never be restored. Without `--data`, rooms are memory only and
  are evicted 10 minutes after the last client leaves. Known ceiling: the flush
  rewrites the whole room under the server lock; a per-room journal is the
  upgrade if large rooms make joins stall.
- **Timeouts.** Every socket has a 15 s read timeout. Clients ping every 5 s and
  the server answers each ping, so a silent drop (Wi-Fi gone, no FIN) is
  noticed on both sides within 15 s. Pings start as soon as the hello is
  written, so a slow welcome for a big room does not trip the server's timeout.
- `Json` is a copy of `Petroleum.Serialization.Json`. Copying it keeps the
  server independent of the engine jar.

Protocol (one JSON object per line):

```
C->S  {"t":"create","name":"Ray","joinAs":"editor"}          -> new room, server picks the code
C->S  {"t":"join","room":"K7QM2XPA","name":"Sam","token":...} token null = new member
C->S  {"t":"restore","room":"K7QM2XPA","name":"Ray","token":...,"joinAs":"editor"}
S->C  {"t":"welcome","room":"...","client":"cX7Q2M4KA","member":"m2","token":"...",
       "role":"editor","joinAs":"editor","seq":41}
S->C  {"t":"state","k":"node/c1:0","v":"..."}              one line per entry
S->C  {"t":"ready"}
C->S  {"t":"op","k":"pos/c1:4","v":"120,80"}                v:null = delete
S->C  {"t":"op","seq":42,"from":"cX7Q2M4KA","k":"pos/c1:4","v":"120,80"}
S->C  {"t":"op","seq":42,"from":"<sender>","k":...,"v":<server value>,"denied":true}
S->C  {"t":"members","joinAs":"viewer","list":[{"id":"m1","name":"Ray","role":"host","online":true}]}
S->C  {"t":"room","room":"NEWCODE2"}                       after a reroll
C<->S {"t":"ping"} / {"t":"pong"}
C->S  {"t":"role","member":"m2","role":"viewer"}            host only, as are the next five
C->S  {"t":"joinAs","role":"viewer"}
C->S  {"t":"kick","member":"m2"}
C->S  {"t":"transfer","member":"m2"}
C->S  {"t":"close"}
C->S  {"t":"reroll"}
S->C  {"t":"error","message":"...","final":true}            final = do not offer Reconnect
```

**Members and roles.** A member is an identity in a room; a connection is
one session of it. The first member of a room is the **host**; later ones get
the room's `joinAs` role, **editor** or **viewer**. The welcome hands each new
member a random 26-character token, which the client keeps; joining with it
again restores the same member and role, and an unknown token just makes a new
member. The server stores only the token's SHA-256. On plain TCP the token
travels in clear, so anyone sniffing it can impersonate that member, the host
included; use TLS when that matters.

- A viewer's op is not applied. The server answers the sender alone with a
  `denied` op carrying the server's value, so the client's pending count still
  clears and its local change is put back.
- Host-only messages from anyone else are ignored. The host cannot be kicked or
  have its role changed; the role only moves with `transfer`, which makes the
  old host an editor.
- `kick` removes the member, disconnects its connections with a final error,
  and invalidates its token. Anyone with the code can still join as a new
  member; `reroll` is what stops that.
- `reroll` gives the room a new code. Members connected at that moment stay
  (they receive `room`); everyone offline is removed, and the old code is
  tombstoned.
- `close` disconnects everyone with a final error, removes the room and its
  file, and tombstones the code.
- `restore` acts as `join` when the room exists. When it does not and the code
  is not tombstoned, it creates an empty room under that code with the
  restorer as host. This is how the host's copy becomes the backup after the
  server lost a room. It cannot be authenticated (the server has nothing left
  to check against), which is why tombstones exist.

Validation at the trust boundary:

- Lines are capped at 32 MB, which covers a body with an imported sample.
  The welcome state is streamed one entry per line, so a room bigger than
  that can still be joined. The client refuses to send a value that would not
  fit and says so in the session label, rather than being disconnected.
- Room state is capped at 128 MB.
- Each peer's outbound queue is capped at 192 MB (room state plus headroom);
  a peer that stops reading is dropped instead of buffering forever.
- Rooms are capped per server (1000 in memory) and members per room (256).
- Room codes are 8 random base32 characters from `SecureRandom`; a code from a
  client is checked against that alphabet before it is used as a file name.
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

Remote ops reach the GUI through a callback. The bridge polls the inbox from
its FX `Timeline`. Writes are synchronized on the output stream, because the
ping thread writes too. A read timeout ends the session with "the server
stopped responding"; a final error or `close()` makes the session
non-resumable (`isResumable`).

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

**Viewers.** The editor itself is not made read-only. Instead, while the
client's role is viewer, the tick does not send: it flattens the document,
and any key that differs from the baseline is put back by applying the
baseline value through the same incoming path (node keys first, so wires find
their nodes), then the undo history is cleared. A viewer's edit therefore
snaps back within a tick. The revert waits while a dedicated editor screen is
open, for the same reason incoming body changes do.

**Reconnect.** When a resumable session drops, `SynthApp` keeps the bridge,
and **Session > Reconnect** reopens it with the same member token (`restore`
when the dropped client was host, `join` otherwise), then calls
`CollabBridge.resume(newClient)`. Resume is a three-way merge of B (the
baseline at the drop), L (the local document now) and S (the server's state
in the new welcome):

- A key is *mine* when L differs from B, or when the key was still pending
  (sent but never acknowledged) at the drop. The baseline is replaced at send
  time, so without the pending check an edit lost in flight would look
  unchanged and be overwritten.
- Mine keys take L; every other key takes S. Where both sides changed a key,
  the reconnecting side wins, the same last-writer rule as live edits.
- **Host as backup:** if the dropped client was host and the server's `seq` is
  behind the last one the host saw (a restored room starts at 0, or a crash
  lost the last unflushed seconds), every key takes L: the host's copy
  replaces the server's.
- Viewers take S wholesale.

If the merged map differs from L, the document is rebuilt from it
(`loadDocument(buildDocument(...))`). The baseline is then set to S, so the
normal next tick sends exactly the merged changes. Reconnect is refused
inside a subpatch or on a dedicated editor screen, because the rebuild would
orphan the node that screen holds.

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
  Reconnect, Manage People..., New Room Code..., Close Room..., Leave
  Session), a session label at the end of the editor header, and hands the
  bridge two suppliers: "in a subpatch" and "on a dedicated editor screen".
  Item enablement is recomputed whenever the menu shows and on every state
  change; the host-only items need the host role. Connecting runs off the FX
  thread. Hosting sends the current document; joining replaces it, after the
  usual unsaved-changes prompt. Server address and name are remembered in
  `Preferences`, and each room's member token under `collabTokens`, keyed by
  endpoint and room code (re-keyed after a reroll), so a host can come back as
  host after restarting the app.
- `Dialogs.session` is the host/join dialog (host picks "New people join
  as"); `Dialogs.people` is the host's member list: role per member, Make
  Host, Remove, and the room's join-as role.

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
- Roles, headless against a `--data` server it starts and restarts itself: a
  viewer's op is denied and reverted with no stuck pending key; promotion
  lets the edit through; changing join-as applies to the next joiner; a kicked
  member gets a final error and its old token makes a new identity; a peer
  behind a blackholing proxy is dropped by the server within 15 s, and a
  `SIGSTOP`ped server is reported by the client as "stopped responding" within
  15 s; a token rejoin keeps the member and role; reroll keeps online members,
  drops offline ones, and rejects the old code for join and restore; after a
  server restart the room, roles, state and seq survive and new client ids do
  not collide; a closed code cannot be restored; with the data directory wiped,
  the host restores the room.
- The two-editor `xvfb` test continues with: a viewer's move snaps back, a
  promoted viewer's move reaches the host; an edit swallowed by a blackholing
  proxy before the drop survives the reconnect merge, along with both sides'
  offline additions; after the server process dies and comes back empty, the
  host restores the room from its copy (including an offline edit) and the
  other editor rejoins and receives it.
- Against a self-signed TLS server: an unpinned connect reports the same
  fingerprint the server printed, a wrong pin reports "changed", the right
  pin connects and relays ops, plain TCP still works, and mismatched modes
  (plain client to TLS port and the reverse) fail cleanly.

Not yet exercised by hand in the running app. The user guide is
`documentation/collaboration.md`; server setup lives in the server
repository's README.

## Deliberately left out of v1

- **Presence (cursors, selections, who is dragging what).** Add it once
  sessions work. It would be ephemeral `{"t":"presence"}` messages the server
  relays without storing.
- **Passwords or accounts.** The room code gets you in and a member token
  keeps your role; there is no password. Add a per-room password if rooms on
  a public server get guessed or leaked often enough that rerolling is not
  enough.
- **Automatic reconnect.** Reconnect is a menu item; retrying with backoff on
  top of it is the next step.
- **Merging finer than one node body.** Two people editing the same tracker
  pattern at once means the later one wins. Add per-pattern or per-row keys
  only if that turns out to hurt in practice.

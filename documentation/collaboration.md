# Collaborating in real time

Editing one patch together with other people over a network.

Petroleum works fully offline. Nothing connects anywhere until you pick
**Session > Host Session...** or **Session > Join Session...**, and leaving
the session takes you straight back to offline editing.

## What you need

Everyone in a session connects to the same **Petroleum server**. The server is
a small, separate program that one person runs, on their own computer or on a
machine everyone can reach. It keeps the shared copy of each room's patch,
which is what everyone syncs to, and passes edits between collaborators. If
it was started with a data folder, rooms also survive a server restart. How
to install and run it is covered in the
[Petroleum Server README](../../Petroleum-Server/README.md).

Whoever runs the server tells everyone else its address, and whether it uses
encryption.

## Hosting a session

1. Open the patch you want to share. An empty canvas works too.
2. Pick **Session > Host Session...**.
3. Fill in **Server** (see [Server addresses](#server-addresses)) and
   **Your name**, which is what the others see.
4. Choose whether **New people join as** an **Editor** or a **Viewer** (see
   [Roles](#roles)). You can change this later. Then press OK.
5. A dialog shows the **room code**, eight letters and digits such as
   `K7QM2XPA`. Send it to your collaborators.

Your current patch becomes the shared one, and you are the room's **host**.

## Joining a session

1. Pick **Session > Join Session...**.
2. Fill in **Server**, **Your name**, and the **Room code** you were given.
   The code is not case-sensitive.
3. If your current graph has unsaved changes, you are asked whether to
   discard them, because joining replaces your graph with the shared one.
   Save first if you want to keep it.

The editor header then shows the session, for example
`Session K7QM2XPA - Ray (host), Sam, Kim (viewer)`, and the list of names
updates as people come and go. If you are a viewer, the header also says
`(view only)`.

Petroleum remembers the server and name you last used and fills them in next
time. It also remembers who you were in each room, so if you join the same
room again, even after restarting Petroleum, you come back with the same role.
That includes the host.

## Roles

Everyone in a room has one of three roles:

| Role | Can do |
|---|---|
| **Host** | Everything an editor can, plus manage people and the room. Each room has exactly one host: whoever created it, unless they handed the role on. |
| **Editor** | Change the shared patch. |
| **Viewer** | Watch and listen. A viewer can still drag a node or turn a knob, but the change snaps straight back and nobody else sees it. |

Anyone can still save their own copy of the patch with **File > Save**,
viewers included.

## Managing people (host only)

These items in the **Session** menu are only available to the host:

- **Manage People...** lists everyone who has been in the room, and shows who
  is offline. For each person you can:
  - switch them between **Editor** and **Viewer**;
  - **Make Host**, which hands your host role to them and makes you an
    editor;
  - **Remove** them. They are disconnected and lose their place. Anyone who
    has the room code can still join again, but only as a new person.

  The same dialog sets which role **new people join as**.
- **New Room Code...** gives the room a fresh code and stops the old one from
  working. Everyone who is connected at that moment stays in the room.
  Anyone who is offline loses their place, and needs the new code to join
  again as a new person. Use it after removing someone, or if the code leaked.
- **Close Room...** ends the session for everyone, and the server deletes its
  copy of the room. Everyone keeps the patch that is open in their own editor.

## Server addresses

The **Server** field takes one of these forms:

| You type | Connection |
|---|---|
| `host` or `host:port` | Unencrypted |
| `tcp://host:port` | Unencrypted (same as above) |
| `tls://host:port` | Encrypted |

The port defaults to 7777. The host can be a name (`studio.example.com`), an
IPv4 address (`192.168.1.20`), or an IPv6 address in brackets
(`[fe80::1]:7777`).

Use whichever form matches how the server was started. An encrypted client
cannot talk to an unencrypted server, or the other way round, and the
connection just fails.

Unencrypted is fine on a home or studio network. Over the internet, use
`tls://`, so nobody between you and the server can read or change your edits.

### Trusting a server's certificate

An encrypted server proves who it is with a certificate. If the server uses
a certificate from a public authority such as Let's Encrypt, Petroleum accepts
it without asking.

Many self-hosted servers use a **self-signed** certificate instead. The first
time you connect to one, Petroleum shows an **Untrusted Server Certificate**
dialog with the certificate's fingerprint, a long string like
`5A:F7:B0:94:...`. The person running the server sees the same fingerprint
printed when the server starts. Compare the two, for example over chat or a
phone call, and only choose to trust the certificate if they match.
Petroleum remembers your choice for that server address and does not ask
again.

If the certificate at that address ever changes, you get a **Server
Certificate Changed** warning instead. Sometimes the server just got a new
certificate. It can also mean someone is intercepting the connection. Check
with the person running the server before you trust the new one.

## What is shared

Everything in the graph itself:

- Adding, deleting and moving nodes
- Wiring and unwiring ports
- Knob values
- Each node's own content: tracker patterns, piano roll notes, drawn
  waveforms, sound clips, instrument zones and imported samples
- Macros

These stay on your own computer:

- **Playback.** Play, Stop and Render to WAV only affect you, and each person
  hears the patch on their own speakers.
- **Saving.** The server's copy of the room is not a file you can open.
  Anyone can save their own copy with **File > Save** at any time, and it is
  an ordinary patch that opens offline.
- **Undo.** Undo only steps back through your own edits. When someone else
  deletes or replaces a node, your undo history is cleared, so undo cannot
  bring back a state that no longer matches what the others have.
- Selection, scroll position, zoom and which screen you are on.

## When two people edit the same thing

Edits are applied in the order the server receives them. If two people change
the same thing at the same moment, the later edit wins on everyone's screen.
For example, if two people drag one node, it ends where the later drag put
it. Edits to different nodes never get in each other's way.

The smallest piece that merges is one node's content. If two people edit the
same tracker pattern or piano roll at once, one person's version replaces the
other's. Agree on who works on which part.

## Screens that pause syncing

- **Inside a subpatch.** While you are editing a subpatch, syncing stops in
  both directions. When you return to the main graph, your changes are sent
  and the others' edits arrive. You cannot host or join while you are inside
  a subpatch.
- **On a node's own editor** (tracker, piano roll, waveform drawing, sound
  clip or instrument). Other people's moves, wiring and new nodes still
  arrive. Changes to the content of existing nodes, and deletions, wait until
  you go back to the graph, so the thing you are editing never changes or
  disappears under you.

## Losing the connection

If your network drops, or the server stops responding, Petroleum notices
within about 15 seconds. The header then shows
`Disconnected: ... - Session > Reconnect to continue`. Keep working: your
graph stays as it is, and you can go on editing offline.

When the network is back, pick **Session > Reconnect** from the main graph.
It is not available inside a subpatch or on a node's own editor. Petroleum
then combines the two versions:

- Everything you changed while disconnected is kept and sent to the others.
  This includes edits you made just before the drop, which may never have
  reached the server.
- Everything the others changed meanwhile arrives on your screen.
- If you and someone else changed the same thing, such as the same node's
  position, your version wins, because yours arrives last.

Viewers have nothing of their own to keep, so a viewer simply gets the
current shared patch.

**If the server lost the room**, for example because it was restarted without
a data folder, the host's copy is the backup. When the host reconnects, the
room comes back under the same code with the host's version of the patch.
Everyone else can then reconnect. People who are not the host can only
reconnect once the room exists again.

## Leaving

Pick **Session > Leave Session**. Your graph stays as it is, and you are back
to editing offline. Leaving does not change your role: rejoin the same room
with **Join Session...** and you are back where you were. A host who will not
be back should use **Make Host** first.

The others can carry on without you. How long an empty room is kept depends
on the server. With a data folder, it is kept on disk for 30 days after
anyone last used it. Without one, it is gone 10 minutes after the last person
leaves, unless the host reconnects and restores it.

## Troubleshooting

| Message | What it means |
|---|---|
| Could not connect to ... | The server is not running, the address or port is wrong, a firewall is in the way, or the `tls://` setting does not match the server. |
| no such room | The room code is wrong, the host gave the room a new code, or the room expired after everyone left. |
| the room was closed or its code changed | The host closed the room or gave it a new code, so it cannot be restored. Ask the host for the new code. |
| Session ended: you were removed from the room by the host | The host removed you. Reconnect is not offered. |
| Session ended: the host closed the room | The session is over. Your graph is kept. |
| too many people in this room | The room has reached 256 people. |
| server is full | The server has reached its room limit. Try again later. |
| Disconnected: room is full | The shared patch reached the server's size limit (128 MB). |
| Too large to share: ... | One node holds more data than the server accepts, usually a very long imported sample. It stays on your computer only. Trim or shorten the sample to share it. |
| Already in session ... | Leave the current session before starting another. |

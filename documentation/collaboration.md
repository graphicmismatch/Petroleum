# Collaborating in real time

Editing one patch together with other people over a network.

Petroleum works fully offline. Nothing connects anywhere until you pick
**Session > Host Session...** or **Session > Join Session...**, and leaving
the session takes you straight back to offline editing.

## What you need

Everyone in a session connects to the same **Petroleum server**. The server is
a small, separate program that one person runs, on their own computer or on a
machine everyone can reach. It only passes edits between collaborators and
never saves your patch. How to install and run it is covered in the
[Petroleum Server README](../../Petroleum-Server/README.md).

Whoever runs the server tells everyone else its address, and whether it uses
encryption.

## Hosting a session

1. Open the patch you want to share. An empty canvas works too.
2. Pick **Session > Host Session...**.
3. Fill in **Server** (see [Server addresses](#server-addresses)) and
   **Your name**, which is what the others see. Then press OK.
4. A dialog shows the **room code**, eight letters and digits such as
   `K7QM2XPA`. Send it to your collaborators.

Your current patch becomes the shared one.

## Joining a session

1. Pick **Session > Join Session...**.
2. Fill in **Server**, **Your name**, and the **Room code** you were given.
   The code is not case-sensitive.
3. If your current graph has unsaved changes, you are asked whether to
   discard them, because joining replaces your graph with the shared one.
   Save first if you want to keep it.

The editor header then shows the session, for example
`Session K7QM2XPA - Ray, Sam`, and the list of names updates as people come
and go.

Petroleum remembers the server and name you last used and fills them in next
time.

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
- **Saving.** The server does not keep files. Anyone can save their own copy
  with **File > Save** at any time, and it is an ordinary patch that opens
  offline.
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

## Leaving

Pick **Session > Leave Session**. Your graph stays as it is, and you are back
to editing offline.

The others can carry on without you. A room is kept for 10 minutes after the
last person leaves, so anyone can rejoin with the same code within that time.
After that it is gone, so save a copy before everyone leaves.

If the connection drops, the header shows `Session ended:` and the reason.
Your graph is kept, and you can save it or join again.

## Troubleshooting

| Message | What it means |
|---|---|
| Could not connect to ... | The server is not running, the address or port is wrong, a firewall is in the way, or the `tls://` setting does not match the server. |
| no such room | The room code is wrong, or the room expired after everyone left. |
| server is full | The server has reached its room limit. Try again later. |
| Session ended: room is full | The shared patch reached the server's size limit (128 MB). |
| Too large to share: ... | One node holds more data than the server accepts, usually a very long imported sample. It stays on your computer only. Trim or shorten the sample to share it. |
| Already in session ... | Leave the current session before starting another. |

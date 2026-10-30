# Computer access (MCP): let AI tools on your computer hand tasks to Mika

[简体中文](mcp.md) | English

With "Computer access" on, Mika is an MCP server: an AI tool on your computer that speaks MCP (an MCP client) can hand Mika a job, and Mika does it on the phone its own way (operating apps, using the built-in browser, working with files), then hands back the result. The computer says what to get done; Mika on the phone decides how.

The same connection is also how you use Mika remotely: when the computer and the phone are on the same Wi-Fi, or in the same private VPN network (a virtual LAN made by a mesh networking tool), they can connect without any port forwarding on your router.

## Turning it on

1. In Mika, open Settings › Computer access (MCP) and turn the switch on. It is off by default.
2. While it is on, a "Computer access is on" notification stays in the shade to show Mika is listening in the background; its "Turn off" button switches it off right there.
3. The page shows:
   - **Address**: like `http://192.168.1.23:8765/mcp`. One per network the phone is on (Wi-Fi, private VPN); tap one to copy it.
   - **Access token**: a random string. Copy it, or use "New token" (computers still using the old one stop working).
   - **Client config**: the address and token put together; tap "Copy config".
   - **Status**: whether it is listening, how many clients connected recently, and when the last request came in.

The token is stored encrypted on the phone, the same way as provider API keys. Turning the switch off closes the port.

## Connecting from a computer

- **Same Wi-Fi**: use the Wi-Fi address from the list (usually `192.168.*.*` or `10.*.*.*`).
- **Private VPN network**: once the phone and the computer are in the same network, use that network's address from the list (these networks often use the `100.64.*.*` to `100.127.*.*` range).
- When the phone is on mobile data, or the other side has a public address, Mika refuses the connection.

In your MCP client, add a server of type "HTTP" (also called Streamable HTTP), enter the address, and add the header `Authorization: Bearer <token>`. Each client words its config file a little differently; most look like this:

```json
{
  "mcpServers": {
    "mika": {
      "type": "http",
      "url": "http://192.168.1.23:8765/mcp",
      "headers": { "Authorization": "Bearer <token>" }
    }
  }
}
```

You can also try it from a terminal first (use your own address and token):

```sh
curl -s http://192.168.1.23:8765/mcp \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

## Tools

| Tool | Arguments | What it does |
| --- | --- | --- |
| `mika_run_task` | `goal` (required), `conversation` (optional) | Hands Mika a job and returns a `task_id` right away. The task is recorded in a conversation you can see in the app. To follow up on an earlier job, pass the `conversation` that task returned. |
| `mika_task_status` | `task_id` | The task's state now: status, latest progress, what it is waiting for, and the answer when done. |
| `mika_wait_task` | `task_id`, `timeout_s` (optional, default 30, at most 50) | Waits until the status changes (for example Mika starts waiting for an answer, or finishes) or time runs out, then returns the state. Progress notes alone don't count, so it doesn't return on every step. |
| `mika_answer` | `task_id`, `text` | Answers the question Mika asked in this task. |
| `mika_cancel_task` | `task_id` | Stops the task. |
| `mika_list_tasks` | `limit` (optional, default 10) | The most recent tasks handed over from computers, newest first. |

Statuses:

- `starting`: just handed to the phone, not started yet.
- `running`: in progress, with the latest progress note.
- `paused`: waiting for a condition, such as the phone being unlocked (it carries on by itself afterwards), or waiting in the queue.
- `waiting_for_answer`: Mika asked a question; answer with `mika_answer` (answering on the phone works too). If nobody answers for a long time, Mika carries on as if there were no answer.
- `waiting_on_phone`: this step can only be handled by a person on the phone, such as a step handed over for you to do yourself. The computer can't answer it; wait or cancel. The phone shows its usual notification.
- `done`: finished, with the answer; `verified` says whether the answer was checked.
- `failed`: not done, with the reason.
- `cancelled`: stopped.

Suggested flow: `mika_run_task` → `mika_wait_task` repeatedly → on `waiting_for_answer`, reply with `mika_answer` → until `done` / `failed` / `cancelled`.

## Security

- **Off by default**; it listens only while on and stops at once when turned off.
- **Every request needs the token.** The token is 32 random bytes and is compared in constant time; 5 wrong tries from one address within a minute lock that address out for a minute.
- **No browser requests**: anything carrying an `Origin` header is refused, so web pages can't reach the port through your browser.
- **Private networks only**: both ends must be on a LAN, a private VPN network, or the phone itself; connections from public addresses are dropped immediately.
- **Spending and confirmations stay on the phone**: in tasks handed over from a computer, payments and actions that need your approval are never carried out, whether or not "Autonomous mode" is on; a step handed over for you to do yourself can only be done on the phone. `mika_answer` answers questions only and cannot approve any of these.
- **Only the computer's own tasks**: a computer can't see or stop tasks you started on the phone, and the conversations it opens use a fixed prefix, so it can't join your conversations on the phone.
- Every run record notes that the run was started by a computer (MCP).
- The transport is plain HTTP, so use it only on networks you trust. Don't share the token; if you think it leaked, use "New token".

## When it doesn't work

- **No address in the list**: the phone isn't on Wi-Fi or a private VPN network.
- **Status says "Couldn't start"**: another app may be using the port; turn the switch off and on again.
- **The computer can't connect**: make sure both are on the same network; some Wi-Fi networks (guest networks, for example) block devices from reaching each other.
- **The task never starts** (the call fails right away, or the task turns `failed` after two minutes): open Mika on the phone and try again. Android sometimes stops a background app from starting work on its own; once you've opened Mika it works.
- **The task stays `paused`**: the phone is most likely locked. Mika needs the phone unlocked to work.

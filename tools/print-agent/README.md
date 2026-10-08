# Shop print agent

Prints barcode labels on the shop's printer when the application is hosted
somewhere else.

## Why it exists

Direct printing works by asking the operating system the application runs on
which printers it has. While the application ran on the counter PC, that was
the right machine. Hosted on a server, it asks a data centre — which has no
printers, and no route to the one on the shop's USB cable.

So the server stops spooling and starts queueing. It renders each page to a
bitmap at the printer's own resolution — the same drawing code, so the tag comes
out identical to the preview — and this agent collects the pages and puts them
on the real printer.

Everything about how a tag looks is still decided on the server: layout, type
sizes, the logo, the calibration. This end receives a bitmap and a paper size.

## What you need on the shop PC

- A Java runtime, 17 or newer. `java -version` should print something.
- The label printer installed and working (print a Windows test page first).
- This folder: `PrintAgent.java` and `agent.properties`.

Nothing else. No build, no libraries, no installer.

## Setting it up

**1. Make an account for the agent.**

In the application: Users → Add. Give it a username like `print-agent`, a long
random password, and a role that includes **Print barcode labels**
(`LABEL_CREATE`). Nothing else. This account's password lives on the shop PC, so
keep its permissions to the minimum.

**2. Fill in `agent.properties`:**

```properties
server.url=https://your-erp-domain.com/api
agent.username=print-agent
agent.password=the-password-you-just-set
printer.name=Bar Code Printer T-9650 Plus
```

Note the `/api` on the end of the URL.

`printer.name` is worth setting. Without it the agent uses whatever Windows
calls the default printer, and that is a machine-wide setting anyone can
change — the counter PC usually has an office printer on it too, and a tag
must not end up on A4. The agent prints the exact names it can see when it
starts; copy one of those.

**3. Start it:**

```
start-agent.bat
```

or directly:

```
java PrintAgent.java agent.properties
```

It prints the printers it can see and then waits. Leave the window open.

**4. Point the application at it.**

In the application: Labels → Settings → **Where labels print** →
*Printer at the shop (via the print agent)*. The printer dropdown will now list
the printers the agent reported, not the server's. Choose the label printer.

## Which printer a page goes to

Three places decide it, most specific first:

1. The printer chosen in **Labels → Settings** on the server, which travels
   with the page.
2. `printer.name` in this agent's configuration.
3. Failing both, whatever Windows calls the default printer.

So leaving Label Settings on *"Use this computer's default printer"* is fine
as long as `printer.name` is set here. If neither is set and the counter PC's
default is the office printer, that is where the tags will go.

A name that is not installed is reported rather than quietly swapped, and the
message says which of the three it came from.

## Starting it automatically

Put a shortcut to `start-agent.bat` in the Startup folder — press `Win+R`, enter
`shell:startup`, and drop the shortcut in. It then starts whenever someone logs
in to the counter PC.

For a PC that should print without anyone logged in, use Task Scheduler with
"Run whether user is logged on or not". Note that a printer installed only for
one user may not be visible to a service account.

## How it behaves

- Polls every 3 seconds when idle, and goes straight round again while there is
  a backlog, so a counter never waits on the poll interval.
- Backs off to 15 seconds when the server cannot be reached, and recovers on its
  own.
- Signs in again by itself when its access token expires.
- A page it cannot print goes back in the queue and is retried up to three
  times, then marked failed with the reason. It does not retry for ever — a tag
  that will not print is usually a printer that is switched off, and hammering
  it just hides that.
- A page it takes and never reports on — the window was closed, the PC was
  switched off — goes back in the queue after three minutes. Nothing is lost
  silently.

## When nothing prints

**Labels → Settings says the agent is offline.** The window is closed, the PC is
off, or it cannot reach the server. Look at the agent window: it prints why on
every failed attempt.

**"printer ... is not installed on this PC".** The name does not match a
printer on the counter PC. The message says where the name came from — Labels
→ Settings, or `printer.name` here. The agent lists the exact names it can see
when it starts; copy one of those.

**Pages queue but never print.** Check the agent window for errors. Failed pages
are counted on the settings screen.

**It prints, but the tag is wrong.** That is a layout or calibration question,
not an agent one — the agent draws nothing. Use the preview in the application
and the settings there.

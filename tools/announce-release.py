#!/usr/bin/env python3
"""Announce a firmware release to Discord.

Posts the release notes to a channel webhook. Nothing is uploaded: the
images stay on firmware.mono.si, so the licensed NXP ucode they contain
never leaves our own server.

The version comes from conf/machine/gateway-dk.conf and the notes from
CHANGELOG.md -- every entry down to, but not including, the one that bumped
to the previous version, so a release that took several changes announces
all of them.

The webhook URL is a credential: anyone holding it can post to the channel.
It is read from $DISCORD_WEBHOOK, or from ~/.config/mono/release-webhook.
"""

import json
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MACHINE_CONF = REPO / "conf/machine/gateway-dk.conf"
CHANGELOG = REPO / "CHANGELOG.md"
WEBHOOK_FILE = Path.home() / ".config/mono/release-webhook"

BASE_URL = "https://firmware.mono.si"
EMBED_COLOR = 0x2ECC71

# Discord's limits. The description is the one that actually bites: our
# entries run well past a thousand characters.
MAX_DESCRIPTION = 4096
MAX_TITLE = 256


def fail(msg):
    sys.exit(f"announce: {msg}")


def read_version():
    m = re.search(
        r'^FIRMWARE_VERSION\s*\??=\s*"([^"]+)"',
        MACHINE_CONF.read_text(),
        re.M,
    )
    if not m:
        fail(f"no FIRMWARE_VERSION in {MACHINE_CONF}")
    return m.group(1)


def read_notes(version):
    """Every changelog section belonging to this release.

    Sections are newest first. We take them until one announces a bump to
    some *other* version, which is where the previous release ended.
    """
    sections = re.split(r"^## ", CHANGELOG.read_text(), flags=re.M)[1:]
    bump = re.compile(r"Bump FIRMWARE_VERSION to (\S+)")

    picked = []
    for section in sections:
        other = [v for v in bump.findall(section) if v != version]
        if other:
            break
        picked.append("## " + section.strip())

    if not picked:
        fail("changelog has no entries for this release")
    return "\n\n".join(picked)


def read_webhook():
    url = os.environ.get("DISCORD_WEBHOOK", "").strip()
    if url:
        return url
    if not WEBHOOK_FILE.exists():
        fail(
            f"no webhook. Put the URL in {WEBHOOK_FILE} (chmod 600) "
            "or set $DISCORD_WEBHOOK"
        )
    mode = WEBHOOK_FILE.stat().st_mode & 0o077
    if mode:
        fail(f"{WEBHOOK_FILE} is group/world readable -- chmod 600 it")
    url = WEBHOOK_FILE.read_text().strip()
    if not url:
        fail(f"{WEBHOOK_FILE} is empty")
    return url


def build_payload(version, notes):
    install = f"`firmware update --url {BASE_URL}/{version}`"

    if len(notes) > MAX_DESCRIPTION:
        tail = "\n\n… truncated, see CHANGELOG.md for the rest."
        notes = notes[: MAX_DESCRIPTION - len(tail)].rstrip() + tail

    return {
        "embeds": [
            {
                "title": f"Gateway firmware {version}"[:MAX_TITLE],
                "description": notes,
                "color": EMBED_COLOR,
                "fields": [{"name": "Install", "value": install}],
            }
        ]
    }


def main():
    assume_yes = "--yes" in sys.argv

    version = read_version()
    payload = build_payload(version, read_notes(version))

    print(payload["embeds"][0]["title"])
    print()
    print(payload["embeds"][0]["description"])
    print()
    print("Install:", payload["embeds"][0]["fields"][0]["value"])
    print()

    if not assume_yes:
        try:
            if input("Post this to Discord? [y/N] ").strip().lower() != "y":
                # Declining is a choice, not a failure.
                print("announce: not posted")
                return
        except EOFError:
            fail("no tty to confirm on, pass --yes")

    # curl rather than urllib so the URL never reaches a traceback.
    proc = subprocess.run(
        [
            "curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}",
            "-H", "Content-Type: application/json",
            "--data-binary", "@-",
            read_webhook(),
        ],
        input=json.dumps(payload).encode(),
        capture_output=True,
    )
    code = proc.stdout.decode().strip()
    if code != "204":
        fail(f"Discord returned HTTP {code}: {proc.stderr.decode().strip()}")

    print(f"announced {version}")


if __name__ == "__main__":
    main()

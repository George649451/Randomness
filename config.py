"""Configuration: the inline "modes" and their Claude instructions.

Each mode maps a short keyword (what the user types after @yourbot) to a system
prompt. The user's text is sent as the user turn; the mode's instructions are the
system prompt. Add or edit modes freely — keep the instructions tight and tell
Claude to output ONLY the result, since the response goes straight into a chat.
"""

from __future__ import annotations

import os

# The mode used when the query has no recognized prefix.
DEFAULT_MODE = "ask"

# mode keyword -> (emoji, human label, system prompt / instructions)
MODES: dict[str, tuple[str, str, str]] = {
    "ask": (
        "💬",
        "Ask Claude",
        "You are a helpful, concise assistant. Answer the user's message directly "
        "and completely in a few sentences. No preamble, no sign-off. Plain text "
        "only (this is going into a chat message).",
    ),
    "translate": (
        "🌐",
        "Translate",
        "You are a translation engine. Detect the language of the user's text and "
        "translate it to English; if it is already English, translate it to Spanish. "
        "Output ONLY the translation — no quotes, no notes, no language labels.",
    ),
    "summarize": (
        "📝",
        "Summarize",
        "Summarize the user's text in 2–3 short, clear sentences capturing the key "
        "points. Output only the summary — no preamble.",
    ),
    "fix": (
        "✅",
        "Fix grammar",
        "You are a grammar and spelling corrector. Return the user's text with "
        "grammar, spelling, and punctuation corrected, preserving the original "
        "meaning and tone. Output ONLY the corrected text.",
    ),
    "explain": (
        "🧠",
        "Explain simply",
        "Explain the user's topic or text clearly and simply, as if to a curious "
        "beginner, in a short paragraph. No preamble.",
    ),
    "rewrite": (
        "✍️",
        "Make it professional",
        "Rewrite the user's text in a clear, professional, friendly tone suitable "
        "for a work message. Keep it concise. Output ONLY the rewritten text.",
    ),
}


def emoji(mode: str) -> str:
    return MODES[mode][0]


def label(mode: str) -> str:
    return MODES[mode][1]


def instructions(mode: str) -> str:
    return MODES[mode][2]


def parse_query(query: str) -> tuple[str, str]:
    """Split a raw inline query into (mode, text).

    Recognizes `mode: text` or `mode text` when the first token is a known mode.
    Otherwise falls back to DEFAULT_MODE with the whole query as the text.
    """
    stripped = query.strip()
    if not stripped:
        return DEFAULT_MODE, ""

    # Split off the first token (allow an optional trailing colon).
    head, sep, tail = stripped.partition(" ")
    key = head.rstrip(":").lower()
    if key in MODES and (sep or head.endswith(":")):
        return key, tail.strip()

    # Also support "mode:text" with no space.
    if ":" in stripped:
        maybe_mode, _, rest = stripped.partition(":")
        if maybe_mode.strip().lower() in MODES:
            return maybe_mode.strip().lower(), rest.strip()

    return DEFAULT_MODE, stripped


# --- Runtime settings (env-overridable) -----------------------------------

TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")

CLAUDE_MODEL = os.environ.get("CLAUDE_MODEL", "claude-opus-4-8")
# Keep max_tokens modest: Telegram messages cap at 4096 chars, and inline replies
# should be snappy. Raise it if you switch to long-form modes.
CLAUDE_MAX_TOKENS = int(os.environ.get("CLAUDE_MAX_TOKENS", "1024"))
# effort: low | medium | high | max. `low` keeps inline replies fast.
CLAUDE_EFFORT = os.environ.get("CLAUDE_EFFORT", "low")

# Telegram hard limit on a text message.
TELEGRAM_MAX_CHARS = 4096

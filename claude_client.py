"""Thin async wrapper around the Anthropic Messages API.

Builds a prompt from (instructions, user_input) and returns the response text.
The instructions are stable per mode, so we cache them with prompt caching to
cut latency and cost across repeated calls.
"""

from __future__ import annotations

import anthropic

import config

# Resolves credentials from the environment (ANTHROPIC_API_KEY).
_client = anthropic.AsyncAnthropic()


async def generate(instructions: str, user_input: str) -> str:
    """Send one request to Claude and return the concatenated text output."""
    response = await _client.messages.create(
        model=config.CLAUDE_MODEL,
        max_tokens=config.CLAUDE_MAX_TOKENS,
        thinking={"type": "adaptive"},
        output_config={"effort": config.CLAUDE_EFFORT},
        system=[
            {
                # Stable per mode -> cache it so repeated calls hit the cache.
                "type": "text",
                "text": instructions,
                "cache_control": {"type": "ephemeral"},
            }
        ],
        messages=[{"role": "user", "content": user_input}],
    )

    text = "\n".join(
        block.text for block in response.content if block.type == "text"
    ).strip()
    return text or "(Claude returned an empty response.)"

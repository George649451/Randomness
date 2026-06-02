# Inline Claude Bot

A Telegram **inline bot** that runs whatever you type through Claude and drops the
result into the chat. Works from any conversation — a DM, a group, a channel —
without the bot being a member of it.

```
@yourbot translate: bonjour le monde
@yourbot summarize: <paste a long paragraph>
@yourbot fix: i has went too the stor
@yourbot what is the capital of France?
```

Tapping a result sends an instant placeholder, then the bot calls Claude and
**edits the message in place** with the answer — so the model's few-second
latency never blocks Telegram's inline UI.

## A note on privacy

This is an *inline* bot. By design it is **not** in your conversations and cannot
read them. Telegram only sends it the text you type after `@yourbot`, plus your
user ID. It can't see other people's messages or the chat history. When you pick
a result, *you* send the message — optionally tagged "via @yourbot".

## How it works

1. **`inline_query`** — you type `@yourbot …`; the bot offers one result per
   *mode* (Ask / Translate / Summarize / Fix / Explain / Rewrite — see
   `config.py`), detected mode first.
2. You tap one → Telegram sends a placeholder message with a loading button.
   The button matters: it guarantees Telegram returns an `inline_message_id`.
3. **`chosen_inline_result`** — the bot looks up your text, calls Claude
   (instructions = the mode's system prompt, your text = the user turn), and
   **edits** the placeholder with the answer.

You can also just DM the bot directly — any message is treated as an "ask".

## Setup

### 1. Create the bot with @BotFather

In Telegram, talk to [@BotFather](https://t.me/BotFather):

1. `/newbot` → choose a name and username → copy the **token**.
2. `/setinline` → select your bot → set a placeholder like `Ask Claude…`.
   **(Required — enables inline mode.)**
3. `/setinlinefeedback` → select your bot → choose **Enabled** (e.g. 100%).
   **(Required — without this the bot never receives `chosen_inline_result`
   and can't edit the message with the answer.)**

### 2. Configure

```bash
cp .env.example .env
# edit .env: TELEGRAM_BOT_TOKEN and ANTHROPIC_API_KEY
```

### 3. Install and run

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python bot.py
```

The bot runs via long polling — no public URL or webhook needed.

## Customizing

- **Add or change modes** in `config.py` → `MODES`. Each is
  `keyword -> (emoji, label, system prompt)`. Keep prompts tight and tell Claude
  to "output ONLY the result" — the text goes straight into a chat.
- **Model / cost / speed**: `CLAUDE_MODEL`, `CLAUDE_MAX_TOKENS`, `CLAUDE_EFFORT`
  in `.env`. `CLAUDE_EFFORT=low` keeps inline replies snappy; raise to
  `medium`/`high` for harder tasks.

## Files

| File | Purpose |
|------|---------|
| `bot.py` | Telegram handlers + the placeholder→edit flow |
| `config.py` | Modes/instructions + query parsing + settings |
| `claude_client.py` | Async Anthropic Messages API wrapper (with prompt caching) |

## Notes

- Telegram caps a message at 4096 chars; long answers are truncated. `max_tokens`
  defaults to 1024 to stay well under that and keep replies fast.
- Pending inline requests are held in a bounded in-memory map. For multi-process
  or restart-durable deployments, back it with Redis instead.

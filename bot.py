"""An inline Telegram bot that runs your text through Claude.

Usage from any chat (DM, group, channel) where the bot is available:

    @yourbot translate: bonjour le monde
    @yourbot summarize: <long text>
    @yourbot what is the capital of France?

The bot returns a list of "modes" (defined in config.py). Tapping one sends a
placeholder message instantly, then the bot calls Claude and edits the message
in place with the result — so the few-second model latency doesn't block the
inline UI.

The bot never reads the conversation it's used in. Inline mode only delivers the
text typed after @yourbot, plus the invoking user's ID. See README.md.
"""

from __future__ import annotations

import logging
import uuid
from collections import OrderedDict

from dotenv import load_dotenv

# Load .env before `config` reads environment variables at import time.
load_dotenv()

from telegram import (  # noqa: E402
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    InlineQueryResultArticle,
    InputTextMessageContent,
    Update,
)
from telegram.ext import (  # noqa: E402
    Application,
    CallbackQueryHandler,
    ChosenInlineResultHandler,
    CommandHandler,
    ContextTypes,
    InlineQueryHandler,
    MessageHandler,
    filters,
)

import config  # noqa: E402
from claude_client import generate  # noqa: E402

logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s", level=logging.INFO
)
logger = logging.getLogger("inline-claude-bot")

# Maps an inline result id -> (mode, text). Populated when we answer an inline
# query, consumed when the user picks a result. Bounded so it can't grow forever
# if results are offered but never chosen.
_PENDING: "OrderedDict[str, tuple[str, str]]" = OrderedDict()
_PENDING_MAX = 2000


def _remember(mode: str, text: str) -> str:
    rid = uuid.uuid4().hex
    _PENDING[rid] = (mode, text)
    while len(_PENDING) > _PENDING_MAX:
        _PENDING.popitem(last=False)
    return rid


def _loading_markup() -> InlineKeyboardMarkup:
    # Attaching an inline keyboard guarantees `inline_message_id` is delivered in
    # the chosen_inline_result update, which we need to edit the message later.
    return InlineKeyboardMarkup(
        [[InlineKeyboardButton("⏳ Generating…", callback_data="noop")]]
    )


# --- Handlers --------------------------------------------------------------


async def on_inline_query(update: Update, _ctx: ContextTypes.DEFAULT_TYPE) -> None:
    """Offer one result per mode, with the detected/default mode first."""
    query = update.inline_query.query
    mode, text = config.parse_query(query)

    if not text:
        # Nothing to act on yet — show a hint instead of empty results.
        await update.inline_query.answer(
            [],
            cache_time=1,
            is_personal=True,
            button=None,
        )
        return

    ordered = [mode] + [m for m in config.MODES if m != mode]
    preview = text if len(text) <= 100 else text[:99] + "…"

    results = []
    for m in ordered:
        rid = _remember(m, text)
        results.append(
            InlineQueryResultArticle(
                id=rid,
                title=f"{config.emoji(m)} {config.label(m)}",
                description=preview,
                input_message_content=InputTextMessageContent(
                    f"{config.emoji(m)} {config.label(m)} — ⏳ thinking…\n\n{preview}"
                ),
                reply_markup=_loading_markup(),
            )
        )

    # cache_time=0 + is_personal: never reuse result ids across users/queries.
    await update.inline_query.answer(results, cache_time=0, is_personal=True)


async def on_chosen_result(update: Update, ctx: ContextTypes.DEFAULT_TYPE) -> None:
    """User picked a result: call Claude, then edit the sent message in place."""
    chosen = update.chosen_inline_result
    inline_message_id = chosen.inline_message_id
    entry = _PENDING.pop(chosen.result_id, None)

    if inline_message_id is None:
        # Happens when inline feedback isn't enabled in BotFather, or no keyboard
        # was attached. We can't edit the message without this id.
        logger.warning(
            "No inline_message_id — enable /setinlinefeedback in BotFather."
        )
        return
    if entry is None:
        logger.warning("Chosen result %s not found in pending map.", chosen.result_id)
        return

    mode, text = entry
    header = f"{config.emoji(mode)} {config.label(mode)}"
    try:
        answer = await generate(config.instructions(mode), text)
    except Exception:  # noqa: BLE001 — surface any failure to the user
        logger.exception("Claude call failed")
        answer = "⚠️ Sorry, something went wrong talking to Claude. Try again."

    body = f"{header}\n\n{answer}"
    if len(body) > config.TELEGRAM_MAX_CHARS:
        body = body[: config.TELEGRAM_MAX_CHARS - 1] + "…"

    # Plain text (no parse_mode) so arbitrary model output can't break entity parsing.
    await ctx.bot.edit_message_text(
        text=body,
        inline_message_id=inline_message_id,
        reply_markup=None,  # drop the loading button
    )


async def on_noop_callback(update: Update, _ctx: ContextTypes.DEFAULT_TYPE) -> None:
    # The loading button: just acknowledge if tapped while we're still generating.
    await update.callback_query.answer("Still generating…")


async def on_private_message(update: Update, _ctx: ContextTypes.DEFAULT_TYPE) -> None:
    """Bonus: in a direct chat with the bot, treat any text as an 'ask'."""
    text = (update.message.text or "").strip()
    if not text:
        return
    mode, parsed = config.parse_query(text)
    placeholder = await update.message.reply_text(
        f"{config.emoji(mode)} {config.label(mode)} — ⏳ thinking…"
    )
    try:
        answer = await generate(config.instructions(mode), parsed or text)
    except Exception:  # noqa: BLE001
        logger.exception("Claude call failed")
        answer = "⚠️ Sorry, something went wrong talking to Claude. Try again."
    body = answer[: config.TELEGRAM_MAX_CHARS]
    await placeholder.edit_text(body)


async def on_start(update: Update, ctx: ContextTypes.DEFAULT_TYPE) -> None:
    modes = "\n".join(
        f"  {config.emoji(m)} `{m}` — {config.label(m)}" for m in config.MODES
    )
    username = ctx.bot.username or "yourbot"
    await update.message.reply_text(
        "Hi! I'm an inline Claude bot. Use me from *any* chat by typing:\n\n"
        f"`@{username} translate: bonjour`\n\n"
        "Available modes:\n" + modes + "\n\n"
        "Or just message me here directly and I'll answer.",
        parse_mode="Markdown",
    )


def build_application() -> Application:
    if not config.TELEGRAM_BOT_TOKEN:
        raise SystemExit("TELEGRAM_BOT_TOKEN is not set (see .env.example).")

    app = Application.builder().token(config.TELEGRAM_BOT_TOKEN).build()

    app.add_handler(CommandHandler(["start", "help"], on_start))
    app.add_handler(InlineQueryHandler(on_inline_query))
    app.add_handler(ChosenInlineResultHandler(on_chosen_result))
    app.add_handler(CallbackQueryHandler(on_noop_callback, pattern="^noop$"))
    app.add_handler(
        MessageHandler(
            filters.TEXT & ~filters.COMMAND & filters.ChatType.PRIVATE,
            on_private_message,
        )
    )
    return app


def main() -> None:
    app = build_application()
    logger.info("Starting bot (polling)…")
    # ALL_TYPES ensures chosen_inline_result updates are delivered.
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()

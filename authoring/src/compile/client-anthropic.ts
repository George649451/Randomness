// The real CompileClient — the one module that imports the SDK and hits the API.
//
// Model: claude-opus-4-8 (the current most-capable Claude; the spec reserves the
// cheap Haiku-class model for the per-message router, and a stronger model for the
// authoring compile). Key lives client-side: this is an internal tool with trusted
// users (spec §4.2), an explicitly accepted tradeoff — so we run in the browser via
// dangerouslyAllowBrowser. tool_choice is "auto" so the model freely picks ask vs
// commit; we force a single tool with disable_parallel_tool_use and fall back to
// treating bare text as a clarifying question.

import Anthropic from "@anthropic-ai/sdk";
import type { ToolRegistry } from "../descriptors/types";
import { buildSystemPrompt, buildTurnMessage, COMPILE_TOOLS } from "./prompt";
import { parseDecision } from "./parse";
import type { CompileClient, CompileInput, ModelDecision } from "./types";

const MODEL = "claude-opus-4-8";

export interface AnthropicClientConfig {
  apiKey: string;
  registry: ToolRegistry;
  initialKeys?: string[];
}

export class AnthropicCompileClient implements CompileClient {
  private client: Anthropic;
  private system: string;

  constructor(cfg: AnthropicClientConfig) {
    this.client = new Anthropic({ apiKey: cfg.apiKey, dangerouslyAllowBrowser: true });
    this.system = buildSystemPrompt(cfg.registry, cfg.initialKeys ?? []);
  }

  async decide(input: CompileInput): Promise<ModelDecision> {
    const messages: Anthropic.MessageParam[] = [
      ...input.history.map((m) => ({ role: m.role, content: m.text })),
      { role: "user" as const, content: buildTurnMessage(input) },
    ];

    const response = await this.client.messages.create({
      model: MODEL,
      max_tokens: 4096,
      system: this.system,
      tools: COMPILE_TOOLS as unknown as Anthropic.Tool[],
      tool_choice: { type: "auto", disable_parallel_tool_use: true },
      messages,
    });

    const toolUse = response.content.find((b) => b.type === "tool_use");
    if (toolUse && toolUse.type === "tool_use") {
      return parseDecision(toolUse.name, toolUse.input);
    }

    // No tool call — treat any text the model returned as a clarifying question.
    const text = response.content
      .filter((b): b is Anthropic.TextBlock => b.type === "text")
      .map((b) => b.text)
      .join("\n")
      .trim();
    return { kind: "ask", question: text || "Could you clarify what you'd like to change?" };
  }
}

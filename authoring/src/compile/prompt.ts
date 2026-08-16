// Pure prompt construction for the compile layer. No SDK imports here — this
// module just turns (registry, graph, history, brief) into the strings and tool
// schemas the model call needs, so it can be unit-tested without a network.

import type { ToolRegistry } from "../descriptors/types";
import type { CompileInput } from "./types";

/** Render the tool registry as the model-facing vocabulary (spec §5.1). */
function renderRegistry(registry: ToolRegistry): string {
  return Object.values(registry)
    .map((t) => {
      const inputs = Object.entries(t.inputs)
        .map(([n, s]) => `${n}: ${s.type}${s.required ? " (required)" : ""}`)
        .join(", ");
      const returns = Object.entries(t.returns)
        .map(([n, ty]) => `${n}: ${ty}`)
        .join(", ");
      return [
        `- ${t.name} [${t.kind}/${t.tier}]: ${t.description}`,
        `    inputs: ${inputs || "(none)"}`,
        `    returns: ${returns || "(none)"}`,
      ].join("\n");
    })
    .join("\n");
}

export function buildSystemPrompt(registry: ToolRegistry, initialKeys: string[]): string {
  return `You are the authoring assistant for a customer-support workflow tool. An Ops
author describes a workflow in plain language; you translate that intent into a
deterministic workflow graph by proposing precise edits.

# The graph
A workflow is { "start": "<node id>", "nodes": [ ... ] }. Three node shapes:

- to-do (an action/lookup):
  { "id", "do": "<tool name>", "args": { "k": "{{path}}" }, "as": "<ctx key>",
    "on_empty": "<id>", "on_error": "<id>", "on_timeout": "<id>", "next": "<id>" }
  Every to-do MUST have on_error and on_timeout exits (no to-do without a failure path).
- condition (a branch):
  { "id", "branch": [ { "when": "<expr>", "goto": "<id>" },
                      { "when_llm": "<question>", "goto": "<id>" },
                      { "else": "<id>" } ] }
  Every branch MUST have an "else". "when" is a deterministic expression over the run
  context (==, !=, <, <=, >, >=, and, or, in, dotted field access). "when_llm" is a
  fuzzy/semantic judgement the model evaluates at runtime (true/false).
- reply (drafts text, never sends):
  { "id", "say": "<prose instruction>", "next": null }

Templating: "{{path.to.value}}" reads the run context. Context roots that exist:
${initialKeys.map((k) => `  - ${k} (initial)`).join("\n") || "  (none)"}
plus the "as" output of every to-do node.

# Discipline (non-negotiable)
- Conditions are EDGES, never prose. Never write "...and if they're angry, apologize"
  inside a say; that branch must be a condition node.
- Prefer deterministic "when" over "when_llm". Only use "when_llm" for genuinely fuzzy
  judgements ("the customer sounds upset").
- Emit TARGETED diffs, never the whole graph. A turn about one node must not touch others.

# Available tools (the only verbs you may use in a "do")
${renderRegistry(registry)}

# Your two moves each turn — call exactly one tool
1. ask_question — when the brief is missing something the schema requires: a
   required-but-unmentioned tool input, or an unfilled mandatory exit. Ask ONLY about
   schema-missing things; do not interrogate. No graph change happens.
2. commit_changes — when you can make progress: emit the diff ops plus a one-line
   summary of what you changed (the human's review surface).

Make the smallest correct change. If you are unsure whether a required input exists,
ask rather than invent it.`;
}

/** JSON Schema for one node — permissive; the validator enforces the real rules. */
const NODE_SCHEMA = {
  type: "object",
  properties: {
    id: { type: "string" },
    do: { type: "string", description: "tool name (to-do nodes)" },
    args: { type: "object", description: "tool inputs, values may use {{templates}}" },
    as: { type: "string", description: "context key to store the result under" },
    on_empty: { type: "string" },
    on_error: { type: "string" },
    on_timeout: { type: "string" },
    branch: {
      type: "array",
      items: {
        type: "object",
        properties: {
          when: { type: "string" },
          when_llm: { type: "string" },
          goto: { type: "string" },
          else: { type: "string" },
        },
      },
    },
    say: { type: "string", description: "reply prose (reply nodes)" },
    next: { type: ["string", "null"], description: "success/unconditional next; null ends" },
  },
  required: ["id"],
} as const;

/** The two tools the model may call. Shapes mirror DiffOp + the ask decision. */
export const COMPILE_TOOLS = [
  {
    name: "ask_question",
    description:
      "Ask the author one clarifying question about something the schema requires but " +
      "the brief did not supply. Does not change the graph.",
    input_schema: {
      type: "object",
      properties: {
        question: { type: "string", description: "the question to ask the author" },
        reason: { type: "string", description: "optional: why this is needed" },
      },
      required: ["question"],
    },
  },
  {
    name: "commit_changes",
    description:
      "Apply targeted edits to the workflow graph. Emit only the ops needed for this " +
      "turn — never re-emit the whole graph.",
    input_schema: {
      type: "object",
      properties: {
        summary: {
          type: "string",
          description: "one-line, human-readable description of what changed",
        },
        ops: {
          type: "array",
          description: "ordered diff operations",
          items: {
            type: "object",
            properties: {
              op: { type: "string", enum: ["set_start", "add_node", "remove_node", "update_node"] },
              node: { ...NODE_SCHEMA, description: "for add_node" },
              id: { type: "string", description: "for remove_node / update_node" },
              set: { type: "object", description: "for update_node: fields to merge (null deletes)" },
            },
            required: ["op"],
          },
        },
      },
      required: ["summary", "ops"],
    },
  },
] as const;

/** Build the per-turn user message: the current graph (truth) + the new brief. */
export function buildTurnMessage(input: CompileInput): string {
  return [
    "Current workflow JSON:",
    "```json",
    JSON.stringify(input.graph, null, 2),
    "```",
    "",
    `Author: ${input.brief}`,
  ].join("\n");
}

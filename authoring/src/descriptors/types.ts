// Tool descriptors — the contract the factory hands to the authoring tool (spec §5.1).
//
// In production the frontmatter is GENERATED from the adapter's typed schema
// (`zodToJsonSchema(inputs)`) and only the prose description is hand-written
// (spec §5, no-drift directive). Here we hand-author a small mock registry so
// the authoring tool has a vocabulary to validate against and to label boxes.

/** read | write — write trips the human-approval gate (spec §8). */
export type ToolKind = "read" | "write";

/** Runtime properties of the adapter (spec §5.3). `api` is always preferred. */
export type ToolTier = "api" | "dom" | "a11y" | "vision";

export interface InputSpec {
  type: "string" | "number" | "boolean" | "file";
  required: boolean;
}

export interface ToolDescriptor {
  name: string;
  /** Leaks into workflow behaviour: surfaced on the chart box (spec §5.1, Appendix). */
  kind: ToolKind;
  /** Leaks into workflow behaviour: surfaced on the chart box (spec §5.1, Appendix). */
  tier: ToolTier;
  inputs: Record<string, InputSpec>;
  /** Declared output fields the next node's `when`/`when_llm`/templates may read. */
  returns: Record<string, string>;
  /** Prose, matched against the author's intent in the chat. */
  description: string;
}

export type ToolRegistry = Record<string, ToolDescriptor>;

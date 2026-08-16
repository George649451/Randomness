// A scripted CompileClient for tests and offline development. Returns queued
// decisions in order — no network, no key. This is the seam that lets the engine
// and UI be exercised without the model.

import type { CompileClient, CompileInput, ModelDecision } from "./types";

export class FakeCompileClient implements CompileClient {
  private queue: ModelDecision[];
  /** Inputs seen, in order — handy for assertions in tests. */
  public readonly seen: CompileInput[] = [];

  constructor(decisions: ModelDecision[]) {
    this.queue = [...decisions];
  }

  async decide(input: CompileInput): Promise<ModelDecision> {
    this.seen.push(input);
    const next = this.queue.shift();
    if (!next) throw new Error("FakeCompileClient: no scripted decision left");
    return next;
  }
}

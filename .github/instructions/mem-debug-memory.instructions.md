---
description: 'macOS memory pressure diagnostic toolkit using Babashka tasks and bash scripts.'
applyTo: '**/**'
---

# Toolsmith mindset: Improve diagnostic tools as you go

This toolkit is built to evolve. Approach each investigation as both a diagnostic session and an opportunity to sharpen the tools themselves. We are toolsmiths—every friction point, missing metric, or repeated manual command is a signal to improve the toolkit for future use.

## Discover and use project tasks first

When exploring or diagnosing, begin by running `bb tasks` to identify available purpose-built tasks. These tasks encapsulate domain knowledge, combine live and historical data, and provide formatted output—reducing cognitive load and errors compared to manual shell commands.

For instance:
- Use `bb menu:metrics` for quick memory stats instead of chaining grep and sysctl.
- Use `bb menu:clojure-lsp` to check LSP instances instead of parsing ps aux output.

This approach leverages the project's designed workflows for accurate, efficient diagnostics.

## Custom commands are valid

Existing tasks may not cover every diagnostic need. Creating ad-hoc shell commands is completely valid when:
- Investigating something the existing tasks don't address
- Combining data in a novel way for a specific hypothesis
- Exploring unfamiliar territory

**Signal for improvement**: If you find yourself crafting custom command lines, this could indicate a missing `menu:*` task. Consider adding a new task to capture that workflow for future use.

## Evaluate snapshot coverage

Stay alert to whether the snapshot tasks (`bb snap`, `bb heavy`) are capturing the right data for current investigations. As diagnostic needs evolve or new suspects emerge:
- Check if key processes or metrics are missing from snapshot output
- Note when analysis requires data not present in historical snapshots
- Consider enhancing the snapshot scripts (`bin/memsnap-*`) to capture additional context

The snapshots are only as useful as the data they collect. If a critical metric isn't being recorded, future investigations will lack the historical baseline needed for comparison.
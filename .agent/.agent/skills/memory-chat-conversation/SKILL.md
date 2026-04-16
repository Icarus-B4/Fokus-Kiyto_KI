---
name: memory-chat-conversation
description: Automatically manage and retrieve project-specific conversation memory and session state. Use this skill when the user asks to "remember" current progress, "retrieve" session history, or when starting a new session to restore context.
---

## Conversation Memory
You're a memory systems specialist who has built AI assistants that remember users across months of interactions. You've implemented systems that know when to remember, when to forget, and how to surface relevant memories.

## Core Principles
- Memory types differ—short-term, long-term, and entity-relative.
- Memory is not just storage—it's about retrieval, relevance, and context.
- Tiered Memory System for different purposes.

## Capabilities
- short-term-memory
- long-term-memory
- entity-memory
- memory-persistence
- memory-retrieval
- memory-consolidation

## Retrieval
- Always read `.agent/CONVERSATION_MEMORY.md` at the beginning of a NEW conversation to understand the current task state and history.
- Use this to answer the user's "What did we do last time?" or "Restore my context."

## Storage & Auto-Update
- **Automatic Session Wrap-up**: You MUST perform a chronological memory update (prepended to History) as the **final action** of every major task or at the end of a session. 
- **Format**: Prepend new summaries to the **## 📜 Aktivitäts-Historie** section of `.agent/CONVERSATION_MEMORY.md`. 
- **Structure**:
  - **Date & Time Stamp**: Always include a timestamp for the new entry.
  - **Summary**: Concise bullet points of what was accomplished.
  - **Technical Insights**: New decisions (e.g., "Changed library X to version Y").
  - **Status Updates**: Mark tasks as completed or pending in the global status section if needed.
- **Rules**:
  - ⚠️ DO NOT OVERWRITE the entire History section.
  - ⚠️ DO NOT duplicate the global technical foundation at every entry.
  - ⚠️ Keep it concise to avoid context bloat in future chats.

## Related Skills
Works well with: safe-arduino-doc-reader, context-window-management, rag-implementation.

## When to Use
Use this skill whenever the user explicitly mentions "memory", "remember", or "retrieve" history. Also use it proactively at the start of a session to load context and at the end of a session to ensure persistence.
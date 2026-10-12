---
trigger: always_on
---

Agent Instructions

This file is read by automated agents (code analyzers, Al coding assistants) operating on this repository. It points them at the human-authored references they should consult before producing output.

Development Process Guide
Humans Steer, Agents Execute

Operate with harness-engineering principles:
• Humans provide intent: scope, constraints, priorities, acceptance criteria. 
• Agents execute end-to-end: implement, test, and document outcomes. 
• Keep repository artifacts as the system of record.

Plan Lifecycle Discipline
Keep plans in plans/(pending.active .completed] 
• Use pending for approved but not started work. 
• Use active for in-progress work with live status updates. 
• Use completed only after validation and handoff notes are added. 
• Every plan must include these sections in order: 
1. Objective 
2. Implementation Goal 
3. Scope 
4. Architecture Diagram 
5. Data Access Pattern 
6. API Design - new APIs or changes to existing APIs 
7. Entity Design - new entities or changes to existing entities 
8. Key Design Decisions 
9. Test Coverage Plan 
10. Verification Checklist

Required Agent Loop For Fixes/Features
1. Confirm or create the relevant plan before coding. 
2. Move/update plan state (pending -> active -> completed) as work progresses. 
3. Execute implementation and verification (tests/lints/build checks relevant to scope). 
4. Record decisions, risks, and follow-ups in the plan before completion

Guardrails
Prefer small, reviewable increments.
Encode recurring feedback into docs/rules/checks instead of repeating instructions. 
If requirements are ambiguous, ask for clarification before broad changes.
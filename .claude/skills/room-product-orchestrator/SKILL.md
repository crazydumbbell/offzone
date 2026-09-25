---
name: room-product-orchestrator
description: Offzone 제품 방향·세일즈·UI/UX·마스코트 디자인·앱 구성 그래프를 함께 생성, 수정, 업데이트하거나 이전 결과를 보완할 때 조율한다. 단순 보고서 요약·번역이나 무관한 파일 수정은 담당 작업만 직접 수행한다.
---

# Room Product Orchestrator

Read project-root ORCHESTRATOR.md first. It owns the active strategy revision, shared work IDs, scope and decision log. Read APP_METADATA.json for implementation facts; confirm important claims in source. New user instructions supersede old report recommendations.

For app upgrade planning or implementation, read APP_UPGRADE_PLAN.ko.md for the current delivery scope, dependencies and acceptance gates. The coordinator owns that plan; its estimates and proposals are not completed features or fixed deadlines.

## Team and runtime

Use one coordinator with relevant specialists from `.claude/agents/room-sales.md`, `room-ux.md`, `room-architecture.md`; add `room-mascot-designer.md` when the request changes mascot artwork or placement. The mascot specialist uses `.claude/skills/room-mascot-design/SKILL.md` and reviews actual app screenshots with Room UX. Role files use Claude's `opus` setting for that runtime. In another host, load their contents into its available agent tools and inherit a supported model; do not call an unavailable model or pretend TeamCreate exists. Codex sessions can use collaboration spawn/message tools. Sequential execution is valid if no agent runtime is available. These files do not start a background service.

## Workflow

1. Determine whether this is a new, partial or follow-up change. Read current files; preserve unrelated edits. For coordinated work, record input revision, assigned ownership and unresolved decisions in `_workspace/00_alignment.md`.
   If the user requests implementation, assign runtime file ownership separately and complete the code and appropriate validation. Report-only specialist ownership must not narrow an authorized implementation request.
2. Pin user decisions and impacted work IDs in ORCHESTRATOR.md. The coordinator alone owns shared PRODUCT.md and APP_METADATA.json. Assign each specialist its report/graph files.
3. Run independent work in parallel when useful. Each specialist reports the customer problem, promise, user action, implementation dependency, metric, confidence and unresolved conflicts using shared work IDs.
4. Exchange results: Sales asks UX whether the promise is understandable and actionable; UX asks Architecture whether all entry/exit/error paths are possible; Architecture flags unsupported claims to both. Do not generate separate conflicting backlogs.
5. Resolve conflicts using user decisions and observed behavior first, safe recovery and privacy next, then activation, repeat value and business outcomes. Preserve alternatives when evidence is insufficient; do not treat assumptions as customer approval.
6. The coordinator updates decisions, shared priorities, metadata and affected product/design pointers. Verify English-first global positioning; retain Korean localization. Document runtime localization debt separately from strategy edits.
7. Run `python3 tools/check_product_alignment.py` when available. Review graph references and actual English copy, not only file existence. Report what is synchronized, what remains proposed and which implementation/release gates are open.
8. When mascot artwork or placement changes, run the mascot skill's screenshot review loop after integration. Record its scored observations and unresolved visual defects; a generated image alone does not close the task.

## Failure handling

Retry a failed evidence/tool step once if useful, then use a bounded fallback and disclose the limitation. Missing evidence is not permission to invent a feature or metric. An incomplete required artifact remains incomplete. Do not restart unaffected specialists or rebuild a full graph for a minor copy change.

## Test scenarios

- Normal: “글로벌 영어 사용자에 맞춰 세일즈와 UX를 조정해줘.” Update the shared revision and both reports; retain work/rest/presence/personal contexts without replacing the audience with one demographic.
- Partial: “NFC 첫 설정만 개선해줘.” Read RULE-01/ACT-01, revise the UX path and graph dependency, flag the affected sales claim. Do not invent a new pricing decision.
- Conflict: “위치가 틀렸을 때 결제하고 해제하게 하면 전환이 오르지 않을까?” Evaluate the proposal, retain free recovery, record business/UX/technical concerns rather than implementing coercion.
- Missing evidence: a graph extraction misses Swift relationships. Report structural limits and source-confirmed links; do not claim full runtime coverage.
- Mascot follow-up: “선택한 고양이가 앱에 안 어울려, 배경도 보여.” Find the board original, update only affected state assets/screens, then compare real iOS and Android captures with the mascot design loop.

Meaningful checks: inconsistent strategy revisions fail; missing feature IDs in a graph fail; proposals cannot become verified features merely by editing reports. The validator cannot prove policy compliance, UX quality or device behavior.

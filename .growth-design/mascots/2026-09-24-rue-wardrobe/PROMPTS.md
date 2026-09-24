# New outfit images — exact Codex built-in `image_gen` prompt content

Each of the three requests used `codex exec --image .growth-design/mascots/2026-09-24-rue-short-skirt/assets/anchor.png` as the **selected adult Rue v6 reference** and explicitly ordered one **built-in** image_gen invocation. No fallback CLI, direct API or code drawing; these are separate single-image calls. Neither the user's original mood reference nor the previous v4 portraits were attached. Built-in outputs were copied byte-for-byte into `assets/`; generated masters never edited/retouched.

### 01 / navy + terracotta
> This is a one-asset illustration request. Use the built-in image_gen tool exactly once (not CLI, not API, no code/file editing). Attached image is the selected adult Rue v6 character identity and pose reference. Use case: identity-preserve. Asset type: adult digital self-management avatar wardrobe concept, single full-body PNG. Change ONLY outfit: replace sage-green cardigan and charcoal skirt with a relaxed deep-blue cotton cardigan over a cream camisole and a muted terracotta opaque A-line above-knee skirt, flat shoes. Preserve the exact adult face, brown bob haircut, shy kind expression, body proportions, hands-together pose, framing and drawing style of the attached reference. Hem remains above-knee as v6. Not a school uniform, not sexualized, no child traits, no writing or watermark. Ask for genuinely transparent background, no glows, no stage or floor shadow. One image, not a sheet. Return generated file absolute path and a short transparency/identity caveat; do not change anything else.

Built-in original: `/Users/exchip/.codex/generated_images/01a0d242-d252-7271-becf-ba84c4ecf621/exec-85a83e11-8f85-4216-a673-f9d012ee53f1.png`.

### 02 / pine + brown
> Use built-in image_gen exactly once to edit the attached selected adult Rue v6 illustration (no CLI/API, no files or code edits). Create ONE distinct full-body casual-weekend avatar wardrobe variant: change ONLY clothing to an ivory soft knit mock-neck under a muted warm-brown open overshirt and a deep pine-green opaque above-knee A-line skirt; preserve black flat shoes. Same mature adult face, brown bob, shy kind smile, original hands-together standing pose, head-to-toe framing and illustrated texture. No pupil/face/neckline changes; no child or school uniform, no extra props or text. Output a genuinely transparent PNG with no halo, background, stage or shadow; preserve native alpha. Return absolute generated file path and caveat.

Built-in original: `/Users/exchip/.codex/generated_images/01a0d245-323f-74c2-9fa3-ef4307fd008f/exec-9ed31d96-93f1-45d3-84b7-a0beadb176c2.png`.

### 03 / lavender + indigo
> Use built-in image_gen exactly once for one illustration edit, no CLI/API and do not edit code. Attached image is the chosen adult Rue v6 full-body avatar. New outfit only: replace sage cardigan and charcoal skirt with a soft lavender long-sleeved button cardigan over an opaque cream top and a charcoal-indigo opaque A-line above-knee skirt; keep simple flat shoes. Keep her adult age cues, identical brown bob, shy expression, hand-in-hand standing pose, body proportions, head-to-toe composition and graphic-painterly texture. Not schoolgirl, no uniform, no revealing attire, no prop or text. Require true transparent background alpha without colored glow or floor, but do not fake a cutout. One fullbody PNG. Return generated file absolute path and honesty about halo/identity.

Built-in original: `/Users/exchip/.codex/generated_images/01a0d246-661e-7520-89c7-8d4c2bbc7d21/exec-d350b170-345b-49f4-b8eb-eb0e77a4b9f4.png`.

All outputs have RGBA with zero-alpha corners, **but** visible inner halos and minor identity/texture differences. Prompt adherence is not a clean cutout guarantee; no release-ready clothing layers or matching state expressions were generated.

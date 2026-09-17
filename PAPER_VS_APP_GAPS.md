# Paper vs. Actual App — Gaps to Reconcile

Comparing "Copy of CP1-DERMALENS Chapters 1-4 REVISED.pdf" against the app as actually built.
For the group to review before the next revision pass.

---

## 1. The core methodology has quietly changed (the big one) — UPDATE 2026-09-16: now resolved

**Update**: as of 2026-09-16, the real 6-class merge model exists, is bundled, and is
live-verified — this section below describes the gap as it stood during earlier
paper-vs-app comparison; the underlying methodology mismatch is now closed. Overall
mAP50=0.654 (`training/merge_and_train_multiclass.ipynb`, run `multiclass_merged_v2`); see
`HANDOFF.md`'s YOLOv11 section for the full per-class breakdown and how the first merge
attempt's cross-condition confusion (a live acne photo misclassified as Scabies) was diagnosed
and fixed via instance-aware oversampling. One residual issue: Melasma's AP50 (0.569) is still
meaningfully behind its solo result (0.696) and didn't move between the two merge attempts —
worth another pass before calling this fully finished, but it does classify correctly, just with
lower confidence headroom than the other five conditions.

The paper (Scope §1.4; Conceptual Framework §2.3) describes **one multi-class YOLOv11 model**
trained on all six conditions simultaneously — "the model assigns it to one of the six
pre-trained condition categories... not a binary (yes/no) detection, it is a multi-class
classification." That is now what's actually running.

The remaining paper-reconciliation work is narrower than before: Table 7's dataset split, the
Detection Accuracy Testing protocol, and the Conceptual Framework diagram's "6 Skin Condition
Classes" input block can now be written to match reality rather than requiring a decision about
which reality to write toward (see items #2 and #3 below — those still need real numbers filled
in from the v2 run).

---

## 2. Confidence threshold mechanism doesn't match Table 17

Paper defines a fixed 4-tier universal scheme:
- Below 50% → Rejected
- 50–69% → Low Confidence (rejected)
- 70–86% → Acceptable (shown with advisory)
- 87–100% → High Confidence (shown with full guidance)

...sourced from Diptho & Basak's benchmark (88.7%/86.7%).

Actual app: a **single binary threshold**, derived empirically from the model's own
F1-confidence curve peak — not a fixed universal cutoff, not tiered into four display states.

**Update 2026-09-16**: now that the app runs one merged 6-class model instead of swappable
single-class ones, there's one real threshold instead of a different one per condition:
`CONFIDENCE_THRESHOLD = 0.247` (`YoloDetector.kt`), taken from the merged model's own
`BoxF1_curve.png` ("all classes 0.63 at 0.247"). The earlier per-model values below (0.207,
0.308) are from the now-superseded single-class models and no longer apply.

Real observed confidences during live testing ranged **46–78%** on the current merged model,
still well under the paper's "87% = High Confidence" bar.

---

## 3. Datasets don't match Table 6 / Table 7 at all

Paper lists specific sources with exact counts:

| Condition | Paper's count | Paper's source |
|---|---|---|
| Acne Vulgaris | 1,217 | dermnet (Kaggle) |
| Eczema | 3,413 | skin-diseases-image-dataset (Kaggle) |
| Melasma | 187 | muhammadalirhojab/melasma (Kaggle) |
| Scabies | 1,500 | scabies/scabies-ps3vx (Roboflow) |
| Warts | 644 | 31-classes-of-skin-disease (Kaggle) |
| Tinea | 1,025 | 31-classes-of-skin-disease (Kaggle) |
| **Total** | **7,986** | |

None of the actual proven datasets match those sources or counts. **Update 2026-09-16**: this is
now the final, locked-in list actually used to train the bundled `multiclass_merged_v2` model
(`training/merge_and_train_multiclass.ipynb`'s `CONDITIONS` list):

| Condition | Actual dataset used (Roboflow project, version) |
|---|---|
| Acne Vulgaris | `acne-fixed-oozdk` v2 |
| Eczema | `eczema-fixed-mdndj` v1 |
| Melasma | `dermalens-yolov11` v8 |
| Tinea | `ringworm-or-tinea` v2 |
| Warts | `warts-oyz7h` v5 |
| Scabies | `scabies-erb5y-57tb3` v1 |

Exact per-condition image/instance counts after merging and balancing aren't written down
anywhere yet (they print at notebook run-time in steps 5b/5c but weren't saved) — re-run cells
1-5 of the notebook and capture the printed counts before writing Table 7's final numbers.

Table 7 needs a full rewrite to reflect these real sources/versions — not just cosmetic edits.

---

## 4. A real architecture claim is contradicted by actual code

Paper states (§1.4 Scope, §3.1):

> "the application eliminates the need for server-side scripting: all the detection and
> classification tasks and processes will be performed entirely on the device"

But `worker/ContributionUploadWorker.kt` exists and does real network uploads (to a Google Apps
Script + Drive bridge) when a user opts into "Contribute to Research." That's server-side
involvement the paper explicitly disclaims.

**Needs reconciling**: either scope the offline claim to just detection/inference (which is
true), or disclose the contribution pipeline as a separate, explicitly opt-in, online-only
feature.

---

## 5. Features built that aren't in the Storyboard or Functional Requirements at all

- **Family Tree feature** (`FamilyTree.kt`, `FamilyTreeScreen.kt`) — condition subtype reference
  screens with original schematic icons, reached from Scan Result's "Related Conditions" card.
  Not in Table 18 (Primary User Flow, SCR-01–13), not in FR1–FR11, not in the ER diagram or use
  case diagram.
- **Per-scan "Contribute to Research" control** — currently still bundled with "Save to History"
  rather than a separate per-scan choice (flagged earlier, not yet resolved). Not documented as a
  feature or privacy consideration anywhere in the paper.
- **"Analysis Unavailable" honest-failure state** — when inference throws (OOM, corrupt image,
  etc.), the app shows an explicit failure result instead of silently falling back to fake/mock
  data. Not one of the five error states in Table 20 (ERR-01–05); worth adding as a sixth case.

---

## 6. New limitations discovered this session, not in §1.4 Limitations

- **Training data watermark contamination.** Multiple real candidate datasets (Eczema, Tinea)
  were found to carry embedded DermNet/VisualDx/Adobe Stock watermarks baked into the photos — a
  real shortcut-learning risk independent of the copyright question. Mitigated by filtering
  (Tinea) or accepted as a known risk (Eczema). Not mentioned anywhere currently.
- **Single-class models didn't discriminate between conditions — resolved 2026-09-16.** The
  earlier single-class-per-condition workflow had a real, demonstrated failure mode: the Acne
  solo model, shown a real acne photo, called it Eczema at 59.6% confidence, higher than its
  confidence on an actual Eczema photo (expected behavior for a single-class detector with no
  negative examples during training). The real 6-class merge model fixes this — confirmed via
  both its confusion matrix (cross-condition confusion between real classes tops out at 0.09) and
  live testing (the exact acne photo that misfired as Scabies at 52.3% in the merge's first
  attempt now correctly scores Acne Vulgaris). Still worth a line in Limitations about the
  *history* of this failure mode and that it's model-specific, not eliminated as a category of
  risk for any future model swap.
- **Tinea detection is scoped to the classic ring-shaped presentation only** (tinea corporis),
  not the other four location-based subtypes (pedis, cruris, capitis, unguium) that the paper's
  general "Tinea" framing implies coverage of.
- **Colab free-tier GPU quota exhaustion** was a real, recurring development constraint (multiple
  training runs blocked or interrupted). Not mentioned under Hardware/Software Requirements,
  which just states Colab provides "free T4 GPUs" with no qualification.

---

## 7. Screenshot content doesn't match real output

Figure 35's mockup shows "92% confidence" for a result. Real confidences observed live during
testing across every condition ranged roughly 40–73%. If Figure 35 stays as-is for the defense, a
panelist comparing it to a live demo could reasonably ask why the numbers don't match.

---

## Suggested next steps

1. ~~Decide: keep pursuing the real 6-class merge before defense, or reframe the paper around the
   single-class swap-one-at-a-time reality~~ **Resolved 2026-09-16**: the merge worked
   (mAP50=0.654, live-verified) — write the paper toward the merged-model reality, matching what
   §1.4/§2.3 already claimed all along.
2. Rewrite Table 6/7 with final, real dataset sources and counts (list is locked in — see #3
   above; exact per-condition counts still need to be captured from a notebook re-run).
3. Rewrite the confidence threshold section (§4.1.5.7, Table 17) to match the actual per-model
   F1-derived threshold approach, or change the app to actually implement the paper's 4-tier
   scheme — pick one and make them match.
4. Add Family Tree to the Storyboard, Functional Requirements, and relevant diagrams — or cut it
   if it's out of scope for defense.
5. Reconcile the "no server-side processing" claim with the real Contribute-to-Research upload
   pipeline.
6. Add the watermark/data-quality and single-class-discrimination findings to Limitations.
7. Replace or caveat Figure 35's mockup confidence number.

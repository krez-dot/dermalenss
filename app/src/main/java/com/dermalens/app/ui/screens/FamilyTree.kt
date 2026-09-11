package com.dermalens.app.ui.screens

import androidx.compose.ui.graphics.Color

/**
 * Which small schematic shape represents a relative in the family tree list -- deliberately not a
 * real clinical photo. Almost every dermatology reference photo is copyrighted commercial stock
 * (DermNet, VisualDx, etc.), which isn't something to embed in a shipped app without a license.
 * These are simple, original, Canvas-drawn shapes conveying the one key visual trait from each
 * relative's `distinguishingFeature` -- see the icon drawing in FamilyTreeScreen.kt.
 */
enum class LesionIconType {
    DARK_DOT,     // small dark spot on skin -- e.g. a blackhead
    PALE_BUMP,    // small pale raised bump -- e.g. a whitehead, a flat wart
    RED_BUMP,     // solid red/pink raised bump, no center -- e.g. a papule
    PUS_BUMP,     // red bump with a lighter center -- e.g. a pustule
    DEEP_BUMP,    // larger, darker, deeper lump -- e.g. a nodule
    SCALE_PATCH,  // irregular flaky/crusted patch -- e.g. dermatitis, crusted scabies
    RING,         // a ring outline, clear center -- e.g. tinea's classic ringworm shape
    ROUGH_BUMP,   // bump with an irregular, bumpy edge -- e.g. a common wart
    FLAT_PATCH    // flat pigmented patch, no raised shadow -- e.g. melasma-family discoloration
}

/**
 * One relative/subtype shown under a detected condition's family tree -- pure reference content,
 * not something the model detects. Kept separate from [DetectionResult] on purpose: these are
 * never a scan outcome, so nothing here should look like a diagnosis or imply the app can tell
 * these apart on a photo.
 */
data class FamilyTreeRelative(
    val name: String,
    val description: String,
    val distinguishingFeature: String,
    val icon: LesionIconType = LesionIconType.RED_BUMP
)

data class FamilyTree(
    /** One line on what actually groups these together (shared cause, shared category, etc.) --
     *  shown above the list so the grouping itself makes sense, not just a list of names. */
    val groupingNote: String,
    val relatives: List<FamilyTreeRelative>
)

/**
 * Static reference trees for the app's 6 detectable conditions. Content, not a detection target --
 * see chat/HANDOFF.md: subtype-splitting Acne into a detector was tried and shelved (it hurt mAP,
 * see training/acne_subtypes_future/README.md); this reuses that same clinical research as
 * education instead, which doesn't have that accuracy risk.
 *
 * IMPORTANT: this content needs a real accuracy pass before shipping -- drafted from general
 * dermatological knowledge, not sourced from a clinical reference per-line. Same bar as everything
 * else in this app that makes a medical claim.
 */
val familyTrees: Map<String, FamilyTree> = mapOf(
    "Acne Vulgaris" to FamilyTree(
        groupingNote = "Acne vulgaris isn't one lesion type -- it's a spectrum from mild, non-inflamed clogged pores to severe, deep inflammation. What it's called shifts as it progresses.",
        relatives = listOf(
            FamilyTreeRelative("Blackhead (Open Comedo)", "A clogged pore that stays open at the surface -- the trapped oil and dead skin oxidize on contact with air and darken.", "Small dark, flat bumps, no redness or pain.", LesionIconType.DARK_DOT),
            FamilyTreeRelative("Whitehead (Closed Comedo)", "A clogged pore that closes over, trapping the buildup just under the skin.", "Small closed white/skin-toned bumps, no dark center.", LesionIconType.PALE_BUMP),
            FamilyTreeRelative("Papule", "An inflamed bump where the clogged pore's wall has broken down, causing redness and swelling -- no pus yet.", "Firm, red, tender, no visible center.", LesionIconType.RED_BUMP),
            FamilyTreeRelative("Pustule", "A papule that's progressed to a visible pus-filled center as the immune response intensifies.", "Red bump with a white or yellow center.", LesionIconType.PUS_BUMP),
            FamilyTreeRelative("Nodule", "Deep, firm inflammation below the skin surface -- more severe, more likely to scar.", "Large, firm, painful, deep-seated lumps.", LesionIconType.DEEP_BUMP)
        )
    ),
    "Eczema" to FamilyTree(
        groupingNote = "\"Eczema\" is the everyday name for atopic dermatitis specifically, but it's also used loosely for a group of conditions that all cause dry, inflamed, itchy skin through different triggers.",
        relatives = listOf(
            FamilyTreeRelative("Atopic Dermatitis", "The most common form -- a chronic condition linked to an overactive immune response, often runs with allergies/asthma.", "Dry, itchy, red patches in skin folds (elbows, knees), often since childhood.", LesionIconType.SCALE_PATCH),
            FamilyTreeRelative("Contact Dermatitis", "A reaction where skin actually touched something irritating or allergenic (soap, metal, a plant).", "Redness/rash confined to where contact happened, often with a clear edge or shape matching the trigger.", LesionIconType.SCALE_PATCH),
            FamilyTreeRelative("Seborrheic Dermatitis", "Linked to oil-producing skin areas and a common yeast that lives on skin -- what causes dandruff.", "Greasy, flaking, yellowish scale on the scalp, face (around nose/eyebrows), or chest.", LesionIconType.SCALE_PATCH),
            FamilyTreeRelative("Dyshidrotic Eczema", "Small, intensely itchy blisters, cause not fully understood -- often stress- or sweat-related.", "Tiny fluid-filled blisters specifically on the palms, sides of fingers, or soles.", LesionIconType.PALE_BUMP),
            FamilyTreeRelative("Nummular Eczema", "Presents as distinct coin-shaped patches, can follow skin injury or dryness.", "Round or oval, coin-shaped patches with a clear edge -- easy to mistake for tinea.", LesionIconType.RING)
        )
    ),
    "Melasma" to FamilyTree(
        groupingNote = "Melasma is a hyperpigmentation disorder, not an infection or growth -- these are other things that cause patchy brown discoloration and commonly get confused with it.",
        relatives = listOf(
            FamilyTreeRelative("Post-Inflammatory Hyperpigmentation (PIH)", "Dark marks left behind after skin heals from acne, injury, or inflammation -- not hormonal like melasma.", "Follows the shape/location of a prior injury or breakout, fades gradually over months.", LesionIconType.FLAT_PATCH),
            FamilyTreeRelative("Solar Lentigines (Sun/Age Spots)", "Small, well-defined dark spots from cumulative sun exposure over years.", "Small, sharply-bordered individual spots, not the larger symmetrical patches melasma forms.", LesionIconType.FLAT_PATCH),
            FamilyTreeRelative("Café-au-Lait Macules", "Flat, uniformly light-brown birthmark-like patches, usually present from childhood.", "Present since childhood, doesn't change with sun exposure or hormones the way melasma does.", LesionIconType.FLAT_PATCH)
        )
    ),
    "Tinea" to FamilyTree(
        groupingNote = "Tinea is a fungal infection (ringworm) that's named for where on the body it shows up -- same type of fungus, different location and appearance.",
        relatives = listOf(
            FamilyTreeRelative("Tinea Corporis (Body)", "The classic \"ringworm\" on the trunk or limbs.", "Ring-shaped rash with a raised, scaly border and a clearer center.", LesionIconType.RING),
            FamilyTreeRelative("Tinea Pedis (Athlete's Foot)", "Fungal infection between the toes or on the soles.", "Itching, peeling, cracked skin between toes or on the sole.", LesionIconType.SCALE_PATCH),
            FamilyTreeRelative("Tinea Cruris (Jock Itch)", "Fungal infection of the groin/inner thigh area.", "Red, itchy rash with a well-defined, often scaly edge in the groin fold.", LesionIconType.RING),
            FamilyTreeRelative("Tinea Capitis (Scalp)", "Fungal infection of the scalp, most common in children.", "Patchy hair loss with scaling, sometimes broken-off hairs at the scalp.", LesionIconType.SCALE_PATCH),
            FamilyTreeRelative("Tinea Unguium (Nail Fungus)", "Fungal infection of the nail itself, not just surrounding skin.", "Thickened, discolored, crumbly nails rather than a skin rash.", LesionIconType.DARK_DOT)
        )
    ),
    "Warts" to FamilyTree(
        groupingNote = "All warts are caused by HPV (human papillomavirus), but they look different depending on where they grow and which HPV strain is involved.",
        relatives = listOf(
            FamilyTreeRelative("Common Wart (Verruca Vulgaris)", "The typical wart, usually on hands or fingers.", "Rough, raised, flesh-colored bump, sometimes with visible black dots (clotted blood vessels).", LesionIconType.ROUGH_BUMP),
            FamilyTreeRelative("Plantar Wart", "Grows on the sole of the foot, pushed inward by body weight.", "Flat rather than raised (pressure flattens it), can be tender when walking.", LesionIconType.FLAT_PATCH),
            FamilyTreeRelative("Flat Wart (Verruca Plana)", "Smaller and smoother than common warts, often appears in clusters.", "Small, smooth-topped, slightly raised bumps, often many at once on the face or legs.", LesionIconType.PALE_BUMP),
            FamilyTreeRelative("Filiform Wart", "A thin, finger-like projection, usually on the face.", "Long, narrow, thread-like growth, often around the mouth, eyes, or nose.", LesionIconType.ROUGH_BUMP)
        )
    ),
    "Scabies" to FamilyTree(
        groupingNote = "Scabies has one cause (the Sarcoptes scabiei mite), but severity varies a lot -- this is the one variant worth knowing about, not a family of different conditions.",
        relatives = listOf(
            FamilyTreeRelative("Crusted (Norwegian) Scabies", "A severe form in people with weakened immune systems, where mite numbers multiply far beyond typical scabies -- highly contagious and needs urgent care.", "Thick, crusted patches of skin covering large areas, often without the intense itching typical scabies causes.", LesionIconType.SCALE_PATCH)
        )
    )
)

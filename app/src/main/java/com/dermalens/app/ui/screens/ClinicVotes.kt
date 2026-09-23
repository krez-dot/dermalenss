package com.dermalens.app.ui.screens

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Crowd-sourced "is this clinic still open" signal for Clinic Locator -- Google's own Places
 * data can be stale for a specific branch even when the business itself hasn't been marked
 * closed there yet (a real case found live: "Professional Skin Care Formula" in Tarlac City
 * still shows businessStatus=OPERATIONAL on Google's side despite no longer existing on the
 * ground). "Report incorrect info" on the clinic popup routes that correction to Google's own
 * system, which fixes it for every Maps user eventually -- this is the faster, DermaLens-local
 * signal in the meantime: a per-clinic open/closed tally in Firestore, one vote per Firebase
 * account (re-voting changes your vote rather than double-counting; tapping your own current
 * vote again clears it).
 *
 * Deliberately NOT anonymous the way Contribute to Research's uploads are -- each vote is keyed
 * by the voter's Firebase UID so a single account can't stuff the tally, but only the aggregate
 * counts and the vote's own owner are ever readable (see the Firestore security rules this
 * needs, documented in HANDOFF.md). Writes go directly from the client via
 * FieldValue.increment() inside a transaction, no Cloud Functions involved, so this stays on
 * Firebase's free Spark plan -- the same billing constraint that pushed Contribute to Research
 * onto Apps Script instead of Firebase Storage (see that pipeline's README section).
 */
data class ClinicVoteTally(
    val confirmedOpenCount: Int,
    val confirmedClosedCount: Int,
    /** "open", "closed", or null if the current user hasn't voted on this clinic. */
    val myVote: String?
)

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

private fun clinicStatusDoc(placeId: String) =
    FirebaseFirestore.getInstance().collection("clinic_status").document(placeId)

/** Reads the current aggregate tally for [placeId] plus how [userId] voted, if at all. Safe to
 *  call for a clinic nobody has voted on yet -- an absent document just reads as zero counts. */
suspend fun fetchClinicVoteTally(placeId: String, userId: String): ClinicVoteTally {
    val statusDoc = clinicStatusDoc(placeId).get().awaitTask()
    val myVoteDoc = clinicStatusDoc(placeId).collection("votes").document(userId).get().awaitTask()
    return ClinicVoteTally(
        confirmedOpenCount = statusDoc.getLong("confirmedOpenCount")?.toInt() ?: 0,
        confirmedClosedCount = statusDoc.getLong("confirmedClosedCount")?.toInt() ?: 0,
        myVote = myVoteDoc.getString("vote")
    )
}

/**
 * Casts, changes, or clears [userId]'s vote on whether [placeId] is still open. Tapping the
 * same choice that's already your current vote clears it instead of doing nothing -- a user who
 * taps the wrong button, or just changes their mind, isn't stuck with no way back except voting
 * the opposite way first. Runs as a single Firestore transaction so the aggregate counts and
 * this user's own vote record can never drift apart (e.g. a crash between two separate writes
 * double-counting, or leaving a stale vote recorded after its count was rolled back).
 */
suspend fun castClinicVote(placeId: String, userId: String, vote: String): ClinicVoteTally {
    val statusRef = clinicStatusDoc(placeId)
    val voteRef = statusRef.collection("votes").document(userId)
    return FirebaseFirestore.getInstance().runTransaction { txn ->
        val statusSnap = txn.get(statusRef)
        val previousVote = txn.get(voteRef).getString("vote")

        var openDelta = 0L
        var closedDelta = 0L
        val newVote: String?

        if (previousVote == vote) {
            newVote = null
            if (vote == "open") openDelta = -1 else closedDelta = -1
            txn.delete(voteRef)
        } else {
            newVote = vote
            if (previousVote == "open") openDelta -= 1
            if (previousVote == "closed") closedDelta -= 1
            if (vote == "open") openDelta += 1 else closedDelta += 1
            txn.set(voteRef, mapOf("vote" to vote, "votedAt" to FieldValue.serverTimestamp()))
        }

        if (openDelta != 0L || closedDelta != 0L) {
            txn.set(
                statusRef,
                mapOf(
                    "confirmedOpenCount" to FieldValue.increment(openDelta),
                    "confirmedClosedCount" to FieldValue.increment(closedDelta)
                ),
                SetOptions.merge()
            )
        }

        val currentOpen = (statusSnap.getLong("confirmedOpenCount") ?: 0L) + openDelta
        val currentClosed = (statusSnap.getLong("confirmedClosedCount") ?: 0L) + closedDelta
        ClinicVoteTally(currentOpen.toInt(), currentClosed.toInt(), newVote)
    }.awaitTask()
}

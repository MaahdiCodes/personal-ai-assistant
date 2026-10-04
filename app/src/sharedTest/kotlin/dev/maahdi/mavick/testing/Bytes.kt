package dev.maahdi.mavick.testing

/** True if [needle] appears anywhere in this array. Used to prove secrets are not stored in plain form. */
fun ByteArray.containsSequence(needle: ByteArray): Boolean {
    if (needle.isEmpty()) return true
    if (needle.size > size) return false
    for (start in 0..size - needle.size) {
        if (needle.indices.all { offset -> this[start + offset] == needle[offset] }) return true
    }
    return false
}

package dev.maahdi.mavick.data

/** Lower-case hexadecimal, two digits per byte: how fingerprints and file checksums are written. */
fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

package io.nekohasekai.sfa.utils

import android.util.Base64

/**
 * Encoded-config support for the `ENC1:` container format.
 *
 * Container: `ENC1:` + base64( reverse( XOR(plaintext, key) ) ).
 *
 * This is obfuscation, not encryption: the key ships inside the APK, so treat it as
 * "not casually readable", not as a secret. It is deliberately cheap — no crypto
 * provider, no key derivation, nothing to slow down import.
 *
 * ## Keep in step with Go
 *
 * The same transform lives in `sing-box/common/configcodec/codec.go`, which is what
 * `sing-box encode` uses to produce the files this decodes. **The key below must match
 * `key` in that file byte for byte.** There is no compile-time link between them: if
 * they diverge, imports fail at runtime with a confusing parse error.
 *
 * `common/configcodec/codec_test.go` guards the format invariants this implementation
 * depends on (standard base64 alphabet, single line, `ENC1:` prefix).
 *
 * Once you rebuild `libbox.aar` you can delete this transform and call
 * `Libbox.decodeConfig` instead — `experimental/libbox/codec.go` already exports it —
 * which removes the duplication entirely. That needs the NDK, so it is not the MVP path.
 */
object ConfigCodec {

    private const val VERSION_PREFIX = "ENC1:"

    /** Must equal `key` in sing-box/common/configcodec/codec.go. */
    private val KEY = byteArrayOf(
        0x5A, 0x3F, 0x7B, 0x2E, 0x9C.toByte(), 0x4D, 0x1A, 0x8F.toByte(),
        0x6E, 0x2B, 0x5C, 0x9A.toByte(), 0x3D, 0x7F, 0x4E, 0x1B,
    )

    /** Encodes a plaintext config into the distributable `ENC1:` form. */
    fun encode(plaintext: String): String {
        val encoded = xorWithKey(plaintext.toByteArray(Charsets.UTF_8))
        encoded.reverse()
        return VERSION_PREFIX + Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    /**
     * Decodes an `ENC1:` config to plaintext.
     *
     * Input without the prefix is returned unchanged, so this is safe to call on a
     * config of either form. Corrupt input is also returned unchanged rather than
     * throwing: the caller then fails on the JSON parse, which produces a far clearer
     * error than a codec stack trace.
     */
    fun decode(encoded: String): String {
        if (!isEncoded(encoded)) return encoded
        // Trim both ends, as configcodec.Decode does: a distributed file very often
        // picks up a trailing newline, which must not reach the base64 payload.
        // A leading BOM (Windows Notepad adds one) is dropped too.
        val payload = trimLeading(encoded).trimEnd().removePrefix(VERSION_PREFIX)
        val decoded = try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            return encoded
        }
        decoded.reverse()
        return String(xorWithKey(decoded), Charsets.UTF_8)
    }

    /** Decodes from bytes, for file reads. */
    fun decode(data: ByteArray): String = decode(String(data, Charsets.UTF_8))

    /** Whether [data] carries the `ENC1:` prefix. */
    fun isEncoded(data: String): Boolean = trimLeading(data).startsWith(VERSION_PREFIX)

    /**
     * Whether [data] carries the `ENC1:` prefix, after an optional UTF-8 BOM and
     * leading whitespace.
     *
     * Compares byte by byte so this stays safe on binary input such as a gzipped
     * share-profile, where decoding to a String first would be lossy.
     */
    fun isEncoded(data: ByteArray): Boolean {
        var start = 0
        if (data.size >= 3 &&
            data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()
        ) {
            start = 3
        }
        while (start < data.size && data[start].toInt().toChar() in ASCII_WHITESPACE) {
            start++
        }
        if (data.size - start < VERSION_PREFIX.length) return false
        for (i in VERSION_PREFIX.indices) {
            if (data[start + i].toInt().toChar() != VERSION_PREFIX[i]) return false
        }
        return true
    }

    private const val ASCII_WHITESPACE = " \t\r\n"

    /** Drops a leading BOM (U+FEFF) and whitespace. */
    private fun trimLeading(data: String): String = data.trimStart { it == '\uFEFF' || it.isWhitespace() }

    /**
     * XOR each byte against the rotating key, indexed by position in this array.
     *
     * Order matters and the two directions are not interchangeable: encode XORs then
     * reverses, decode reverses then XORs. Doing XOR-then-reverse in both directions
     * looks like a tidy involution but only round-trips when the key length divides
     * evenly into the reversal — with this 16-byte key it silently decodes to garbage,
     * because reversing changes which key byte each plaintext position lines up with.
     *
     * Mirrors the two loops in configcodec.Encode / configcodec.Decode.
     */
    private fun xorWithKey(input: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        for (i in input.indices) {
            output[i] = (input[i].toInt() xor KEY[i % KEY.size].toInt()).toByte()
        }
        return output
    }
}

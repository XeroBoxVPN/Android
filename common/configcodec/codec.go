// Package configcodec provides encoding/decoding for obfuscated config files.
// Format: ENC1:<base64-payload>
// Transform: XOR with rotating key → reverse → base64
package configcodec

import (
	"bytes"
	"encoding/base64"
	"strings"
	"unicode"
)

const versionPrefix = "ENC1:"

// 16-byte obfuscation key (change this for your deployment)
var key = []byte{
	0x5A, 0x3F, 0x7B, 0x2E, 0x9C, 0x4D, 0x1A, 0x8F,
	0x6E, 0x2B, 0x5C, 0x9A, 0x3D, 0x7F, 0x4E, 0x1B,
}

// Encode encodes plaintext config to obfuscated format.
func Encode(plaintext string) string {
	bytes := []byte(plaintext)

	// Step 1: XOR with rotating key
	xored := make([]byte, len(bytes))
	for i := range bytes {
		xored[i] = bytes[i] ^ key[i%len(key)]
	}

	// Step 2: Reverse
	reverse(xored)

	// Step 3: Base64 encode
	encoded := base64.StdEncoding.EncodeToString(xored)

	// Step 4: Add version prefix
	return versionPrefix + encoded
}

// Decode decodes obfuscated config back to plaintext.
// If input doesn't have the ENC1: prefix, returns it unchanged (backward compat).
func Decode(encoded string) string {
	trimmed := strings.TrimSpace(trimLeading(encoded))

	// Backward compatibility: if no prefix, assume it's already plaintext
	if !strings.HasPrefix(trimmed, versionPrefix) {
		return encoded
	}

	payload := strings.TrimPrefix(trimmed, versionPrefix)

	// Step 1: Base64 decode
	bytes, err := base64.StdEncoding.DecodeString(payload)
	if err != nil {
		// Invalid base64 — return original (might be corrupted)
		return encoded
	}

	// Step 2: Reverse
	reverse(bytes)

	// Step 3: XOR with rotating key
	decoded := make([]byte, len(bytes))
	for i := range bytes {
		decoded[i] = bytes[i] ^ key[i%len(key)]
	}

	return string(decoded)
}

// DecodeBytes decodes from byte slice (convenience for file reads).
func DecodeBytes(data []byte) string {
	return Decode(string(data))
}

// IsEncoded checks if a string appears to be an encoded config.
func IsEncoded(data string) bool {
	return strings.HasPrefix(trimLeading(data), versionPrefix)
}

// IsEncodedBytes checks if a byte slice appears to be an encoded config, after an
// optional UTF-8 BOM and leading ASCII whitespace. It compares bytes without
// converting the whole slice, so it stays cheap on large or binary input.
func IsEncodedBytes(data []byte) bool {
	data = bytes.TrimPrefix(data, utf8BOM)
	data = bytes.TrimLeft(data, " \t\r\n")
	return bytes.HasPrefix(data, []byte(versionPrefix))
}

var utf8BOM = []byte{0xEF, 0xBB, 0xBF}

// trimLeading drops a leading BOM (U+FEFF, which Windows Notepad adds) and
// whitespace. strings.TrimSpace alone does not treat U+FEFF as space. Kept in step
// with trimLeading in ConfigCodec.kt.
func trimLeading(data string) string {
	return strings.TrimLeftFunc(data, func(r rune) bool {
		return r == 0xFEFF || unicode.IsSpace(r)
	})
}

// reverse reverses a byte slice in place.
func reverse(b []byte) {
	for i, j := 0, len(b)-1; i < j; i, j = i+1, j-1 {
		b[i], b[j] = b[j], b[i]
	}
}

// SetKey allows changing the obfuscation key at runtime.
// Key must be at least 1 byte. For security, use 16+ bytes.
func SetKey(newKey []byte) {
	if len(newKey) > 0 {
		key = make([]byte, len(newKey))
		copy(key, newKey)
	}
}

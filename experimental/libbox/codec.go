package libbox

import "github.com/sagernet/sing-box/common/configcodec"

// Config codec, exported to the mobile clients so the key and the algorithm live
// in exactly one place. Clients must not reimplement these: a divergent copy fails
// at runtime with no compile error.
//
// gomobile lowercases the first letter, so these bind as Libbox.encodeConfig,
// Libbox.decodeConfig and Libbox.isConfigEncoded.

// EncodeConfig encodes a plaintext config into the distributable ENC1: form.
func EncodeConfig(plaintext string) string {
	return configcodec.Encode(plaintext)
}

// DecodeConfig decodes an ENC1: config back to plaintext. Input without the
// prefix is returned unchanged, so callers may pass either form.
func DecodeConfig(encoded string) string {
	return configcodec.Decode(encoded)
}

// IsConfigEncoded reports whether data carries the ENC1: prefix.
func IsConfigEncoded(data string) bool {
	return configcodec.IsEncoded(data)
}

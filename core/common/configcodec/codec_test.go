package configcodec

import (
	"encoding/base64"
	"strings"
	"testing"
)

const sampleConfig = `{"log":{"level":"info"},"outbounds":[{"type":"direct"}]}`

func TestRoundTrip(t *testing.T) {
	t.Parallel()
	for _, input := range []string{
		sampleConfig,
		"",
		"{}",
		strings.Repeat("a", 1024), // longer than the 16-byte key, exercises rotation
		"unicode: 配置 ✓ عربى",      // multi-byte UTF-8 must survive
		"\x00\x01\xff binary-ish", // bytes outside the printable range
	} {
		if got := Decode(Encode(input)); got != input {
			t.Errorf("round trip mismatch\n input: %q\n   got: %q", input, got)
		}
	}
}

// The Android client decodes these payloads through libbox, but any client that
// reimplements the transform must produce output matching these invariants.
func TestEncodedFormInvariants(t *testing.T) {
	t.Parallel()
	encoded := Encode(sampleConfig)

	if !strings.HasPrefix(encoded, versionPrefix) {
		t.Fatalf("missing %q prefix: %q", versionPrefix, encoded)
	}
	payload := strings.TrimPrefix(encoded, versionPrefix)

	if strings.ContainsAny(payload, "\r\n") {
		t.Error("payload must be single-line: Android's Base64.NO_WRAP emits no line breaks")
	}
	// Standard alphabet with padding, i.e. Go's StdEncoding and Android's
	// Base64.NO_WRAP. URL-safe output here would break the Kotlin side.
	if strings.ContainsAny(payload, "-_") {
		t.Error("payload must use the standard base64 alphabet, not URL-safe")
	}
	if _, err := base64.StdEncoding.DecodeString(payload); err != nil {
		t.Errorf("payload is not valid standard base64: %v", err)
	}
}

func TestDecodeToleratesSurroundingWhitespace(t *testing.T) {
	t.Parallel()
	// Distributed files routinely pick up a trailing newline, from an editor or from a
	// shell redirect. Both clients trim before base64-decoding; this pins that.
	encoded := Encode(sampleConfig)
	for _, variant := range []string{
		encoded + "\n",
		encoded + "\r\n",
		"\n" + encoded,
		"  " + encoded + "  \n",
	} {
		if got := Decode(variant); got != sampleConfig {
			t.Errorf("whitespace broke decode\nvariant: %q\n    got: %q", variant, got)
		}
	}
}

func TestDecodePassesThroughPlaintext(t *testing.T) {
	t.Parallel()
	// Configs that were never encoded must survive Decode untouched, which is what
	// lets the load path call Decode unconditionally.
	for _, input := range []string{sampleConfig, "", "not json at all"} {
		if got := Decode(input); got != input {
			t.Errorf("plaintext was altered\n input: %q\n   got: %q", input, got)
		}
	}
}

func TestDecodeRejectsCorruptPayloadWithoutPanicking(t *testing.T) {
	t.Parallel()
	// A truncated or corrupt payload must return something harmless rather than
	// panicking: this input reaches Decode straight from a user-supplied file.
	for _, input := range []string{
		versionPrefix + "!!!!not base64!!!!",
		versionPrefix,
		versionPrefix + "a", // invalid base64 length
	} {
		_ = Decode(input) // must not panic
	}
}

func TestIsEncoded(t *testing.T) {
	t.Parallel()
	cases := map[string]bool{
		Encode(sampleConfig):                    true,
		"  " + Encode(sampleConfig):             true, // leading whitespace tolerated
		"\xEF\xBB\xBF" + Encode(sampleConfig):   true, // UTF-8 BOM, as Windows Notepad saves
		"\xEF\xBB\xBF\n" + Encode(sampleConfig): true,
		sampleConfig:                            false,
		"":                                      false,
		"ENC":                                   false,
		"ENC2:something":                        false, // a future version is not this one
	}
	for input, want := range cases {
		if got := IsEncoded(input); got != want {
			t.Errorf("IsEncoded(%.24q) = %v, want %v", input, got, want)
		}
		if got := IsEncodedBytes([]byte(input)); got != want {
			t.Errorf("IsEncodedBytes(%.24q) = %v, want %v", input, got, want)
		}
	}
}

// TestGoldenVector pins the exact bytes the encoder produces, so an accidental change
// to the transform or the key is caught here instead of in the field.
//
// The vector is empty until you generate it, because it depends on your deployment key:
//
//	go run ./cmd/sing-box encode testdata/sample.json
//
// Paste the output below. Regenerate it deliberately whenever you rotate the key, and
// note that doing so invalidates every config already distributed to clients.
func TestGoldenVector(t *testing.T) {
	t.Parallel()
	const golden = "" // ← paste `sing-box encode` output here
	if golden == "" {
		t.Skip("golden vector not pinned; see the comment above")
	}
	if got := Encode(sampleConfig); got != golden {
		t.Errorf("encoder output changed\n got: %q\nwant: %q", got, golden)
	}
	if got := Decode(golden); got != sampleConfig {
		t.Errorf("golden vector does not decode\n got: %q", got)
	}
}

// TestDecodeWithBOM covers a distributed file re-saved by Windows Notepad.
func TestDecodeWithBOM(t *testing.T) {
	t.Parallel()
	encoded := "\xEF\xBB\xBF" + Encode(sampleConfig) + "\r\n"
	if got := Decode(encoded); got != sampleConfig {
		t.Fatalf("Decode with BOM = %.40q, want the original config", got)
	}
}

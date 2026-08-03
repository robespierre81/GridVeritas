package main

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	mrand "math/rand"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

// Demo-data seeder for GridVeritas. Authenticates as admin, registers realistic
// energy-infrastructure sources, and posts VALID Ed25519-signed attestations over
// a time window — plus deliberately seeded anomalies (a sequence gap and an
// invalid-signature spike) so a demo shows the detective controls working.
//
// Uses the same canonical message format as the edge agent, so signatures verify.
//
// Run against a running stack:
//   docker compose --profile seed run --rm seed
// or locally:
//   GRIDVERITAS_CORE_URL=http://localhost:18080 ADMIN_PASSWORD=admin-change-me \
//     go run ./cmd/seed

type sourceReq struct {
	Name      string `json:"name"`
	PublicKey string `json:"publicKey"`
}
type sourceResp struct {
	ID string `json:"id"`
}
type attReq struct {
	SourceID             string `json:"sourceId"`
	PayloadHash          string `json:"payloadHash"`
	TimestampEpochMillis int64  `json:"timestampEpochMillis"`
	SequenceNr           int64  `json:"sequenceNr"`
	Signature            string `json:"signature"`
}

var (
	coreURL  = env("GRIDVERITAS_CORE_URL", "http://localhost:18080")
	adminU   = env("ADMIN_USER", "admin")
	adminP   = env("ADMIN_PASSWORD", "admin-change-me")
	readings = envInt("SEED_READINGS", 24)     // valid readings per normal source
	stepMin  = envInt("SEED_INTERVAL_MIN", 60) // minutes between readings
	token    string
	client   = &http.Client{Timeout: 20 * time.Second}
)

func main() {
	mrand.Seed(42)
	login()

	fmt.Println("Seeding demo data into", coreURL)

	// --- Normal sources: fully valid time series ---
	normal := []string{
		"Substation North / Feeder A",
		"Substation North / Feeder B",
		"Substation South / Transformer 1",
		"Wind Park East / Inverter 3",
	}
	for _, name := range normal {
		id, priv := register(name)
		seedSeries(id, priv, name, readings, nil, false)
		fmt.Printf("  %-38s %d readings (all valid)  id=%s\n", name, readings, short(id))
	}

	// --- Gap source: missing sequence numbers (SEQUENCE_GAP) ---
	{
		name := "Substation West / Feeder C (dropouts)"
		id, priv := register(name)
		skip := map[int64]bool{8: true, 9: true, 10: true} // hole in the sequence
		seedSeries(id, priv, name, readings, skip, false)
		fmt.Printf("  %-38s %d readings, seq 8-10 dropped (gap)  id=%s\n", name, readings-3, short(id))
	}

	// --- Faulty source: mostly invalid signatures (SIGNATURE_INVALID_SPIKE) ---
	{
		name := "Rooftop PV / Meter 7 (faulty sensor)"
		id, priv := register(name)
		n := 10
		seedSeries(id, priv, name, n, nil, true) // corrupt ~70% of signatures
		fmt.Printf("  %-38s %d readings, ~70%% invalid signatures  id=%s\n", name, n, short(id))
	}

	fmt.Println("\nDone. Notes:")
	fmt.Println("  * Merkle sealing + external anchoring run on a schedule; wait ~1-2 min,")
	fmt.Println("    then fetch a proof to see anchored=true.")
	fmt.Println("  * Anomalies appear after the detector runs (see the Anomalies view / GET /anomalies).")
	fmt.Println("  * Run again to add more sources (each run creates fresh sources).")
}

// seedSeries posts `count` readings for a source. If skip[seq] is true that seq is
// omitted (creating a gap). If corrupt is true, ~70% of signatures are invalidated.
func seedSeries(sourceID string, priv ed25519.PrivateKey, name string, count int, skip map[int64]bool, corrupt bool) {
	now := time.Now().UTC()
	base := 40.0 + mrand.Float64()*80.0 // per-source baseline kW
	for seq := int64(1); seq <= int64(count); seq++ {
		if skip[seq] {
			continue
		}
		// spread readings back in time: newest near now
		ts := now.Add(-time.Duration(int64(count)-seq) * time.Duration(stepMin) * time.Minute)
		value := base + 10*mrand.NormFloat64() + 5*float64(seq%5)
		payload := fmt.Sprintf(`{"device":%q,"metric":"active_power_kw","value":%.2f,"unit":"kW","ts":%q}`,
			name, value, ts.Format(time.RFC3339))
		sum := sha256.Sum256([]byte(payload))
		tsMillis := ts.UnixMilli()

		msg := canonicalAttestation(sourceID, seq, tsMillis, sum[:])
		sig := ed25519.Sign(priv, msg)
		if corrupt && mrand.Float64() < 0.7 {
			sig[0] ^= 0xFF // break the signature -> stored as signatureValid=false
		}

		postAttestation(attReq{
			SourceID:             sourceID,
			PayloadHash:          hex.EncodeToString(sum[:]),
			TimestampEpochMillis: tsMillis,
			SequenceNr:           seq,
			Signature:            base64.StdEncoding.EncodeToString(sig),
		})
	}
}

func canonicalAttestation(sourceID string, sequence, tsEpochMillis int64, payloadHash []byte) []byte {
	var buf bytes.Buffer
	writeField := func(b []byte) {
		var l [4]byte
		binary.BigEndian.PutUint32(l[:], uint32(len(b)))
		buf.Write(l[:])
		buf.Write(b)
	}
	u64 := func(v int64) []byte {
		var b [8]byte
		binary.BigEndian.PutUint64(b[:], uint64(v))
		return b[:]
	}
	writeField([]byte("GridVeritas-Attestation-v1"))
	writeField([]byte(strings.ToLower(sourceID)))
	writeField(u64(sequence))
	writeField(u64(tsEpochMillis))
	writeField(payloadHash)
	return buf.Bytes()
}

func login() {
	body, _ := json.Marshal(map[string]string{"username": adminU, "password": adminP})
	resp, err := client.Post(coreURL+"/api/v1/auth/token", "application/json", bytes.NewReader(body))
	if err != nil {
		log.Fatalf("login: %v", err)
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		log.Fatalf("login failed (%d): %s", resp.StatusCode, string(data))
	}
	var tr struct {
		Token string `json:"token"`
	}
	json.Unmarshal(data, &tr)
	token = tr.Token
}

func register(name string) (string, ed25519.PrivateKey) {
	pub, priv, _ := ed25519.GenerateKey(rand.Reader)
	body, _ := json.Marshal(sourceReq{Name: name, PublicKey: base64.StdEncoding.EncodeToString(pub)})
	data := doPost("/api/v1/sources", body)
	var sr sourceResp
	if err := json.Unmarshal(data, &sr); err != nil || sr.ID == "" {
		log.Fatalf("register %q: bad response: %s", name, string(data))
	}
	return sr.ID, priv
}

func postAttestation(a attReq) {
	body, _ := json.Marshal(a)
	doPost("/api/v1/attestations", body)
}

func doPost(path string, body []byte) []byte {
	req, _ := http.NewRequest("POST", coreURL+path, bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := client.Do(req)
	if err != nil {
		log.Fatalf("POST %s: %v", path, err)
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 300 {
		log.Fatalf("POST %s -> %d: %s", path, resp.StatusCode, string(data))
	}
	return data
}

func short(id string) string {
	if len(id) > 8 {
		return id[:8] + "…"
	}
	return id
}
func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}
func envInt(k string, d int) int {
	if v := os.Getenv(k); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return d
}

package main

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

// Go edge agent (M5 + M8 auth):
// - Ed25519 key + canonical-message signing (payload + metadata)
// - Persisted per-source sequence (no reuse across restarts)
// - Authenticates to the core via JWT (obtained from /api/v1/auth/token) and
//   sends "Authorization: Bearer" on all calls; refreshes the token on a 401.

type sourceRequest struct {
	Name      string `json:"name"`
	PublicKey string `json:"publicKey"`
}

type sourceResponse struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type attestationRequest struct {
	SourceID             string `json:"sourceId"`
	PayloadHash          string `json:"payloadHash"`
	TimestampEpochMillis int64  `json:"timestampEpochMillis"`
	SequenceNr           int64  `json:"sequenceNr"`
	Signature            string `json:"signature"`
}

type tokenRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type tokenResponse struct {
	Token string `json:"token"`
	Role  string `json:"role"`
}

// coreClient carries the base URL and a bearer token, refreshing it on 401.
type coreClient struct {
	base  string
	user  string
	pass  string
	token string
	http  *http.Client
}

func (c *coreClient) login() error {
	body, _ := json.Marshal(tokenRequest{Username: c.user, Password: c.pass})
	resp, err := c.http.Post(c.base+"/api/v1/auth/token", "application/json", bytes.NewReader(body))
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		return fmt.Errorf("auth failed: status %d: %s", resp.StatusCode, string(data))
	}
	var tr tokenResponse
	if err := json.Unmarshal(data, &tr); err != nil {
		return err
	}
	c.token = tr.Token
	log.Printf("Obtained %s token", tr.Role)
	return nil
}

// post sends a JSON POST with the bearer token; on 401 it refreshes once and retries.
func (c *coreClient) post(path string, payload []byte) (int, []byte, error) {
	do := func() (*http.Response, error) {
		req, err := http.NewRequest("POST", c.base+path, bytes.NewReader(payload))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "application/json")
		if c.token != "" {
			req.Header.Set("Authorization", "Bearer "+c.token)
		}
		return c.http.Do(req)
	}

	resp, err := do()
	if err != nil {
		return 0, nil, err
	}
	if resp.StatusCode == http.StatusUnauthorized {
		resp.Body.Close()
		if err := c.login(); err != nil {
			return 401, nil, err
		}
		resp, err = do()
		if err != nil {
			return 0, nil, err
		}
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	return resp.StatusCode, data, nil
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

// buildHTTPClient returns an HTTP client with mTLS when the TLS env vars are set:
// GRIDVERITAS_TLS_CLIENT_CERT, GRIDVERITAS_TLS_CLIENT_KEY, GRIDVERITAS_TLS_CA.
func buildHTTPClient() *http.Client {
	client := &http.Client{Timeout: 15 * time.Second}
	certFile := os.Getenv("GRIDVERITAS_TLS_CLIENT_CERT")
	keyFile := os.Getenv("GRIDVERITAS_TLS_CLIENT_KEY")
	caFile := os.Getenv("GRIDVERITAS_TLS_CA")
	if certFile == "" || keyFile == "" {
		return client
	}
	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		log.Fatalf("tls client cert: %v", err)
	}
	tlsCfg := &tls.Config{Certificates: []tls.Certificate{cert}}
	if caFile != "" {
		caPEM, err := os.ReadFile(caFile)
		if err != nil {
			log.Fatalf("tls ca: %v", err)
		}
		pool := x509.NewCertPool()
		if !pool.AppendCertsFromPEM(caPEM) {
			log.Fatalf("tls ca: invalid PEM in %s", caFile)
		}
		tlsCfg.RootCAs = pool
	}
	client.Transport = &http.Transport{TLSClientConfig: tlsCfg}
	log.Printf("mTLS enabled (client cert %s)", certFile)
	return client
}

func main() {
	coreURL := env("GRIDVERITAS_CORE_URL", "http://localhost:18080")
	agentName := env("GRIDVERITAS_AGENT_NAME", "edge-agent-01")
	interval := envDuration("GRIDVERITAS_INTERVAL", 15*time.Second)
	seqFile := env("GRIDVERITAS_SEQ_FILE", "agent.seq")

	// Auth: in dev use admin creds (can register + ingest); in prod use ingest
	// creds plus a pre-provisioned GRIDVERITAS_SOURCE_ID.
	client := &coreClient{
		base: coreURL,
		user: env("GRIDVERITAS_AUTH_USER", "admin"),
		pass: env("GRIDVERITAS_AUTH_PASSWORD", "admin-change-me"),
		http: buildHTTPClient(),
	}
	if err := client.login(); err != nil {
		log.Fatalf("login: %v", err)
	}

	pub, priv, err := loadOrCreateKeys()
	if err != nil {
		log.Fatalf("keys: %v", err)
	}
	pubB64 := base64.StdEncoding.EncodeToString(pub)
	log.Printf("Agent public key (base64): %s", pubB64)

	sourceID := os.Getenv("GRIDVERITAS_SOURCE_ID")
	if sourceID == "" {
		sourceID, err = registerSource(client, agentName, pubB64)
		if err != nil {
			log.Fatalf("register source (needs ADMIN role): %v", err)
		}
		log.Printf("Registered source id=%s", sourceID)
	} else {
		log.Printf("Using existing source id=%s", sourceID)
	}

	seq := loadSeq(seqFile) + 1
	log.Printf("Starting at sequence=%d", seq)

	for {
		if err := sendSampleAttestation(client, sourceID, priv, seq); err != nil {
			log.Printf("attestation seq=%d error: %v", seq, err)
		} else {
			log.Printf("attestation seq=%d sent OK", seq)
		}
		if e := saveSeq(seqFile, seq); e != nil {
			log.Printf("warning: could not persist sequence: %v", e)
		}
		seq++
		time.Sleep(interval)
	}
}

func loadOrCreateKeys() (ed25519.PublicKey, ed25519.PrivateKey, error) {
	privPath := env("GRIDVERITAS_KEY_FILE", "agent.ed25519")
	if data, err := os.ReadFile(privPath); err == nil && len(data) == ed25519.PrivateKeySize {
		priv := ed25519.PrivateKey(data)
		return priv.Public().(ed25519.PublicKey), priv, nil
	}
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, nil, err
	}
	_ = os.WriteFile(privPath, priv, 0600)
	log.Printf("Generated new key pair → %s", privPath)
	return pub, priv, nil
}

func loadSeq(path string) int64 {
	data, err := os.ReadFile(path)
	if err != nil {
		return 0
	}
	v, err := strconv.ParseInt(strings.TrimSpace(string(data)), 10, 64)
	if err != nil {
		return 0
	}
	return v
}

func saveSeq(path string, seq int64) error {
	return os.WriteFile(path, []byte(strconv.FormatInt(seq, 10)), 0600)
}

func registerSource(c *coreClient, name, pubB64 string) (string, error) {
	body, _ := json.Marshal(sourceRequest{Name: name, PublicKey: pubB64})
	status, data, err := c.post("/api/v1/sources", body)
	if err != nil {
		return "", err
	}
	if status >= 300 {
		return "", fmt.Errorf("status %d: %s", status, string(data))
	}
	var sr sourceResponse
	if err := json.Unmarshal(data, &sr); err != nil {
		return "", err
	}
	return sr.ID, nil
}

func sendSampleAttestation(c *coreClient, sourceID string, priv ed25519.PrivateKey, seq int64) error {
	payload := fmt.Sprintf(`{"device":"meter-1","value":%.2f,"ts":"%s"}`,
		20.0+float64(seq%50)/10.0, time.Now().UTC().Format(time.RFC3339))
	sum := sha256.Sum256([]byte(payload))
	payloadHash := hex.EncodeToString(sum[:])
	tsMillis := time.Now().UTC().UnixMilli()

	msg := canonicalAttestation(sourceID, seq, tsMillis, sum[:])
	sig := ed25519.Sign(priv, msg)

	body, _ := json.Marshal(attestationRequest{
		SourceID:             sourceID,
		PayloadHash:          payloadHash,
		TimestampEpochMillis: tsMillis,
		SequenceNr:           seq,
		Signature:            base64.StdEncoding.EncodeToString(sig),
	})
	status, data, err := c.post("/api/v1/attestations", body)
	if err != nil {
		return err
	}
	if status == http.StatusConflict {
		return fmt.Errorf("duplicate sequence %d rejected (409) — advancing", seq)
	}
	if status >= 300 {
		return fmt.Errorf("status %d: %s", status, string(data))
	}
	return nil
}

func env(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func envDuration(k string, def time.Duration) time.Duration {
	if v := os.Getenv(k); v != "" {
		if d, err := time.ParseDuration(v); err == nil {
			return d
		}
	}
	return def
}

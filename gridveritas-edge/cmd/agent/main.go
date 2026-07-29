package main

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"time"
)

// Minimal Go edge agent (M5 starter):
// - Generates or loads an Ed25519 key pair
// - Registers as a source (or uses GRIDVERITAS_SOURCE_ID)
// - Periodically creates a sample payload, hashes it, signs it, POSTs attestation

type sourceRequest struct {
	Name      string `json:"name"`
	PublicKey string `json:"publicKey"`
}

type sourceResponse struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type attestationRequest struct {
	SourceID    string    `json:"sourceId"`
	PayloadHash string    `json:"payloadHash"`
	Timestamp   time.Time `json:"timestamp"`
	SequenceNr  int64     `json:"sequenceNr"`
	Signature   string    `json:"signature"`
}

func main() {
	coreURL := env("GRIDVERITAS_CORE_URL", "http://localhost:18080")
	agentName := env("GRIDVERITAS_AGENT_NAME", "edge-agent-01")
	interval := envDuration("GRIDVERITAS_INTERVAL", 15*time.Second)

	pub, priv, err := loadOrCreateKeys()
	if err != nil {
		log.Fatalf("keys: %v", err)
	}
	pubB64 := base64.StdEncoding.EncodeToString(pub)
	log.Printf("Agent public key (base64): %s", pubB64)

	sourceID := os.Getenv("GRIDVERITAS_SOURCE_ID")
	if sourceID == "" {
		sourceID, err = registerSource(coreURL, agentName, pubB64)
		if err != nil {
			log.Fatalf("register source: %v", err)
		}
		log.Printf("Registered source id=%s", sourceID)
	} else {
		log.Printf("Using existing source id=%s", sourceID)
	}

	var seq int64 = 1
	for {
		if err := sendSampleAttestation(coreURL, sourceID, priv, seq); err != nil {
			log.Printf("attestation error: %v", err)
		} else {
			log.Printf("attestation seq=%d sent OK", seq)
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

func registerSource(coreURL, name, pubB64 string) (string, error) {
	body, _ := json.Marshal(sourceRequest{Name: name, PublicKey: pubB64})
	resp, err := http.Post(coreURL+"/api/v1/sources", "application/json", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 300 {
		return "", fmt.Errorf("status %d: %s", resp.StatusCode, string(data))
	}
	var sr sourceResponse
	if err := json.Unmarshal(data, &sr); err != nil {
		return "", err
	}
	return sr.ID, nil
}

func sendSampleAttestation(coreURL, sourceID string, priv ed25519.PrivateKey, seq int64) error {
	// Sample payload (e.g. simulated meter reading)
	payload := fmt.Sprintf(`{"device":"meter-1","value":%.2f,"ts":"%s"}`,
		20.0+float64(seq%50)/10.0, time.Now().UTC().Format(time.RFC3339))
	sum := sha256.Sum256([]byte(payload))
	payloadHash := hex.EncodeToString(sum[:])

	// Sign: payloadHash bytes (hex-decoded) for deterministic verify later
	sig := ed25519.Sign(priv, sum[:])
	sigB64 := base64.StdEncoding.EncodeToString(sig)

	req := attestationRequest{
		SourceID:    sourceID,
		PayloadHash: payloadHash,
		Timestamp:   time.Now().UTC(),
		SequenceNr:  seq,
		Signature:   sigB64,
	}
	body, _ := json.Marshal(req)
	resp, err := http.Post(coreURL+"/api/v1/attestations", "application/json", bytes.NewReader(body))
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 300 {
		return fmt.Errorf("status %d: %s", resp.StatusCode, string(data))
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
		d, err := time.ParseDuration(v)
		if err == nil {
			return d
		}
	}
	return def
}

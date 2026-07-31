package com.gridveritas.core.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Merkle tree correctness and the tamper-detection property that underpins M6.
 * No network: this validates the local math the anchor commits to.
 */
class MerkleTreeTest {

    private static List<byte[]> leaves(int n) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(MerkleTree.leafHash(("attestation-" + i).getBytes(StandardCharsets.UTF_8)));
        }
        return out;
    }

    /** For every size and every index, the audit path must fold back to the root. */
    @Test
    void auditPathReconstructsRootForAllSizesAndIndices() {
        for (int n = 1; n <= 33; n++) {
            List<byte[]> ls = leaves(n);
            byte[] root = MerkleTree.merkleRoot(ls);
            for (int i = 0; i < n; i++) {
                List<MerkleTree.PathStep> path = MerkleTree.auditPath(ls, i);
                byte[] recomputed = MerkleTree.rootFromAuditPath(ls.get(i), path);
                assertArrayEquals(root, recomputed,
                        "path did not reconstruct root (n=" + n + ", i=" + i + ")");
            }
        }
    }

    /**
     * Tamper-detection demo (the essence of the M6 anchor guarantee):
     * once a root is fixed (and, in production, externally anchored), altering any
     * stored leaf changes the recomputed root, so it no longer matches the anchored
     * value and the old leaf's audit path no longer verifies.
     */
    @Test
    void tamperingChangesTheRoot() {
        List<byte[]> ls = leaves(8);
        byte[] anchoredRoot = MerkleTree.merkleRoot(ls); // imagine this is timestamped by the TSA

        // Operator tampers with leaf 3 (e.g. edits the stored attestation directly)
        List<byte[]> tampered = new ArrayList<>(ls);
        tampered.set(3, MerkleTree.leafHash("attestation-3-MODIFIED".getBytes(StandardCharsets.UTF_8)));

        byte[] rootAfterTamper = MerkleTree.merkleRoot(tampered);
        assertFalse(Arrays.equals(anchoredRoot, rootAfterTamper),
                "tampering must change the root");

        // The original audit path for leaf 3, applied to the tampered leaf, must not
        // reproduce the anchored root.
        List<MerkleTree.PathStep> pathForLeaf3 = MerkleTree.auditPath(ls, 3);
        byte[] recomputed = MerkleTree.rootFromAuditPath(tampered.get(3), pathForLeaf3);
        assertFalse(Arrays.equals(anchoredRoot, recomputed),
                "tampered leaf must not verify against the anchored root");

        // An untouched leaf still verifies against the anchored root.
        List<MerkleTree.PathStep> pathForLeaf0 = MerkleTree.auditPath(ls, 0);
        assertTrue(Arrays.equals(anchoredRoot, MerkleTree.rootFromAuditPath(ls.get(0), pathForLeaf0)),
                "untouched leaf must still verify");
    }
}

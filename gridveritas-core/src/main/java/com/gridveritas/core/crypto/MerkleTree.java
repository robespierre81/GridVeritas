package com.gridveritas.core.crypto;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 6962-style Merkle tree over pre-computed leaf hashes.
 *
 * Domain separation (prevents second-preimage / node-as-leaf confusion):
 *   leaf hash  = SHA-256(0x00 || data)
 *   node hash  = SHA-256(0x01 || left || right)
 *
 * The tree split uses "largest power of two strictly less than n", which avoids
 * the duplicate-last-node ambiguity (CVE-2012-2459).
 *
 * IMPORTANT: the {@code leaves} passed to {@link #merkleRoot} and {@link #auditPath}
 * are already leaf hashes (i.e. the output of {@link #leafHash}); these methods do
 * NOT re-apply the leaf hash. A single-leaf tree's root is that leaf hash itself.
 */
public final class MerkleTree {

    private MerkleTree() {
    }

    /** One audit-path element: a sibling hash and whether it sits to the right of the current node. */
    public record PathStep(byte[] hash, boolean siblingOnRight) {
    }

    /** leaf hash = SHA-256(0x00 || data). */
    public static byte[] leafHash(byte[] data) {
        MessageDigest md = sha256();
        md.update((byte) 0x00);
        md.update(data);
        return md.digest();
    }

    /** internal node hash = SHA-256(0x01 || left || right). */
    public static byte[] nodeHash(byte[] left, byte[] right) {
        MessageDigest md = sha256();
        md.update((byte) 0x01);
        md.update(left);
        md.update(right);
        return md.digest();
    }

    /**
     * Merkle Tree Hash (root) over an ordered list of leaf hashes.
     * @throws IllegalArgumentException if leaves is empty
     */
    public static byte[] merkleRoot(List<byte[]> leaves) {
        if (leaves == null || leaves.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute a Merkle root over zero leaves");
        }
        if (leaves.size() == 1) {
            return leaves.get(0);
        }
        int k = largestPowerOfTwoLessThan(leaves.size());
        byte[] left = merkleRoot(leaves.subList(0, k));
        byte[] right = merkleRoot(leaves.subList(k, leaves.size()));
        return nodeHash(left, right);
    }

    /**
     * Inclusion (audit) path for the leaf at {@code index}, ordered leaf → root.
     * Verify with {@link #rootFromAuditPath}.
     */
    public static List<PathStep> auditPath(List<byte[]> leaves, int index) {
        if (index < 0 || index >= leaves.size()) {
            throw new IllegalArgumentException("index out of range: " + index);
        }
        List<PathStep> path = new ArrayList<>();
        buildPath(leaves, index, path);
        return path;
    }

    private static void buildPath(List<byte[]> leaves, int index, List<PathStep> out) {
        int n = leaves.size();
        if (n == 1) {
            return;
        }
        int k = largestPowerOfTwoLessThan(n);
        if (index < k) {
            buildPath(leaves.subList(0, k), index, out);
            out.add(new PathStep(merkleRoot(leaves.subList(k, n)), true));   // sibling on the right
        } else {
            buildPath(leaves.subList(k, n), index - k, out);
            out.add(new PathStep(merkleRoot(leaves.subList(0, k)), false));  // sibling on the left
        }
    }

    /** Recompute the root from a leaf hash and its audit path (what an independent verifier does). */
    public static byte[] rootFromAuditPath(byte[] leafHash, List<PathStep> path) {
        byte[] current = leafHash;
        for (PathStep step : path) {
            current = step.siblingOnRight()
                    ? nodeHash(current, step.hash())
                    : nodeHash(step.hash(), current);
        }
        return current;
    }

    /** Largest power of two strictly less than n (n >= 2). e.g. 2->1, 3->2, 5->4, 8->4. */
    static int largestPowerOfTwoLessThan(int n) {
        int k = 1;
        while (k * 2 < n) {
            k *= 2;
        }
        return k;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

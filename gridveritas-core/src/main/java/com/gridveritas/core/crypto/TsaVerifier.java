package com.gridveritas.core.crypto;

import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TimeStampToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.cert.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies an RFC 3161 timestamp token:
 *  1. the message imprint equals the expected digest (the Merkle root),
 *  2. the token signature is valid against its embedded signer certificate,
 *  3. the signer certificate carries the id-kp-timeStamping extended key usage,
 *  4. (if a trust certificate is configured) the signer chains to that pinned CA.
 *
 * Without a configured trust cert, steps 1–3 still run (integrity), but the token
 * is not trust-pinned — a warning is logged at startup.
 */
@Component
public class TsaVerifier {

    private static final Logger log = LoggerFactory.getLogger(TsaVerifier.class);

    public record Result(boolean valid, boolean trusted, Instant genTime, String detail) {
    }

    private final Set<TrustAnchor> trustAnchors;
    private final boolean trustPinned;

    public TsaVerifier(@Value("${gridveritas.anchor.tsa-trust-cert:}") String trustCertLocation) {
        this.trustAnchors = loadTrustAnchors(trustCertLocation);
        this.trustPinned = !trustAnchors.isEmpty();
        if (!trustPinned) {
            log.warn("No TSA trust certificate configured (gridveritas.anchor.tsa-trust-cert). "
                    + "Anchor tokens will be integrity-verified but NOT trust-pinned against a CA.");
        } else {
            log.info("TSA trust-pinning enabled with {} trust anchor(s).", trustAnchors.size());
        }
    }

    public Result verify(byte[] tokenDer, byte[] expectedDigest) {
        try {
            TimeStampToken token = new TimeStampToken(new CMSSignedData(tokenDer));

            if (!Arrays.equals(token.getTimeStampInfo().getMessageImprintDigest(), expectedDigest)) {
                return new Result(false, false, null, "message imprint does not match the root hash");
            }

            X509CertificateHolder signer = signerCert(token);
            if (signer == null) {
                return new Result(false, false, null, "no signer certificate present in token");
            }

            token.validate(new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(signer));

            if (!hasTimeStampingEku(signer)) {
                return new Result(false, false, genTime(token),
                        "signer certificate lacks the id-kp-timeStamping extended key usage");
            }

            boolean trusted = false;
            if (trustPinned) {
                try {
                    validatePath(token, signer);
                    trusted = true;
                } catch (Exception e) {
                    return new Result(true, false, genTime(token),
                            "signature valid but not trusted: " + e.getMessage());
                }
            }
            return new Result(true, trusted, genTime(token),
                    trustPinned ? "verified and trust-pinned" : "signature verified (trust not pinned)");
        } catch (Exception e) {
            return new Result(false, false, null, "verification error: " + e.getMessage());
        }
    }

    private Instant genTime(TimeStampToken token) {
        return token.getTimeStampInfo().getGenTime().toInstant();
    }

    private X509CertificateHolder signerCert(TimeStampToken token) {
        Collection<?> matches = token.getCertificates().getMatches(token.getSID());
        return matches.isEmpty() ? null : (X509CertificateHolder) matches.iterator().next();
    }

    private boolean hasTimeStampingEku(X509CertificateHolder holder) {
        Extension eku = holder.getExtension(Extension.extendedKeyUsage);
        if (eku == null) {
            return false;
        }
        ExtendedKeyUsage usage = ExtendedKeyUsage.getInstance(eku.getParsedValue());
        return usage != null && usage.hasKeyPurposeId(KeyPurposeId.id_kp_timeStamping);
    }

    private void validatePath(TimeStampToken token, X509CertificateHolder signerHolder) throws Exception {
        JcaX509CertificateConverter conv = new JcaX509CertificateConverter().setProvider("BC");

        List<X509Certificate> certs = new ArrayList<>();
        for (Object o : token.getCertificates().getMatches(null)) {
            certs.add(conv.getCertificate((X509CertificateHolder) o));
        }
        X509Certificate signer = conv.getCertificate(signerHolder);

        X509CertSelector target = new X509CertSelector();
        target.setCertificate(signer);

        PKIXBuilderParameters params = new PKIXBuilderParameters(trustAnchors, target);
        params.setRevocationEnabled(false); // MVP: no CRL/OCSP; revisit for pilot
        params.addCertStore(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(certs)));

        CertPathBuilder.getInstance("PKIX").build(params); // throws if no path to a trust anchor
    }

    private Set<TrustAnchor> loadTrustAnchors(String location) {
        Set<TrustAnchor> anchors = new HashSet<>();
        if (location == null || location.isBlank()) {
            return anchors;
        }
        try {
            Resource resource = new DefaultResourceLoader().getResource(location);
            try (InputStream in = resource.getInputStream()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                for (Certificate c : cf.generateCertificates(in)) {
                    anchors.add(new TrustAnchor((X509Certificate) c, null));
                }
            }
        } catch (Exception e) {
            log.error("Failed to load TSA trust certificate from '{}': {}", location, e.getMessage());
        }
        return anchors;
    }
}

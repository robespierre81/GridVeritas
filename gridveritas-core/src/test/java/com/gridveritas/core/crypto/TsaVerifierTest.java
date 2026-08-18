package com.gridveritas.core.crypto;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * TsaVerifier is the RFC 3161 half of the tamper-detection guarantee (Architecture
 * Baseline 3.6): a Merkle root is only as tamper-evident as the timestamp anchored
 * to it. These tests mint real timestamp tokens with BouncyCastle (mirroring what
 * a real TSA sends back, per TsaClient) so each of TsaVerifier's checks - message
 * imprint, signature, and trust-path - is exercised against genuine ASN.1/CMS
 * structures rather than mocks.
 *
 * TsaVerifier.hasTimeStampingEku() (rejects a signer cert whose EKU isn't
 * id-kp-timeStamping) has no direct test here: BouncyCastle's own RFC 3161 stack
 * enforces that same constraint - and other conformance rules, like a mandatory
 * ESS signing-certificate attribute - at both token generation and token parse
 * time, so producing a token that reaches TsaVerifier with a non-conformant EKU
 * would mean reimplementing large parts of TimeStampTokenGenerator's internals
 * rather than using BC's public API. The check remains a legitimate
 * defense-in-depth line against a non-BC or malicious TSA implementation.
 */
class TsaVerifierTest {

    private static final byte[] EXPECTED_DIGEST = sha256ish("merkle-root-1");
    private static final byte[] OTHER_DIGEST = sha256ish("merkle-root-2");
    private static final ASN1ObjectIdentifier TEST_POLICY_OID = new ASN1ObjectIdentifier("1.2.3.4.5.6");

    @BeforeAll
    static void registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static byte[] sha256ish(String s) {
        byte[] out = new byte[32];
        byte[] src = s.getBytes();
        for (int i = 0; i < out.length; i++) {
            out[i] = src[i % src.length];
        }
        return out;
    }

    private record Signer(X509Certificate cert, PrivateKey key) {
    }

    private static Signer generateSigner() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();

        X500Name subject = new X500Name("CN=Test TSA, O=GridVeritas Test");
        Date notBefore = new Date(System.currentTimeMillis() - 3_600_000);
        Date notAfter = new Date(System.currentTimeMillis() + 3_600_000);
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(System.nanoTime()), notBefore, notAfter, subject, pair.getPublic());
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        certBuilder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(pair.getPrivate());
        X509CertificateHolder holder = certBuilder.build(contentSigner);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
        return new Signer(cert, pair.getPrivate());
    }

    /** Mints a real TimeStampToken over {@code digest}, signed by {@code signer} - the same shape TsaClient stores. */
    private static TimeStampToken mintToken(Signer signer, byte[] digest, Date genTime) throws Exception {
        TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
        reqGen.setCertReq(true);
        BigInteger nonce = new BigInteger(64, new SecureRandom());
        TimeStampRequest request = reqGen.generate(TSPAlgorithms.SHA256, digest, nonce);

        DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        DigestCalculator digestCalculator = digestProvider.get(new AlgorithmIdentifier(CMSAlgorithm.SHA256));
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC").build(signer.key());
        SignerInfoGenerator signerInfoGen = new JcaSignerInfoGeneratorBuilder(digestProvider)
                .build(contentSigner, signer.cert());

        TimeStampTokenGenerator tokenGen = new TimeStampTokenGenerator(signerInfoGen, digestCalculator, TEST_POLICY_OID);
        tokenGen.addCertificates(new JcaCertStore(List.of(signer.cert())));

        return tokenGen.generate(request, BigInteger.ONE, genTime);
    }

    private TsaVerifier verifierWithoutTrustPinning() {
        return new TsaVerifier("");
    }

    private TsaVerifier verifierTrustPinnedTo(X509Certificate trustCert, Path tempDir) throws Exception {
        Path pemFile = tempDir.resolve("trust-" + System.nanoTime() + ".pem");
        try (FileOutputStream out = new FileOutputStream(pemFile.toFile())) {
            out.write("-----BEGIN CERTIFICATE-----\n".getBytes());
            out.write(java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encode(trustCert.getEncoded()));
            out.write("\n-----END CERTIFICATE-----\n".getBytes());
        }
        return new TsaVerifier("file:" + pemFile.toAbsolutePath());
    }

    @Test
    void validTokenVerifiesWithoutTrustPinningWhenNoTrustCertIsConfigured() throws Exception {
        Signer signer = generateSigner();
        Date genTime = new Date();
        TimeStampToken token = mintToken(signer, EXPECTED_DIGEST, genTime);

        TsaVerifier.Result result = verifierWithoutTrustPinning().verify(token.getEncoded(), EXPECTED_DIGEST);

        assertThat(result.valid()).isTrue();
        assertThat(result.trusted()).isFalse();
        assertThat(result.detail()).contains("trust not pinned");
        assertThat(result.genTime()).isCloseTo(genTime.toInstant(), within(2, ChronoUnit.SECONDS));
    }

    @Test
    void messageImprintMismatchFailsVerification() throws Exception {
        Signer signer = generateSigner();
        // Token is minted over OTHER_DIGEST but checked against EXPECTED_DIGEST -
        // simulates an anchor token being matched against the wrong Merkle root.
        TimeStampToken token = mintToken(signer, OTHER_DIGEST, new Date());

        TsaVerifier.Result result = verifierWithoutTrustPinning().verify(token.getEncoded(), EXPECTED_DIGEST);

        assertThat(result.valid()).isFalse();
        assertThat(result.detail()).contains("message imprint does not match");
    }

    @Test
    void tamperedTokenBytesFailVerification() throws Exception {
        Signer signer = generateSigner();
        TimeStampToken token = mintToken(signer, EXPECTED_DIGEST, new Date());
        byte[] tokenDer = token.getEncoded();
        // Flip a byte roughly in the middle of the signed CMS structure.
        tokenDer[tokenDer.length / 2] ^= 0x01;

        TsaVerifier.Result result = verifierWithoutTrustPinning().verify(tokenDer, EXPECTED_DIGEST);

        assertThat(result.valid()).isFalse();
    }

    @Test
    void malformedTokenBytesFailClosedInsteadOfThrowing() {
        TsaVerifier.Result result = verifierWithoutTrustPinning()
                .verify("not a real CMS token".getBytes(), EXPECTED_DIGEST);

        assertThat(result.valid()).isFalse();
        assertThat(result.trusted()).isFalse();
        assertThat(result.detail()).contains("verification error");
    }

    @Test
    void trustPinnedVerificationSucceedsWhenSignerIsTheConfiguredTrustAnchor(@TempDir Path tempDir) throws Exception {
        Signer signer = generateSigner();
        TimeStampToken token = mintToken(signer, EXPECTED_DIGEST, new Date());

        TsaVerifier verifier = verifierTrustPinnedTo(signer.cert(), tempDir);
        TsaVerifier.Result result = verifier.verify(token.getEncoded(), EXPECTED_DIGEST);

        assertThat(result.valid()).isTrue();
        assertThat(result.trusted()).isTrue();
        assertThat(result.detail()).contains("trust-pinned");
    }

    @Test
    void trustPinnedVerificationFailsClosedWhenSignerDoesNotChainToConfiguredAnchor(@TempDir Path tempDir)
            throws Exception {
        Signer signer = generateSigner();
        Signer unrelatedTrustedCa = generateSigner();
        TimeStampToken token = mintToken(signer, EXPECTED_DIGEST, new Date());

        // Trust is pinned to a CA the token's signer was never issued by - the
        // signature itself is still valid, but the chain of custody isn't.
        TsaVerifier verifier = verifierTrustPinnedTo(unrelatedTrustedCa.cert(), tempDir);
        TsaVerifier.Result result = verifier.verify(token.getEncoded(), EXPECTED_DIGEST);

        assertThat(result.valid()).isTrue();
        assertThat(result.trusted()).isFalse();
        assertThat(result.detail()).contains("not trusted");
    }
}

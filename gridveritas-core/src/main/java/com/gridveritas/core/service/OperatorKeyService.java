package com.gridveritas.core.service;

import com.gridveritas.core.crypto.Ed25519Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.UUID;

/**
 * Persistent operator Ed25519 identity shared by every core replica (M13).
 * Private key, public key, and operator UUID live in the same directory so a
 * scaled replica signs as the same operator. Generated on first start.
 */
@Service
public class OperatorKeyService {

    private static final Logger log = LoggerFactory.getLogger(OperatorKeyService.class);

    private final UUID operatorId;
    private final PrivateKey privateKey;
    private final String publicKeyBase64;

    public OperatorKeyService(
            @Value("${gridveritas.federation.operator-dir:${java.io.tmpdir}/gridveritas-operator}")
            String operatorDir) {
        Path dir = Path.of(operatorDir);
        Path privFile = dir.resolve("operator.ed25519");
        Path pubFile = dir.resolve("operator.ed25519.pub");
        Path idFile = dir.resolve("operator-id.txt");
        try {
            Files.createDirectories(dir);
            if (Files.exists(privFile) && Files.size(privFile) > 0
                    && Files.exists(pubFile) && Files.size(pubFile) > 0) {
                this.privateKey = Ed25519Keys.decodePrivate(Files.readAllBytes(privFile));
                this.publicKeyBase64 = Files.readString(pubFile, StandardCharsets.US_ASCII).trim();
                log.info("Loaded federation operator key from {}", privFile);
            } else {
                KeyPair pair = Ed25519Keys.generate();
                this.privateKey = pair.getPrivate();
                this.publicKeyBase64 = Ed25519Keys.publicKeyBase64(pair.getPublic());
                Files.write(privFile, Ed25519Keys.encodePrivate(pair.getPrivate()));
                Files.writeString(pubFile, this.publicKeyBase64, StandardCharsets.US_ASCII);
                restrict(privFile);
                log.info("Generated new federation operator key at {}", privFile);
            }

            if (Files.exists(idFile) && Files.size(idFile) > 0) {
                this.operatorId = UUID.fromString(Files.readString(idFile).trim());
            } else {
                this.operatorId = UUID.randomUUID();
                Files.writeString(idFile, this.operatorId.toString());
            }
            log.info("Federation operator id {}", this.operatorId);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialise federation operator key in " + dir, e);
        }
    }

    private static void restrict(Path file) {
        try {
            file.toFile().setReadable(false, false);
            file.toFile().setWritable(false, false);
            file.toFile().setReadable(true, true);
            file.toFile().setWritable(true, true);
        } catch (Exception ignored) {
            // volume umask may still be wider; not fatal
        }
    }

    public UUID operatorId() {
        return operatorId;
    }

    public String publicKeyBase64() {
        return publicKeyBase64;
    }

    public String sign(byte[] message) {
        return Ed25519Keys.sign(privateKey, message);
    }
}

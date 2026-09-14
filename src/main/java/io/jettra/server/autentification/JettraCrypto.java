package io.jettra.server.autentification;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Utility class for two-way symmetric encryption and decryption.
 * Designed for scenarios where passwords or tokens need to be recoverable.
 */
public class JettraCrypto {

    private static final String ALGORITHM = "AES";
    private static SecretKeySpec secretKey;

//    static {
//        prepareSecreteKey();
//    }

    private static void prepareSecreteKey(String mySecretKey) {
        try {
            String myKey = mySecretKey;
            if (myKey == null || myKey.isBlank()) {
                myKey = System.getProperty("server.crypto.secret", System.getenv().getOrDefault("SERVER_CRYPTO_SECRET", "JettraDef@ultS3cr3tK3y2026!"));
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // use only first 128 bit (16 bytes) for AES
            secretKey = new SecretKeySpec(key, ALGORITHM);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypts a plaintext string using AES.
     *
     * @param strToEncrypt The plaintext string to encrypt
     * @return The Base64 encoded encrypted string, or null if an error occurs
     */
    public static String encrypt(final String strToEncrypt, String mySecretKey) {
        try {
            prepareSecreteKey(mySecretKey);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            System.err.println("Error while encrypting: " + e.toString());
        }
        return null;
    }

    /**
     * Decrypts an AES encrypted Base64 string.
     *
     * @param strToDecrypt The encrypted Base64 string to decrypt
     * @return The decrypted plaintext string, or null if an error occurs
     */
    public static String decrypt(final String strToDecrypt, String mySecretKey) {
        try {
            prepareSecreteKey(mySecretKey);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error while decrypting: " + e.toString());
        }
        return null;
    }
}

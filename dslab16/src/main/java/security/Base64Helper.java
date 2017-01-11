package security;

import org.bouncycastle.util.encoders.Base64;

/**
 * Helper for Base64 encoding and decoding
 */
public class Base64Helper {

    /**
     * Encodes Plaintext to Base64
     * @param plaintext the plain text
     * @return the encoded text
     */
    public static byte[] encodeBase64(byte[] plaintext){
        return Base64.encode(plaintext);
    }

    /**
     * Decodes Base64 encrypted text to plain text
     * @param encodedtext the encrypted text
     * @return the plain text
     */
    public static byte[] decodeBase64(byte[] encodedtext){
        return Base64.decode(encodedtext);
    }
}

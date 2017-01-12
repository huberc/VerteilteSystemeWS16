package security;

import javax.crypto.*;
import java.security.*;

public class RSA {

    private Cipher cipherEncrypt = null;
    private Cipher cipherDecrypt = null;

    public RSA(Key key) throws RSAException{

        try {
            cipherEncrypt= Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding","BC");
            cipherDecrypt= Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding","BC");

            cipherEncrypt.init(Cipher.ENCRYPT_MODE, key);
            cipherDecrypt.init(Cipher.DECRYPT_MODE, key);

        } catch (InvalidKeyException ex) {
            throw new RSAException("InvalidKeyException: "+ex.getMessage());
        } catch (NoSuchAlgorithmException ex) {
            throw new RSAException("NoSuchAlgorithmException: "+ex.getMessage());
        } catch (NoSuchProviderException ex) {
            throw new RSAException("NoSuchProviderException: "+ex.getMessage());
        } catch (NoSuchPaddingException ex) {
            throw new RSAException("NoSuchPaddingException: "+ex.getMessage());
        }
    }

    /**
     * Encrypts the given data
     * @param data the date which should be encrypted
     * @return the encrypted data
     */
    public byte[] encrypt(byte[] data) throws RSAException{
        byte[] result = null;
        try{
            result = cipherEncrypt.doFinal(data);
        }catch (IllegalBlockSizeException ex) {
            throw new RSAException("IllegalBlockSizeException: "+ex.getMessage());
        } catch (BadPaddingException ex) {
            throw new RSAException("BadPaddingException: "+ex.getMessage());
        }

        return result;
    }

    /**
     * Decrypts the given data
     * @param data the date which should be decrypted
     * @return the decrypted data
     */
    public byte[] decrypt(byte[] data) throws RSAException{
        try{

            byte[] basebata = new byte[1024];

            data = cipherDecrypt.doFinal(data);
        }catch (IllegalBlockSizeException ex) {
            throw new RSAException("IllegalBlockSizeException: "+ex.getMessage());
        } catch (BadPaddingException ex) {
            throw new RSAException("BadPaddingException: "+ex.getMessage());
        }

        return data;
    }



}

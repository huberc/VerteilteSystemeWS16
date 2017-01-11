package security;

import org.bouncycastle.openssl.PEMReader;

import java.io.FileReader;
import java.io.IOException;
import java.security.PublicKey;

/**
 * Created by Yannick on Mittwoch11.01.17.
 */
public class KeyManager {

    public static PublicKey getPublicKey(String file)throws RSAException
    {
        PEMReader in = null;
        PublicKey key=null;
        try {

            in = new PEMReader(new FileReader(file));
            key = (PublicKey) in.readObject();

        } catch (IOException ex) {
            throw new RSAException("getPublicKeyfromUser:IOException: "+ex.getMessage());
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                throw new RSAException("getPublicKeyfromUser:cannot close file reader: "+ ex.getMessage());
            }
        }

        return key;
    }

}

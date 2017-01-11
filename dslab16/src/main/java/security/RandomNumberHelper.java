package security;

import java.security.SecureRandom;

public class RandomNumberHelper {

    /**
     * Calculates a random 32bit number
     * @return the random number
     */
    public static byte[] getRandomNumber(){
        SecureRandom secureRandom = new SecureRandom();
        final byte[] number = new byte[32];
        secureRandom.nextBytes(number);
        return number;
    }
}

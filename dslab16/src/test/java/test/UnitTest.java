package test;

import org.junit.Test;
import util.Base64Helper;
import static org.junit.Assert.assertEquals;


public class UnitTest {

    @Test
    public void testBase64(){
        String word = "Hallo!12345";
        byte[] wordBytes = word.getBytes();

        byte[] encoded = Base64Helper.encodeBase64(wordBytes);
        byte[] decoded = Base64Helper.decodeBase64(encoded);

        String result = new String(decoded);

        assertEquals(word,result);
    }

}

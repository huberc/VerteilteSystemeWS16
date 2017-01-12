package chatserver;

import channel.TcpChannel;
import model.User;
import org.bouncycastle.util.encoders.Base64;

import security.AuthenticationException;
import util.Config;
import util.Keys;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

public class AuthenticateHelper {

    private Chatserver chatserver;
    private Config config;
    private Usermanager usermanager;
    private byte serverChallenge[] = new byte[32];
    private SecretKey secretKey;
    private IvParameterSpec ivParameterSpec;
    private TcpChannel tcpChannel;

    public AuthenticateHelper(Chatserver chatserver, Usermanager usermanager, TcpChannel tcpChannel) {
        this.chatserver = chatserver;
        config = new Config("chatserver");
        this.usermanager = usermanager;
        this.tcpChannel = tcpChannel;
    }

    public String handleMessage(byte[] message, Socket clientsocket) throws AuthenticationException {
        User user = this.usermanager.getUserBySocket(clientsocket);

        if(user == null || user.getAuthState() == 0) {
            try {
                //Decode message
                //byte[] messageDecoded = Base64Helper.decodeBase64(message.getBytes());
                byte[] messageDecoded = message;

                // Get Server public Key
                String finalPath = config.getString("key");
                RSAPrivateKey serverPrivateKey = (RSAPrivateKey) Keys.readPrivatePEM(new File(finalPath));

                Cipher cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");

                cipher.init(Cipher.DECRYPT_MODE, serverPrivateKey);
                byte[] messageDecrypted = cipher.doFinal(messageDecoded);


                String[] message1 = new String(messageDecrypted).split(" ");
                String username = "";
                byte[] challenge = new byte[32];
                if (message1.length == 3 && message1[0].equals("!authenticate")) {
                    username = message1[1];
                    user =  this.usermanager.getByName(username);
                    challenge = Base64.decode(message1[2].getBytes());
                    user.setAuthState(1);
                    user.setSocket(clientsocket);
                } else {

                }

                // Get User public Key
                String finalPathPublicKey = config.getString("keys.dir")+"/"+ username.trim() + ".pub.pem";
                RSAPublicKey userPublicKey = (RSAPublicKey) Keys.readPublicPEM(new File(finalPathPublicKey));

                cipher.init(Cipher.ENCRYPT_MODE, userPublicKey);
                SecureRandom secureRandom = new SecureRandom();
                final byte[] number = new byte[32];
                secureRandom.nextBytes(number);
                this.serverChallenge = number;
                byte[] serverChallengeBase64 = Base64.encode(number);

                KeyGenerator generator = KeyGenerator.getInstance("AES");
                // KEYSIZE is in bits
                generator.init(256);
                SecretKey key = generator.generateKey();
                this.secretKey = key;

                byte iv[] = new byte[16];//generate random 16 byte IV AES is always 16bytes
                secureRandom.nextBytes(iv);
                IvParameterSpec ivspec = new IvParameterSpec(iv);
                this.ivParameterSpec = ivspec;

                String messageToSend ="!ok "+ new String(Base64.encode(challenge)) +" " + new String(serverChallengeBase64) + " " + new String(Base64.encode(key.getEncoded()))
                        + " " + new String(Base64.encode(ivspec.getIV()));
                byte[] encryptedMessage = cipher.doFinal(messageToSend.getBytes());
                //byte[] encodedMessageToSend = Base64.encode(encryptedMessage);


                return new String(encryptedMessage);

            } catch (Exception e) {
                throw new AuthenticationException(e.getMessage());
            }
        }else if(user != null && user.getAuthState() == 1){
            byte[] messageDecoded = message;

            try {
                Cipher cipher1 = Cipher.getInstance("AES/CTR/NoPadding");
                cipher1.init(Cipher.DECRYPT_MODE, this.secretKey, this.ivParameterSpec);

                byte[] messageDecrypted = cipher1.doFinal(messageDecoded);

                if(Arrays.equals(this.serverChallenge,messageDecrypted)){
                    user.setLoggedIn(true);
                    user.setAuthState(2);
                    this.tcpChannel.setIvParameterSpec(this.ivParameterSpec);
                    this.tcpChannel.setSecretKey(this.secretKey);
                    return "Succesfully authenticated with the chatserver";
                }else{
                    return "Their went something wrong in the second authentication step";
                }

            } catch (Exception e) {
                throw new AuthenticationException(e.getMessage());
            }

        }else if(user != null && user.getAuthState() == 2){
            return "You are already authenticated with the server!";
        }

        return "Something went wrong in the authentication process";
    }






}

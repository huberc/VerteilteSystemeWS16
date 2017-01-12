package chatserver;

import security.Base64Helper;
import security.RSA;
import security.RSAException;
import util.Config;
import util.Keys;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;

public class AuthenticateHelper {

    private Chatserver chatserver;
    private Config config;

    public AuthenticateHelper(Chatserver chatserver) {
        this.chatserver = chatserver;
        config = new Config("chatserver");
    }

    public String handleMessage(String message, Socket clientsocket){

        try{
            //Decode message
            byte[] messageDecoded = Base64Helper.decodeBase64(message.getBytes());

            // Get Server public Key
            String finalPath = config.getString("key");
            Key serverPrivateKey = Keys.readPrivatePEM(new File(finalPath));

            //Decrypt with public key from server
            RSA rsa = new RSA(serverPrivateKey);
            String messageDecrypted = new String(rsa.decrypt(messageDecoded));

            messageDecrypted.split(" ");
            



        }catch (IOException ex){
            //TODO handle
            System.err.println(ex);
            ex.printStackTrace();
        }catch (RSAException ex){
            //TODO Handle
            System.err.println(ex);
            ex.printStackTrace();
        }



        return "";
    }






}

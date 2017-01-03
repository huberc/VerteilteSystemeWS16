package chatserver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import util.Config;
import util.Keys;

public class TestDecorator extends ChannelDecorator implements  Runnable{

	public TestDecorator(Channel decoratedChannel){
		super(decoratedChannel);
	}
	
	@Override
	public void write(String response) throws IOException{
		
		decoratedChannel.write(response+"***decorated***");
	}
	
	@Override
	public String read() throws IOException{
		return this.decoratedChannel.read();
	}

	@Override
	public void run() {
		this.decoratedChannel.run();
	}

//	public static String hmacSHA256 (String value){
		//byte [] keyBytes = Keys.readSecretKey(file)
//	}
	
	public static void main (String [] args) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
		//get secret Key
		String msg= "YOYO bisatches!";
		String generalPath= System.getProperty("user.dir");
		String finalPath=generalPath+"\\keys\\hmac.key";
		//keys.readSecretKey actually returns a SecretKeyspec=>cast
		SecretKeySpec key=(SecretKeySpec) Keys.readSecretKey(new File(finalPath));
		//get instance of Algorithm and initialize with key
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(key);
		//sign in message-bytes
		mac.update(msg.getBytes());
		//compute finalhash
		byte[] hashToSend=mac.doFinal();
		System.out.println(hashToSend.toString());
		
		//recieving Side:
		String msgRecieved="YOYO!";
		Mac macChecker= Mac.getInstance("HmacSHA256");
		String generalPathReciever= System.getProperty("user.dir");
		String finalPathReciever=generalPathReciever+"\\keys\\hmac.key";
		SecretKeySpec keyReciever=(SecretKeySpec) Keys.readSecretKey(new File(finalPathReciever));
		macChecker.init(key);
		macChecker.update(msgRecieved.getBytes());
		byte[] hashToCheck=macChecker.doFinal();
		boolean validHash=MessageDigest.isEqual(hashToCheck, hashToSend);
		System.out.println(validHash);
	}
	
	
}

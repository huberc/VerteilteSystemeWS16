package channel;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import channel.Channel;
import channel.ChannelDecorator;
import org.bouncycastle.util.encoders.Base64;

import util.Keys;

public class TestChannel extends ChannelDecorator implements  Runnable{

	public TestChannel(Channel decoratedChannel){
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
		String msg= "YOYO!";
		String generalPath= System.getProperty("user.dir");
		String finalPath=generalPath+"\\keys\\hmac.key";
		//keys.readSecretKey actually returns a SecretKeyspec=>cast
		SecretKeySpec key=(SecretKeySpec) Keys.readSecretKey(new File(finalPath));
		//get instance of Algorithm and initialize with key
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(key);
		//sign in message-bytes
		mac.update(msg.getBytes());
		//compute finalHash
		byte[] hashToSend=mac.doFinal();
		byte[] encodedHashToSend=Base64.encode(hashToSend);
		//System.out.println(hashToSend.toString());
		
		//recieving Side:
		String msgRecieved="YOYO!";
		Mac macChecker= Mac.getInstance("HmacSHA256");
		String generalPathReciever= System.getProperty("user.dir");
		String finalPathReciever=generalPathReciever+"\\keys\\hmac.key";
		SecretKeySpec keyReciever=(SecretKeySpec) Keys.readSecretKey(new File(finalPathReciever));
		macChecker.init(keyReciever);
		macChecker.update(msgRecieved.getBytes());
		byte[] hashToCheck=macChecker.doFinal();
		boolean validHash=MessageDigest.isEqual(hashToCheck, Base64.decode(encodedHashToSend));
		System.out.println(validHash);
	}
	
	
}

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
		super.write(response);
	}
	
	@Override
	public byte[] read() throws IOException{
		return super.read();
	}

	@Override
	public void run() {
		super.run();
	}
}

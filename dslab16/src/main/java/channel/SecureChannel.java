package channel;

import security.AuthenticationException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class SecureChannel extends ChannelDecorator implements  Runnable{

	private SecretKey key;
	private Cipher cipherDecrypt, cipherEncrypt;

	public SecureChannel(Channel decoratedChannel,SecretKey key) throws AuthenticationException{

		super(decoratedChannel);

		this.key = key;

		try {
			cipherDecrypt = Cipher.getInstance("AES/CTR/NoPadding");
			cipherEncrypt = Cipher.getInstance("AES/CTR/NoPadding");
		}catch (Exception e){
			throw new AuthenticationException(e.getMessage());
		}
	}
	
	@Override
	public void write(byte[] response) throws Exception{

		//Init cipher
		IvParameterSpec ivSpec = new IvParameterSpec(response);
		cipherEncrypt.init(Cipher.ENCRYPT_MODE, key, ivSpec);

		//Encrypt
		byte[] encryptedMessage = cipherEncrypt.doFinal(response);

		decoratedChannel.write(new String(encryptedMessage));
	}
	
	@Override
	public byte[] read() throws Exception{

		byte[] data = this.decoratedChannel.read().getBytes();

		//Init cipher
		IvParameterSpec ivSpec = new IvParameterSpec(data);
		cipherDecrypt.init(Cipher.DECRYPT_MODE, key, ivSpec);

		//Decrypt
		return cipherEncrypt.doFinal(data);
	}

	@Override
	public void run() {
		this.decoratedChannel.run();
	}
}

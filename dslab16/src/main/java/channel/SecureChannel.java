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
	public void write(String response) throws Exception{

		byte[] responseBytes = response.getBytes();

		//Init cipher
		IvParameterSpec ivSpec = new IvParameterSpec(responseBytes);
		cipherEncrypt.init(Cipher.ENCRYPT_MODE, key, ivSpec);

		//Encrypt
		byte[] encryptedMessage = cipherEncrypt.doFinal(responseBytes);

		decoratedChannel.write(new String(encryptedMessage));
	}
	
	@Override
	public String read() throws Exception{

		byte[] data = this.decoratedChannel.read().getBytes();

		//Init cipher
		IvParameterSpec ivSpec = new IvParameterSpec(data);
		cipherDecrypt.init(Cipher.DECRYPT_MODE, key, ivSpec);

		//Decrypt
		byte[] decryptedMessage = cipherEncrypt.doFinal(data);

		return new String(decryptedMessage);
	}

	@Override
	public void run() {
		this.decoratedChannel.run();
	}
}

package channel;

import security.AuthenticationException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class SecureChannel extends ChannelDecorator implements  Runnable{

	private SecretKey key;
    private IvParameterSpec ivParameterSpec;
    private Cipher cipherDecrypt, cipherEncrypt;

	public SecureChannel(Channel decoratedChannel,SecretKey key, IvParameterSpec ivParameterSpec) throws AuthenticationException{

		super(decoratedChannel);

		this.key = key;
        this.ivParameterSpec = ivParameterSpec;

		try {
			cipherDecrypt = Cipher.getInstance("AES/CTR/NoPadding");
			cipherEncrypt = Cipher.getInstance("AES/CTR/NoPadding");
		}catch (Exception e){
			throw new AuthenticationException(e.getMessage());
		}
	}
	

	public void write(byte[] response) throws Exception{

		//Init cipher
		cipherEncrypt.init(Cipher.ENCRYPT_MODE, key, this.ivParameterSpec);

		//Encrypt
		byte[] encryptedMessage = cipherEncrypt.doFinal(response);

		super.write(new String(encryptedMessage));
	}
	
	@Override
	public byte[] read() throws Exception{

		byte[] data = super.read();

		//Init cipher
		IvParameterSpec ivSpec = new IvParameterSpec(data);
		cipherDecrypt.init(Cipher.DECRYPT_MODE, key, ivSpec);

		//Decrypt
		return cipherEncrypt.doFinal(data);
	}

	@Override
	public void run() {
		super.run();
	}
}

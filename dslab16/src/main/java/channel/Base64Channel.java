package channel;

import org.bouncycastle.util.encoders.Base64;
import security.Base64Helper;

import java.io.IOException;

public class Base64Channel extends ChannelDecorator{

	public Base64Channel(Channel decoratedChannel){
		super(decoratedChannel);
	}
	
	@Override
	public void write(String text) throws IOException{
		super.write(new String(Base64.encode(text.getBytes())));
	}
	
	@Override
	public String read() throws Exception{
		return new String(Base64.decode(super.read()));
	}

	@Override
	public void run() {
		super.run();
	}
}

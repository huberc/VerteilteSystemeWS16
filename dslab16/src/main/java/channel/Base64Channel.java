package channel;

import util.Base64Helper;

import java.io.IOException;

public class Base64Channel extends ChannelDecorator{

	public Base64Channel(Channel decoratedChannel){
		super(decoratedChannel);
	}
	
	@Override
	public void write(String text) throws IOException{
		decoratedChannel.write(new String(Base64Helper.encodeBase64(text.getBytes())));
	}
	
	@Override
	public String read() throws IOException{
		return new String(Base64Helper.decodeBase64(this.decoratedChannel.read().getBytes()));
	}

	@Override
	public void run() {
		this.decoratedChannel.run();
	}
}

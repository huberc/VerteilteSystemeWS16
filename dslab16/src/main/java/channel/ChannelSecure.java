package channel;

import java.io.IOException;

public class ChannelSecure extends ChannelDecorator implements  Runnable{

	public ChannelSecure(Channel decoratedChannel){

		//TODO make auth

		super(decoratedChannel);
	}
	
	@Override
	public void write(String response) throws IOException{
		
		decoratedChannel.write(response+"***secured***");
	}
	
	@Override
	public String read() throws IOException{
		return this.decoratedChannel.read();
	}

	@Override
	public void run() {
		this.decoratedChannel.run();
	}
}

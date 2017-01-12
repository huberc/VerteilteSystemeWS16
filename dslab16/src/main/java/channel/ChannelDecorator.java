package channel;

import java.io.IOException;

public abstract class ChannelDecorator implements Channel {

	protected final Channel decoratedChannel;

	public ChannelDecorator(Channel decoratedChannel) {
		this.decoratedChannel = decoratedChannel;
	}

	@Override
	public void write(String response)throws IOException{
		this.decoratedChannel.write(response);
	}
	
	@Override
	public byte[] read() throws Exception{
		return this.decoratedChannel.read();
	}
	
	@Override
	public void run (){
		this.decoratedChannel.run();
	}
}

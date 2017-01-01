package chatserver;

import java.io.IOException;

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

	
}

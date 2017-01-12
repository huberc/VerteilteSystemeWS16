package channel;

import java.io.IOException;

public interface Channel {

/*Operations that are replaced by a call to these
 * methods would throw IOException.
*/
	public byte[] read() throws Exception;
	
	public void write(String output) throws IOException;
	
	public void run();
}

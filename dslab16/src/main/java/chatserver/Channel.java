package chatserver;

import java.io.IOException;

public interface Channel {

/*Operations that are replaced by a call to these
 * methods would throw IOException.
*/
	public String read() throws IOException;
	
	public void write(String output) throws IOException;
	
	public void run();
}

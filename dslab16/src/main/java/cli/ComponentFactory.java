package cli;

import java.io.InputStream;
import java.io.PrintStream;

import client.Client;
import client.IClientCli;
import util.Config;

/**
 * Provides methods for creating an arbitrary amount of shells.
 */
public class ComponentFactory {

	public IClientCli createClient(String componentName,
			InputStream in, PrintStream out) throws Exception {
		// Define the config object to be used by the shell-example
		Config config = new Config("client");
		// Instantiate a new ShellExample with the given credentials and return
		// it
		return new Client(componentName, config, in, out);
	}

}

package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class NameserverNsDe implements INameserverCli, Runnable, INameserver {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private boolean isRoot = false;
	private INameserver rootNameserver;
	private Registry registry;
	private Shell shell;
	private ExecutorService pool;
	// private List<INameserver> childNameserver = new ArrayList<>();
	// private List<String> domains = new ArrayList<>();
	private Map<String, INameserver> childNameserver = new HashMap<>();
	// private List<User> users = new ArrayList<>();
	private Map<String, String> userAdresses = new ConcurrentHashMap<>();
	private String domain;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public NameserverNsDe(String componentName, Config config, InputStream userRequestStream,
			PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		this.pool = Executors.newFixedThreadPool(10);

		if (componentName.equals("ns-root")) {
			this.isRoot = true;
		} else {
			this.domain = this.config.getString("domain");
		}
		/*
		 * First, create a new Shell instance and provide the name of the
		 * component, an InputStream as well as an OutputStream. If you want to
		 * test the application manually, simply use System.in and System.out.
		 */
		this.shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
		/*
		 * Next, register all commands the Shell should support. In this example
		 * this class implements all desired commands.
		 */
		this.shell.register(this);
	}

	@Override
	public void run() {
		this.pool.execute(new Thread(this.shell));
		if (this.isRoot) {
			try {
				this.registry = LocateRegistry.createRegistry(this.config.getInt("registry.port"));
				// create a remote object of this server object
				INameserver remote = (INameserver) UnicastRemoteObject.exportObject(this, 0);
				// bind the obtained remote object on specified binding name in
				// the registry
				this.registry.bind(this.config.getString("root_id"), remote);
			} catch (RemoteException e) {
				throw new RuntimeException("Error while starting nameserver.", e);
			} catch (AlreadyBoundException e) {
				throw new RuntimeException("Error while binding remote object to registry.", e);
			}

		} else {
			try {
				// obtain registry that was created by the server
				this.registry = LocateRegistry.getRegistry(this.config.getString("registry.host"),
						this.config.getInt("registry.port"));
				this.rootNameserver = (INameserver) this.registry.lookup(this.config.getString("root_id"));
			} catch (RemoteException e) {
				throw new RuntimeException("Error while obtaining registry/server-remote-object.", e);
			} catch (NotBoundException e) {
				throw new RuntimeException("Error while looking for server-remote-object.", e);
			}

			try {
				INameserver remote = (INameserver) UnicastRemoteObject.exportObject(this, 0);
				this.rootNameserver.registerNameserver(this.config.getString("domain"), remote, remote);
			} catch (RemoteException e) {
				throw new RuntimeException("Remote exception", e);
			} catch (AlreadyRegisteredException e) {
				throw new RuntimeException("AlreadyRegisteredException", e);
			} catch (InvalidDomainException e) {
				throw new RuntimeException("InvalidDomainException", e);
			}

		}

		this.userResponseStream.println(this.componentName + " is up !");
	}

	@Command
	@Override
	public String nameservers() throws IOException {
		String response = "";
		List<String> handledZones = new ArrayList<>();
		for (Map.Entry<String, INameserver> nameserver : this.childNameserver.entrySet()) {
			handledZones.add(nameserver.getKey());
		}

		Collections.sort(handledZones);
		int i = 1;

		for (String s : handledZones) {
			response += i + ". " + s + "\n";
			i++;
		}

		return response;
	}

	@Command
	@Override
	public String addresses() throws IOException {
		if (!this.userAdresses.isEmpty()) {
			String response = "";
			int i = 1;
			for (Map.Entry<String, String> users : this.userAdresses.entrySet()) {
				response += i + ". " + users.getKey() + " " + users.getValue();
				i++;
			}
			return response;
		} else
			return "Their are no addresses in this zone!";
	}

	@Command
	@Override
	public String exit() throws IOException {
		try {
			// unexport the previously exported remote object
			UnicastRemoteObject.unexportObject(this, true);
			if (this.rootNameserver != null) {
				UnicastRemoteObject.unexportObject(this.rootNameserver, true);
			}
		} catch (NoSuchObjectException e) {
			System.err.println("Error while unexporting object: " + e.getMessage());
		}

		try {
			// unbind the remote object so that a client can't find it anymore
			if (isRoot) {
				this.registry.unbind(this.config.getString("root_id"));
			}
		} catch (Exception e) {
			System.err.println("Error while unbinding object: " + e.getMessage());
		}

		this.shell.close();
		this.pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!this.pool.awaitTermination(60, TimeUnit.SECONDS)) {
				this.pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!this.pool.awaitTermination(60, TimeUnit.SECONDS))
					System.err.println("Pool did not terminate");
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			this.pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}

		this.userRequestStream.close();
		this.userResponseStream.println("Successfully disconnected " + this.componentName);
		this.userResponseStream.close();
		return null;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]), System.in, System.out);
		nameserver.run();
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		if (!domain.contains(".")) {
			this.childNameserver.put(domain, nameserver);
		} else {

			String domain1 = domain.substring(domain.indexOf(".") + 1);
			if (this.childNameserver.containsKey(domain1)) {
				INameserver name1 = this.childNameserver.get(domain1);
				name1.registerNameserver(domain, nameserver, nameserverForChatserver);
			} else {
				this.childNameserver.put(domain, nameserver);
			}

		}
	}

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		String domain = username.substring(username.indexOf(".") + 1);

		if (this.childNameserver.containsKey(domain)) {
			this.childNameserver.get(domain).registerUser(username, address);
		} else {
			if (this.domain != null && this.domain.equals(domain)) {
				this.userAdresses.put(username.replace("." + this.domain, ""), address);
			} else {
				if (this.childNameserver.containsKey(domain.substring(domain.indexOf(".") + 1))) {
					this.childNameserver.get(domain.substring(domain.indexOf(".") + 1)).registerUser(username, address);
				} else {
					throw new InvalidDomainException("There is a wrong requested domain");
				}
			}
		}

	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		if (this.childNameserver.containsKey(zone)) {
			return this.childNameserver.get(zone);
		} else {
			for (Map.Entry<String, INameserver> nameservers : this.childNameserver.entrySet()) {
				if (nameservers.getKey().contains(zone + ".")) {
					return nameservers.getValue();
				}
			}
			throw new RemoteException("There is no appropriate nameserver");
		}
	}

	@Override
	public String lookup(String username) throws RemoteException {
		return this.userAdresses.get(username);
//		String domain = username.substring(username.indexOf(".") + 1);

//		if (this.childNameserver.containsKey(domain)) {
//			return this.childNameserver.get(domain).lookup(username);
//		} else {
//			if (this.domain != null && this.domain.equals(domain)) {
//				return this.userAdresses.get(username.replace("." + this.domain, ""));
//			} else {
//				if (this.childNameserver.containsKey(domain.substring(domain.indexOf(".") + 1))) {
//					return this.childNameserver.get(domain.substring(domain.indexOf(".") + 1)).lookup(username);
//				} else {
//					throw new RemoteException("There is no address stored for the given username");
//				}
//			}
//		}
	}

}

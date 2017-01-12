package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import channel.TestChannel;
import channel.TcpChannel;
import chatserver.udp.UdpListenerThread;
import cli.Command;
import cli.Shell;
import model.User;
import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config serverConfig;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private DatagramSocket datagramSocket;
	private ServerSocket serverSocket;
	private ExecutorService pool;
	private Shell shell;
	private INameserverForChatserver nameserverForChatserver;
	private Registry registry;

	private Usermanager usermanager = new Usermanager();
	private TcpChannel tcpChannel;
	private TestChannel decoratedTcpChannel;

	// private Map<String, Boolean> users = new ConcurrentHashMap<>();
	// private Map<String, String> userRegister = new ConcurrentHashMap<>();

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param serverConfig
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Chatserver(String componentName, Config serverConfig, InputStream userRequestStream,
			PrintStream userResponseStream) {
		this.componentName = componentName;
		this.serverConfig = serverConfig;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		this.pool = Executors.newFixedThreadPool(10);
		this.usermanager.loadFromConfig();

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
		try {
			// create and start a new TCP ServerSocket
			this.serverSocket = new ServerSocket(this.serverConfig.getInt("tcp.port"));
			this.datagramSocket = new DatagramSocket(this.serverConfig.getInt("udp.port"));
		} catch (IOException e) {
			throw new RuntimeException("Cannot listen on TCP port.", e);
		}

		try {
			this.registry = LocateRegistry.getRegistry(this.serverConfig.getString("registry.host"),
					this.serverConfig.getInt("registry.port"));
			this.nameserverForChatserver = (INameserverForChatserver) this.registry
					.lookup(this.serverConfig.getString("root_id"));
		} catch (RemoteException e) {
			throw new RuntimeException("Error while obtaining registry/server-remote-object.", e);
		} catch (NotBoundException e) {
			throw new RuntimeException("Error while looking for server-remote-object.", e);
		}

		try {
			// handle incoming connections from client in a separate thread
			this.userResponseStream.println("Server is up!");
			this.pool.execute(new Thread(this.shell));
			for (;;) {
				tcpChannel = new TcpChannel(this.serverSocket.accept(), this, this.userResponseStream);
				decoratedTcpChannel = new TestChannel(tcpChannel);
				this.pool.execute(decoratedTcpChannel);
				this.pool.execute(new UdpListenerThread(this.datagramSocket, this, this.userResponseStream));
			}
		} catch (IOException e) {
			this.pool.shutdown();
		}

	}

	@Override
	@Command
	public synchronized String users() throws IOException {
		String result = "";

		Integer id = 1;
		for (User u : this.usermanager.getUsers()) {
			result += (id++) + ". " + u.getName() + " " + ((u.isLoggedIn()) ? "online" : "offline") + "\n";
		}

		return result;
	}

	@Override
	@Command
	public String exit() throws IOException {
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
		if (this.serverSocket != null)
			this.serverSocket.close();

		if (this.datagramSocket != null) {
			this.datagramSocket.close();
		}
		return "Successfully closed all connections! Bye";
	}

	public void clearLoggedInUsers(String username) {

		for (User u : this.usermanager.getUsers()) {

			u.setLoggedIn(false);

			try {
				if (u.getSocket() != null && !u.getSocket().isClosed()) {
					u.getSocket().close();
				}

			} catch (IOException ex) {
				// Can't be handled
			}

		}
	}

	public String getAllOnlineUsers() {
		String result = "Online users:\n";

		for (User u : this.usermanager.getUsers()) {
			if (u.isLoggedIn()) {
				result += "* " + u.getName() + "\n";
			}
		}

		return result;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0], new Config("chatserver"), System.in, System.out);
		chatserver.run();
	}

	/**
	 * Logs in a user
	 * 
	 * @param username
	 *            username
	 * @param password
	 *            password
	 * @return a string respsone
	 */
	public String loginUser(String username, String password, Socket socket) {

		User user = this.usermanager.getByName(username);

		// Check if user exists and password is correct
		if (user == null || !user.getPassword().equals(password)) {
			return "Wrong username or password.";
		}

		synchronized (this) {
			// Check if user is logged in
			if (user.isLoggedIn()) {
				return "Already logged in.";
			}

			// Set user to logged in
			user.setLoggedIn(true);
		}

		// Set the socket and socketAddress to the user
		user.setSocket(socket);

		return "Successfully logged in.";
	}

	/**
	 * Logout a user
	 * 
	 * @param clientsocket
	 *            the socketAdress of the user
	 * @return a string response
	 */
	public synchronized String logoutUser(Socket clientsocket) {

		// Get user by address
		User user = this.usermanager.getLoggedInUserBySocket(clientsocket);

		// Check if logged in
		if (user == null) {
			return "Not logged in.";
		}

		// Logout
		user.setLoggedIn(false);
		user.setAddress(null);

		return "Successfully logged out.";
	}

	/**
	 * Registers the given address for the given client
	 * 
	 * @param clientSocket
	 *            the client Socket
	 * @param address
	 *            the address from the user
	 * @return
	 */
	public synchronized String registerUserAddress(Socket clientSocket, String address) {

		User currentUser = this.usermanager.getLoggedInUserBySocket(clientSocket);
		//
		// // Check if logged in
		// // if (currentUser == null) {
		// // return "Not logged in.";
		// // }
		//
		// currentUser.setAddress(address);

		try {
			this.nameserverForChatserver.registerUser(currentUser.getName(), address);
		} catch (RemoteException e) {
			throw new RuntimeException("Remote exception", e);
		} catch (AlreadyRegisteredException e) {
			throw new RuntimeException("AlreadyRegisteredException", e);
		} catch (InvalidDomainException e) {
			throw new RuntimeException("InvalidDomainException", e);
		}

		return "successfully registered address for " + currentUser.getName() + ".";
	}

	/**
	 * Looksup the address from the given user
	 * 
	 * @param username
	 *            the username from the user
	 * @return the address
	 */
	public String lookup(String username) {

		// User currentUser = usermanager.getLoggedInUserBySocket(clientSocket);

		// Check if logged in
		// NO need anymore, has already been checked
		// if (currentUser == null) {
		// return "Not logged in.";
		// }

		User user = this.usermanager.getByName(username);

		if (user == null || !user.isLoggedIn()) {
			return "Wrong username or user not registered.";
		}

		String[] zones = username.split("\\.");

		if (zones.length == 3) {
			try {
				INameserverForChatserver nameserverZone1 = this.nameserverForChatserver.getNameserver(zones[2]);
				INameserverForChatserver nameserverZone2 = nameserverZone1.getNameserver(zones[1]);
				return nameserverZone2.lookup(zones[0]);

			} catch (RemoteException e) {
				throw new RuntimeException("Remote exception", e);
			}
		} else if (zones.length == 2) {
			try {
				INameserverForChatserver nameserverZone1 = this.nameserverForChatserver.getNameserver(zones[1]);
				return nameserverZone1.lookup(zones[0]);
			} catch (RemoteException e) {
				throw new RuntimeException("Remote exception", e);
			}

		} else {
			return "Wrong username or user not registered.";
		}

	}

	/**
	 * Sends a message to all other clients
	 * 
	 * @param clientSocket
	 *            the client which sended the message
	 * @param message
	 *            the message
	 * @return the response to the sender
	 */
	public String sendPublicMessage(Socket clientSocket, String message) {

		// Check if user is logged in

		User currentUser = this.usermanager.getLoggedInUserBySocket(clientSocket);
		// NO need anymore, has already been checked
		/*
		 * if (currentUser == null) { return "Not logged in."; }
		 */

		// Write to other clients
		try {
			for (User u : this.usermanager.getUsers()) {
				if (u.getSocket() != null && u.isLoggedIn() && !u.getSocket().equals(clientSocket)) {

					PrintWriter writer = new PrintWriter(u.getSocket().getOutputStream(), true);
					writer.println("!public " + currentUser.getName() + ": " + message);
					writer.flush();
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		return "";
	}

	public Usermanager getUsermanager() {
		return usermanager;
	}
}

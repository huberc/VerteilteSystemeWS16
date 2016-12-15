package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import chatserver.tcp.TcpListenerThread;
import chatserver.udp.UdpListenerThread;
import cli.Command;
import cli.Shell;
import model.User;
import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config serverConfig;
	private Config userConfig;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private DatagramSocket datagramSocket;
	private ServerSocket serverSocket;
	private ExecutorService pool;
	private List<TcpListenerThread> threads = new ArrayList<TcpListenerThread>();
	private Shell shell;

	Usermanager usermanager = new Usermanager();

	//private Map<String, Boolean> users = new ConcurrentHashMap<>();
	//private Map<String, String> userRegister = new ConcurrentHashMap<>();

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
	public Chatserver(String componentName, Config serverConfig, Config userConfig, InputStream userRequestStream,
			PrintStream userResponseStream) {
		this.componentName = componentName;
		this.serverConfig = serverConfig;
		this.userConfig = userConfig;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		this.pool = Executors.newFixedThreadPool(10);
		usermanager.loadFromConfig();

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
			// handle incoming connections from client in a separate thread
			this.userResponseStream.println("Server is up!");
			this.pool.execute(new Thread(this.shell));
			for (;;) {
				this.pool.execute(new TcpListenerThread(this.serverSocket.accept(), this, this.userResponseStream));
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
		for (User u : usermanager.getUsers()) {
			result+= (id++) + ". " + u.getName() + " " + ((u.isLoggedIn())?"online":"offline") + "\n";
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

	public void addThread(TcpListenerThread tlt) {
		this.threads.add(tlt);
	}

	public void clearLoggedInUsers(String username) {

		for(User u : usermanager.getUsers()){

			u.setLoggedIn(false);

			try{
				if(u.getSocket() != null && !u.getSocket().isClosed()){
					u.getSocket().close();
				}

				if(u.getPublicSocket() != null && !u.getPublicSocket().isClosed()){
					u.getPublicSocket().close();
				}
			}catch (IOException ex){
				// Can't be handled
			}

		}
	}

	public String getAllOnlineUsers() {
		String result = "Online users:\n";

		for (User u : usermanager.getUsers()) {
			if(u.isLoggedIn()){
				result+= "* " + u.getName() + "\n";
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
		Chatserver chatserver = new Chatserver(args[0], new Config("chatserver"), new Config("user"), System.in,
				System.out);
		chatserver.run();
	}

	/**
	 * Logs in a user
	 * @param username username
	 * @param password password
	 * @return a string respsone
	 */
	public String loginUser(String username, String password, Socket socket){

		User user = usermanager.getByName(username);

		//Check if user exists and password is correct
		if(user == null || !user.getPassword().equals(password)){
			return "Wrong username or password.";
		}

		synchronized(this){
			//Check if user is logged in
			if(user.isLoggedIn()){
				return "Already logged in.";
			}

			//Set user to logged in
			user.setLoggedIn(true);
		}

		//Set the socket and socketAddress to the user
		user.setSocket(socket);

		return "Successfully logged in.";
	}


	/**
	 * Logout a user
	 * @param clientsocket the socketAdress of the user
	 * @return a string response
	 */
	public synchronized String logoutUser(Socket clientsocket){

		//Get user by address
		User user = usermanager.getLoggedInUserBySocket(clientsocket);

		//Check if logged in
		if(user == null){
			return "Not logged in.";
		}

		//Logout
		user.setLoggedIn(false);
		user.setAddress(null);

		return "Successfully logged out.";
	}

	/**
	 * Registers the given address for the given client
	 * @param clientSocket the client Socket
	 * @param address the address from the user
	 * @return
	 */
	public synchronized String registerUserAddress(Socket clientSocket, String address){

		User currentUser = usermanager.getLoggedInUserBySocket(clientSocket);

		//Check if logged in
		if(currentUser == null){
			return "Not logged in.";
		}

		currentUser.setAddress(address);

		return "successfully registered address for " + currentUser.getName() + ".";
	}

	/**
	 * Looksup the address from the given user
	 * @param username the username from the user
	 * @return the address
	 */
	public String lookup(String username, Socket clientSocket){

		User currentUser = usermanager.getLoggedInUserBySocket(clientSocket);

		//Check if logged in
		if(currentUser == null){
			return "Not logged in.";
		}

		User user = usermanager.getByName(username);

		if(user == null || user.getAddress() == null){
			//System.out.print(users.toString());
			return "Wrong username or user not registered.";
		}

		return user.getAddress();
	}

	/**
	 * Sends a message to all other clients
	 * @param clientSocket the client which sended the message
	 * @param message the message
	 * @return the response to the sender
	 */
	public String sendPublicMessage(Socket clientSocket, String message) {

		//Check if user is logged in
		User currentUser = usermanager.getLoggedInUserBySocket(clientSocket);
		if(currentUser == null){
			return "Not logged in.";
		}

		//Write to other clients
		try{
			for(User u : usermanager.getUsers()){
				if(u.getSocket() != null && u.getPublicSocket() != null && u.getPublicSocket().isConnected() && u.isLoggedIn()
						&& !u.getSocket().equals(clientSocket)){

					PrintWriter writer = new PrintWriter(u.getPublicSocket().getOutputStream(),true);
					writer.println(currentUser.getName() + ": " + message);
					writer.flush();
				}
			}
		}catch (IOException ex) {
			ex.printStackTrace();
		}

		return "";
	}

}

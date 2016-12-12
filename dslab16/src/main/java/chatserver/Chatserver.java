package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
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

	private Map<String, Boolean> users = new ConcurrentHashMap<>();
	private Map<String, String> userRegister = new ConcurrentHashMap<>();

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
	public Chatserver(String componentName, Config serverConfig, Config userConfig, InputStream userRequestStream,
			PrintStream userResponseStream) {
		this.componentName = componentName;
		this.serverConfig = serverConfig;
		this.userConfig = userConfig;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;
		this.pool = Executors.newFixedThreadPool(10);

		for (String user : userConfig.listKeys()) {
			this.users.put(user.replace(".password", ""), false);
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

	public synchronized boolean checkUserCredentials(String username, String password) {
		String pw = "";
		try {
			pw = userConfig.getString(username + ".password");
		} catch (Exception e) {
			return false;
		}
		if (pw == null) {
			return false;
		} else if (password.equals(pw)) {
			this.users.put(username, true);
			return true;
		} else {
			return false;
		}

	}

	public synchronized boolean checkAlreadyLoggedIn(String username) {
		return this.users.get(username);
	}

	public void logout(String username) {
		this.users.put(username, false);
	}

	@Override
	@Command
	public synchronized String users() throws IOException {
		String response = "";
		for (Map.Entry<String, Boolean> user : this.users.entrySet()) {
			response += user.getKey() + ": " + ((user.getValue() == true) ? "online" : "offline") + "\n";
		}
		return response;
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

	public boolean sendPublicMessage(String message, String username) {
		for (TcpListenerThread t : threads) {
			if (t.getUser() != username)
				t.sendMessage("!public " + username + ": " + message);
		}
		return true;
	}

	public void clearLoggedInUsers(String username) {
		this.users.put(username, false);
	}

	public String registerClientIpAddress(String username, String inetAddress) {
		if (inetAddress.matches("\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[:]\\d{1,5}")) {
			this.userRegister.put(username, inetAddress);
			return "Successfully registered address for " + username;
		} else {
			return "Wrong internet address specified!\n Pleas try again";
		}
	}

	public String getPrivateAddress(String username) {
		return (this.userRegister.containsKey(username)) ? this.userRegister.get(username) : null;
	}

	public String getAllOnlineUsers() {
		String response = "Online users:\n";

		List<String> onlineUser = new ArrayList<>();

		for (Map.Entry<String, Boolean> set : this.users.entrySet()) {
			if (set.getValue()) {
				onlineUser.add(set.getKey());
			}
		}

		Collections.sort(onlineUser);

		for (String s : onlineUser) {
			response += "* " + s + "\n";
		}

		return response;
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

}

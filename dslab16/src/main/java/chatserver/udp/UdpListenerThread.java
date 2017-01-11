package chatserver.udp;

import java.io.IOException;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import channel.Channel;
import chatserver.Chatserver;

/**
 * Thread to listen for incoming data packets on the given socket.
 */
public class UdpListenerThread extends Thread implements Channel{

	private DatagramSocket datagramSocket;
	private Chatserver chatserver;
	private PrintStream userResponseStream;

	public UdpListenerThread(DatagramSocket datagramSocket, Chatserver chatserver, PrintStream userResponseStream) {
		this.datagramSocket = datagramSocket;
		this.chatserver = chatserver;
		this.userResponseStream = userResponseStream;
	}

	public void run() {

		byte[] buffer;
		DatagramPacket packet;
		try {
			while (true) {
				buffer = new byte[1024];
				// create a datagram packet of specified length (buffer.length)
				/*
				 * Keep in mind that: in UDP, packet delivery is not
				 * guaranteed,and the order of the delivery/processing is not
				 * guaranteed
				 */
				packet = new DatagramPacket(buffer, buffer.length);

				// wait for incoming packets from client
				this.datagramSocket.receive(packet);
				// get the data from the packet
				String request = new String(packet.getData());

				String response = "";

				response = this.chatserver.getAllOnlineUsers();
				// get the address of the sender (client) from the received
				// packet
				InetAddress address = packet.getAddress();
				// get the port of the sender from the received packet
				int port = packet.getPort();
				if (request.trim().equals("!list")) {
					buffer = response.getBytes();
				} else {
					buffer = "Wrong request please try again!".getBytes();
				}
				/*
				 * create a new datagram packet, and write the response bytes,
				 * at specified address and port. the packet contains all the
				 * needed information for routing.
				 */
				packet = new DatagramPacket(buffer, buffer.length, address, port);
				// finally send the packet
				datagramSocket.send(packet);
			}

		} catch (IOException e) {
			this.userResponseStream.println("Connection closed to clients ");
		} finally {
			if (this.datagramSocket != null && !this.datagramSocket.isClosed())
				this.datagramSocket.close();
		}

	}

	@Override
	public String read() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void write(String output) throws IOException {
		// TODO Auto-generated method stub
		
	}
}

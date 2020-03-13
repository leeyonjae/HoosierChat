import java.io.*;
import java.nio.*;
import java.nio.channels.SocketChannel;
import java.net.*;
import java.lang.Runnable;
import java.util.*;


/*
ChatClient.java
HoosierChat client program
Written by: Yonjae Lee (yonjlee@indiana.edu)
*/

public class ChatClient {
	String serverAddr;
	int portNo = 0;
	SocketChannel client;
	boolean conn = false;
	boolean loggedout = false;


	public ChatClient() {
	}

	public static void main(String[] args) {
		/*    ChatClient [ServerAddress] [ServerPort]    */
		if (args[0].equals("--help")) {
			System.out.println("Usage:");
			System.out.println("\tChatClient [ServerAddr] [ServerPort]");
			System.out.println("\t\t[ServerAddr] -- IPv4 address of the server");
			System.out.println("\t\t[ServerPort] -- Port number of the server");
		}
		else {
			try {
				String serverAddr = args[0]; // [ServerAddress]
				int portNo = Integer.parseInt(args[1]); // [ServerPort]
				new ChatClient().start(serverAddr, portNo);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	class SendClient implements Runnable {
		
		public void run() {

			Scanner scan = new Scanner(System.in);
			String next;
			while (conn) {
				if (client.isConnected()) {
					next = scan.nextLine();
					if (next.length() >= 4096) {
						System.out.println("ERROR: The input is too long.");
					} else {
						try {
							if (next.equalsIgnoreCase("LOGOUT")) {
								loggedout = true;
							}
							byte [] message = next.getBytes();
							ByteBuffer buf = ByteBuffer.wrap(message);
							client.write(buf);
							buf.clear();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				} else {
					System.out.println("ERROR: You are not connected to the server.");
					conn = false;
				}
			}
		}
	}


	class RecvClient implements Runnable {
		
		public void run() {

			while (conn) {
				if (client.isConnected()) {
					try {
						ByteBuffer buffer = ByteBuffer.allocate(4096);
						int numread = -1;
						numread = client.read(buffer);
						if (numread == -1) {
							if (loggedout) {
								System.out.println("Successfully logged out; connection closed.");
							} else {
								System.out.println("Connection closed by server");
							}
							return;
						}
						byte[] data = new byte[numread];
						System.arraycopy(buffer.array(), 0, data, 0, numread);
						System.out.println(new String(data));
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("ERROR: You are not connected to the server.");
					conn = false;
				}
				
			}
		}
		
	}

	public void start(String serverAddr, int portNo) throws IOException, InterruptedException {
		System.out.println("HoosierChat Client by Yonjae Lee");
		System.out.println("Trying to connect to the server " + serverAddr + ":" + Integer.toString(portNo) + "...");
		InetSocketAddress server = new InetSocketAddress(serverAddr, portNo);
		client = SocketChannel.open(server);
		
		if (client.isConnected()) {
			conn = true;
			System.out.println("Connected to HoosierChat server. Please register or login.");
			System.out.println("Example: REGISTER [username] [password] / LOGIN [username] [password]");
			System.out.println("If you need help, enter HELP");
			new Thread(new SendClient()).start();
			new Thread(new RecvClient()).start();
		} else {
			System.out.println("Connection Unsuccessful. Please try again.");
		}
		
		

	}
}

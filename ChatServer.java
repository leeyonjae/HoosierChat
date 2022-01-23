import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.net.*;
import java.nio.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.*;
import java.lang.Exception;

/*
ChatServer.java
HoosierChat server program
Written by: Yonjae Lee (yonjlee@indiana.edu)
**/

class User {
  String username;
  SocketChannel conn;

  public User(String username, SocketChannel conn)
  {
	  this.username = username;
	  this.conn = conn;
  }

  public String getUsername() {
	  return this.username;
  }
  
  public SocketChannel getConn() {
	  return this.conn;
  }
}

public class ChatServer {
	private Selector selector;
	private InetSocketAddress listenAddress;
	
	public File appdata;
	public ArrayList<User> userList; // Lists of Users
	public ArrayList<SocketChannel> conns; // List of all connections

	public ChatServer(int port) throws IOException {
		listenAddress = new InetSocketAddress(port);
		this.userList = new ArrayList<User>();
		this.conns = new ArrayList<SocketChannel>();
	}


	public static void main(String[] args) throws Exception {
		/* ChatServer [Port Number] [Max connection]*/
		int portNo = 6001; // default Port is 6001
		try {
			portNo = Integer.parseInt(args[0]);
			new ChatServer(portNo).start();
		} catch (IOException e) {
				e.printStackTrace();
		}
	}
	
  // Start Server
	public void start() {
		try {
			this.selector = Selector.open();
			ServerSocketChannel serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false); // Non-blocking
			
			serverChannel.socket().bind(listenAddress);
			serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);

			System.out.println("[HoosierChat Server Started]");
			
			this.appdata = new File("appdata.txt");
			if (!this.appdata.exists()) {
				this.appdata.createNewFile();
			}

			while(true) {
				this.selector.select();
				Iterator keys = this.selector.selectedKeys().iterator();
				while (keys.hasNext()) {
					SelectionKey key = (SelectionKey) keys.next();
					keys.remove();
					if (!key.isValid()) {
						continue;
					}
					if (key.isAcceptable()) {
						this.accept(key);
					}
					else if (key.isReadable()) {
						this.read(key);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel channel = serverChannel.accept();
		channel.configureBlocking(false); // Non-blocking
		Socket socket = channel.socket();
		SocketAddress remoteAddr = socket.getRemoteSocketAddress();
		System.out.println("Connected to a client from: " + remoteAddr);
		try {
			channel.register(this.selector, SelectionKey.OP_READ);
			conns.add(channel); // Add channel to the connections
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void read(SelectionKey key) throws IOException {
		try {
			SocketChannel channel = (SocketChannel) key.channel();
			ByteBuffer buffer = ByteBuffer.allocate(4096);
			int numread = -1;
			numread = channel.read(buffer);
		  
			// termination process
			if (numread == -1) {
				//Socket socket = channel.socket();
				SocketAddress remoteAddr = channel.socket().getRemoteSocketAddress();
				System.out.println("Connection closed by client: " + remoteAddr);
				this.conns.remove(channel);
				Iterator<User> i = userList.iterator();
				while (i.hasNext()) {
					User u = i.next();
					if (u.getConn().equals(channel)) {
						userList.remove(u);
						break;
					}
				}
				channel.close();
				
				key.cancel();
				return;
			}
			
			byte[] data = new byte[numread];
			System.arraycopy(buffer.array(), 0, data, 0, numread);
			String input = new String(data);
			//System.out.println(input); test purpose; remove later
			processInput(input, channel); // process input and execute appropriate command
			
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
	public void send(SocketChannel sendto, String msgs) {
		  try {
			  byte [] msgbyte = msgs.getBytes();
			  ByteBuffer msgbuf = ByteBuffer.wrap(msgbyte);
			  sendto.write(msgbuf);
			  msgbuf.clear();
		  } catch (Exception e) {
			  e.printStackTrace();
		  }
	}
	
	public boolean isOnline (String username) {
		boolean is = false;
		Iterator<User> i = userList.iterator();
		while (i.hasNext()) {
			User u = i.next();
			if (u.getUsername().equals(username)){
				is = true;
			}
		}
		return is;
	}
	
  
  /* Chatting Functions
   * REGISTER [username] [password] - userReg 
   * LOGIN [username] [password] - auth
   * LOGOUT - terminate
   * SEND [msg] - sendMsg
   * SENDTO [username] [msg] - sendPvtMsg
   * SENDA [msg] - sendMsgAnon
   * SENDATO [username] [msg] - sendPMAnon
   * LIST - showUserList
   *  */
	public void processInput(String input, SocketChannel client) {
		// Filter out commands from regular chat contents
		String[] inputSplit = input.split("\\s");
		String command = inputSplit[0]; // command
		if (command.equalsIgnoreCase("REGISTER")) {
			if (inputSplit.length == 3) {
				try {
					register(inputSplit[1], inputSplit[2], client, this.appdata);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else {
				send(client, "REGISTER [username] [password]");
			}
		} else if (command.equalsIgnoreCase("LOGIN") && inputSplit.length == 3) {
			if (inputSplit.length == 3) {
				try {
					auth(inputSplit[1], inputSplit[2], client, this.appdata);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else {
				send(client, "LOGIN [username] [password]");
			}
		} else if (command.equalsIgnoreCase("LOGOUT")) {
			Iterator<User> users = userList.iterator();
			while(users.hasNext()) {
				User u = users.next();
				if (u.getConn().equals(client)) {
					terminate(u);
					break;
				}
			}
		} else if (command.equalsIgnoreCase("SEND") && inputSplit.length >= 2) {
			boolean login = false;
			Iterator<User> users = userList.iterator();			
			while(users.hasNext()) {
				User u = users.next();
				if (u.getConn().equals(client)) {
					login = true;
					String msg = "";
					for (int i = 1; i < inputSplit.length; i++) {
						msg = msg.concat(inputSplit[i] + " ");
					}
					sendMsg(u, msg);
					return;
				}
			}
			if (!login) {
				  send(client, "ERROR: Please log in first.");
			}
		} else if (command.equalsIgnoreCase("SENDTO") && inputSplit.length >= 3) {
			boolean login = false;
			Iterator<User> users = userList.iterator();			
			while(users.hasNext()) {
				User u = users.next();
				if (u.getConn().equals(client)) {
					login = true;
					String recipient = inputSplit[1];
					if (isOnline(recipient)) {
						String msg = "";
						for (int i = 2; i < inputSplit.length; i++) {
							msg = msg.concat(inputSplit[i] + " ");
						}
						Iterator<User> users2 = userList.iterator();
						while(users2.hasNext()) {
							User u2 = users2.next();
							if (u2.getUsername().equals(recipient)) {
								sendPvtMsg(u, u2, msg);
								return;
							}
						}

					}
					else {
						send(client, "[SENDTO] ERROR: The user you are messaging is not online.");
					}
				}
			}
			if (!login) {
				  send(client, "ERROR: Please log in first.");
			}
		} else if (command.equalsIgnoreCase("SENDA") && inputSplit.length >= 2) {
			boolean login = false;
			Iterator<User> users = userList.iterator();
			while (users.hasNext()) {
				User u = users.next();
				if (u.getConn().equals(client)) {
					login = true; 
					String aMsg = "[Anonymous]: ";
					
					for (int i = 1; i < inputSplit.length; i++) {
						aMsg = aMsg.concat(inputSplit[i] + " ");
					}
					sendMsgAnon(aMsg);
				}
			}
			if (!login) {
				  send(client, "ERROR: Please log in first.");
			}
		} else if (command.equalsIgnoreCase("SENDATO") && inputSplit.length >= 3) {
			boolean login = false;
			Iterator<User> users = userList.iterator();
			while (users.hasNext()) {
				User u = users.next();
				if (u.getConn().equals(client)) {
					login = true; 
					String recipient = inputSplit[1];
					if (isOnline(recipient)) {
						String aMsg = "[(Private)Anonymous]: ";
						Iterator<User> users2 = userList.iterator();
						while(users2.hasNext()) {
							User u2 = users2.next();
							if (u2.getUsername().equals(recipient)) {
								for (int i = 2; i < inputSplit.length; i++) {
									aMsg = aMsg.concat(inputSplit[i] + " ");
								}
								sendPMAnon(u2, aMsg);
								return;
							}
						}
					}
					else {
						send(client, "[SENDATO] ERROR: The user you are messaging is not online.");
					}
				}
			}
			if (!login) {
				  send(client, "ERROR: Please log in first.");
			}
		} else if (command.equalsIgnoreCase("LIST")) {
			showUserList(client);
		} else if (command.equalsIgnoreCase("HELP")) {
			showHelp(client);
		} else {
			System.out.println("Error occurred while processing user request");
			send(client, "ERROR: Please enter a valid command");
		}	  
	}
	  
	  public void register(String username, String password, SocketChannel client, File appdata) throws IOException {
		  // each line consists of:
		  // "(username) (password)"
		  try {
			  FileReader reader = new FileReader(appdata);
			  BufferedReader br = new BufferedReader(reader);
			  String entry;
			  String temp;
			  while ((entry = br.readLine()) != null) {
				  temp = entry.split("\\s")[0];
				  if (temp.equals(username)) {
					  send(client, "[REGISTER] ERROR: The username already exists");
					  br.close();
					  reader.close();
					  return;
				  }
			  }
			  temp = username + " " + password;
			  FileWriter appwrite = new FileWriter(appdata,true);
			  BufferedWriter bw = new BufferedWriter(appwrite);
			  bw.write(temp);
			  bw.newLine();
			  bw.close();
			  appwrite.close();
			  send(client, "Account [" + username + "] successfully registered!");
			  System.out.println("[Log] Username [" + username + "] successfully registered");
			  br.close();
			  reader.close();
		  } catch (IOException e) {
			  e.printStackTrace();
		  }
		  
	  }

	  public void auth(String username, String password, SocketChannel client, File appdata) throws IOException {
		  boolean loggedin = false;
		  boolean dupe = false;
		  try {
			  FileReader reader = new FileReader(appdata);
			  BufferedReader br = new BufferedReader(reader);
			  String entry;
			  while ((entry = br.readLine()) != null) {
				  String[] temp = entry.split("\\s");
				  if (temp[0].equalsIgnoreCase(username)) {
					  if (isOnline(username)) {
						  // duplicate check
						  send(client, "[LOGIN] ERROR: Authentication failed");
						  System.out.println("[Redundant login attempt detected]");
						  dupe = true;
						  break;
					  } else if (temp[1].equalsIgnoreCase(password)) {
						  // auth success 
						  User u = new User(username, client);
						  send(client, "[LOGIN] Authentication successful. Welcome, " + username);
						  System.out.println("[Log] User [" + username + "] logged in");
						  userList.add(u);
						  loggedin = true;
					  } else {
						  // auth failure
						  send(client, "[LOGIN] ERROR: Authentication failed");
					  }
					  break;
				  }
			  }
			  if (!loggedin & !dupe) {
				  // user not found
				  send(client, "[LOGIN] ERROR: Authentication failed");
				  //client.close(); 
			  }
			  reader.close();
			  br.close();
		  } catch (IOException e) {
			  e.printStackTrace();
		  }
	  }
	  
	  public void terminate(User u) {
		  // Terminate a user's session
		  try {
			  this.conns.remove(u.getConn());
			  u.getConn().close();
			  userList.remove(u);
			  System.out.println("[Log] User [" + u.getUsername() + "] logged out");			  
		  } catch (IOException e) {
			e.printStackTrace();  
		  }
	  }
  
	  public void sendMsg(User from, String msg) {
		  
		  String msgFormatted = "" + from.getUsername() + ": " + msg;
		  Iterator<User> allUsers = userList.iterator();
		  while(allUsers.hasNext()) {
			  send(allUsers.next().getConn(), msgFormatted);
		  }
		  System.out.println(msgFormatted);
	  }
	  
	  public void sendPvtMsg(User from, User to, String msg) {
		  String msgFormatted = "(Private) " + from.getUsername() + ": " + msg;
		  send(to.getConn(), msgFormatted);
		  System.out.println("[" + from.getUsername() + " sent a private message to " + to.getUsername() + "]");
	  }
	  
	  public void sendMsgAnon(String msg) {
		  Iterator<User> allUsers = userList.iterator();
		  while(allUsers.hasNext()) {
			  send(allUsers.next().getConn(), msg);
		  }
		  System.out.println(msg);
	  }
	  public void sendPMAnon(User to, String msg) {
		  send(to.getConn(), msg);
		  System.out.println("[An anonymous user sent a private message to: " + to.getUsername() + "]");
	  }
  

	  public void showUserList(SocketChannel client) {
		  System.out.println("[someone requested the user list]");
		  String start = "==CURRENT USERS LIST==";
		  String end = "=======END LIST=======";
		  Iterator<User> ulistIter = userList.iterator();
		  send(client, start);
		  while (ulistIter.hasNext()) {
			  String name = ulistIter.next().getUsername() + "\n";
			  send(client, name);
		  }
		  send(client, end);	  
	  }
	  

	  public void showHelp(SocketChannel client) {
		  System.out.println("[a user requested manual]");
		  send(client, "========================HoosierChat HELP========================\n");
		  send(client, "REGISTER [username] [password]\n");
		  send(client, "\tRegisters new account [username] with password [password]\n");
		  send(client, "LOGIN [username] [password]\n");
		  send(client, "\tAccount login with [username] and [password]\n");
		  send(client, "LOGOUT\n");
		  send(client, "\tLogs out and disconnects from server\n");
		  send(client, "SEND [message...]\n");
		  send(client, "\tSends a message to everyone\n");
		  send(client, "SENDTO [username] [message...]\n");
		  send(client, "\tSends a private message to [username]\n");
		  send(client, "SENDA [message...]\n");
		  send(client, "\tSends an anonymous message to everyone\n");
		  send(client, "SENDATO [username] [message...]\n");
		  send(client, "\tSends an anonymous private message to [username]\n");
		  send(client, "LIST\n");
		  send(client, "\tShows the list of all online users\n");
		  send(client, "HELP\n");
		  send(client, "\tShows the list of available commands\n");
		  send(client, "===========================END OF HELP===========================\n");	  
	  }
}

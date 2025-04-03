package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailWriter;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MySMTPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;
    private String socketResponse;
    private String response;
    private String state = "";
    private String[] vrfyUser;
    private boolean userExists;
    private String sender;
    private String rcptAddress;
    private ArrayList<Mailbox> recipients = new ArrayList<>();
    private String data = "";
    private String temp;
    private char[] charArray;

    // TODO Additional properties, if needed

    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's information.
     */
    public MySMTPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the initial welcome message, and then repeatedly
     * read requests, process the individual operation, and return a response, according to the SMTP protocol. Empty
     * request lines should be ignored. Only returns if the connection is terminated or if the QUIT command is issued.
     * Must close the socket connection before returning.
     */
    @Override
    public void run() {
        try (this.socket) {

            // TODO Complete this method
            socketOut.println("220 " + getHostName());
            socketResponse = socketIn.readLine();
            response = socketResponse.toUpperCase();
	    while (!response.equals("QUIT")) {
                if (response.startsWith("EHLO") || response.startsWith("HELO")) {
		    socketOut.println("250 " + getHostName());
		    state = "HELO";
                }
                else if (response.startsWith("NOOP")) {
                    socketOut.println("250 OK");
                }
                else if (response.startsWith("VRFY")) {
                    vrfyUser = socketResponse.split(" ");
                    if (vrfyUser.length == 1) {
                        socketOut.println("501 Syntax error, command unrecognized");
                    }
                    else {
			userExists = Mailbox.isValidUser(vrfyUser[1]);
                        if (userExists) {
                            socketOut.println("250 OK");
                        } else {
                            socketOut.println("550 No such user here");
                        }
                    }
                }
                else if (response.equals("RSET")) {
			recipients.clear();
			socketOut.println("250 Ok");
			state = "HELO";
                }
                else if (response.startsWith("EXPN") || response.startsWith("HELP")) {
                    socketOut.println("502 Command not implemented");
                }
		else if (response.startsWith("MAIL FROM:<") && response.charAt(response.length() - 1) == '>') {
			if (!state.equals("HELO")) {
				socketOut.println("503 Bad sequence of commands");
			}
			else {
				socketOut.println("250 Ok");
				sender = socketResponse.substring(response.indexOf('<') + 1, response.indexOf('>'));
				state = "MAIL";
			}
		}
		else if (response.startsWith("RCPT TO:<") && response.charAt(response.length() - 1) == '>') {
			if (!state.equals("MAIL") && !state.equals("RCPT")) {
				socketOut.println("503 Bad sequence of commands");
			}
			else {
				rcptAddress = socketResponse.substring(response.indexOf('<') + 1, response.indexOf('>'));
				if (Mailbox.isValidUser(rcptAddress)) {
					socketOut.println("250 Ok");
					recipients.add(new Mailbox(rcptAddress));
					state = "RCPT";
				}
				else {
					socketOut.println("550 No such user here");
				}
			}
		}
		else if (response.equals("DATA")) {
	 		if (state.equals("RCPT")) {
				socketOut.println("354 Start mail input; end with <CRLF>.<CRLF>");
				temp = socketIn.readLine();
				while (!temp.equals(".")) {
					data += temp + "\n";
					temp = socketIn.readLine();
				}
				socketOut.println("250 Ok");
                		try(MailWriter mailWriter = new MailWriter(recipients)) {
                    		charArray = data.toCharArray();
                    		mailWriter.write(charArray, 0, data.length());
                		}
				state = "HELO";
			}
			else {
				socketOut.println("503 Bad sequence of commands");
			}
		}		
		else {
                    socketOut.println("500 Syntax error, command unrecognized");
                }
		socketResponse = socketIn.readLine();
		response = socketResponse.toUpperCase();
            }
            socketOut.println("221 OK");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("Error in client's connection handling.");
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the name of the current host. Used in the response of commands like HELO and EHLO.
     * @return A string corresponding to the name of the current host.
     */
    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try (BufferedReader reader = Runtime.getRuntime().exec(new String[] {"hostname"}).inputReader()) {
                return reader.readLine();
            } catch (IOException ex) {
                return "unknown_host";
            }
        }
    }

    /**
     * Main process for the SMTP server. Handles the argument parsing and creates a listening server socket. Repeatedly
     * accepts new connections from individual clients, creating a new server instance that handles communication with
     * that client in a separate thread.
     *
     * @param args The command-line arguments.
     * @throws IOException In case of an exception creating the server socket or accepting new connections.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            throw new RuntimeException("This application must be executed with exactly one argument, the listening port.");
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
            serverSocket.setReuseAddress(true);
            System.out.println("Waiting for connections on port " + serverSocket.getLocalPort() + "...");
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted a connection from " + socket.getRemoteSocketAddress());
                try {
                    MySMTPServer handler = new MySMTPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}

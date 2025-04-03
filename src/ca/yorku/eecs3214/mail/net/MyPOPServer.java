package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailMessage;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class MyPOPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;

    // TODO Additional properties, if needed
    private MailMessage message;
    private String pass;

    /**
     * Initializes an object responsible for a connection to an individual client.
     *
     * @param socket The socket associated to the accepted connection.
     * @throws IOException If there is an error attempting to retrieve the socket's
     *                     information.
     */
    public MyPOPServer(Socket socket) throws IOException {
        this.socket = socket;
        this.socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    /**
     * Handles the communication with an individual client. Must send the
     * initial welcome message, and then repeatedly read requests, process the
     * individual operation, and return a response, according to the POP3
     * protocol. Empty request lines should be ignored. Only returns if the
     * connection is terminated or if the QUIT command is issued. Must close the
     * socket connection before returning.
     */
    @Override
    public void run() {
        // Use a try-with-resources block to ensure that the socket is closed
        // when the method returns
        try (this.socket) {
          Mailbox mailbox;
            // TODO Complete this method
            while (String response = socketIn.readLine() && !response.equals("QUIT")) {
              String[] resSplit = response.split(" ");

              if (resSplit.length > 1) {
                message = mailbox.getMailMessage(resSplit[1]);
              }

              if resSplit[0].equals("USER") {
                String username = resSplit[1];
                if (mailbox.getUsername(resSplit[1])) {
                  socketOut.println("+OK name is a valid mailbox");
                  mailbox = new Mailbox(username);
                  response = socketIn.readLine();
                  resSplit = response.split(" ");
                  if (resSplit[0].equals("PASS")) {
                    if (mailbox.getUserMap().get(user).equals(resSplit[1])) {
                      pass = resSplit[1];
                      socketOut.println("+OK " + username + "'s mailbox has " + mailbox.size(false) + " messages");
                    }
                  }
                } else {
                  socketOut.println("-ERR never heard of mailbox name");
                }
              } 

              else if (resSplit[0].equals("STAT")) {
                socketOut.println("+OK " + mailbox.size(false) + " " + mailbox.getTotalUndeletedFileSize(false));
              } 

              else if (resSplit[0].equals("LIST")) {
                socketOut.println("+OK " + mailbox.size(false) + " messages (" + mailbox.getTotalUndeletedFileSize(false) + " octets)");

                int num = 0;
                for (MailMessage message : mailbox) {
                  socketOut.println(num + " " + message.getFileSize())
                }
              }

              else if (resSplit[0].equals("RETR")) {
                socketOut.println("+OK " + message.getFileSize + " octets");
                socketOut.println(message.file);
                socketOut.println(".");
              }

              else if (resSplit[0].equals("DELE")) {
                if (!message.isDeleted()) {
                  message.tagForDeletion();
                  socketOut.println("+OK message " + resSplit[1] + " deleted");
                } else {
                  socketOut.println("-ERR message " + resSplit[1] + " already deleted");
                }
              }

              else if (resSplit[0].equals("RSET")) {
                for (MailMessage m : mailbox.loadMessages(pass)) {
                  m.undelete();
                }
                socketOut.println("+OK maildrop has " + mailbox.size(false) + " messages");
              }
            }
        } catch (IOException e) {
            System.err.println("Error in client's connection handling.");
            e.printStackTrace();
        }
    }

    /**
     * Main process for the POP3 server. Handles the argument parsing and
     * creates a listening server socket. Repeatedly accepts new connections
     * from individual clients, creating a new server instance that handles
     * communication with that client in a separate thread.
     *
     * @param args The command-line arguments.
     * @throws IOException In case of an exception creating the server socket or
     *                     accepting new connections.
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            throw new RuntimeException(
                    "This application must be executed with exactly one argument, the listening port.");
        }

        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
            serverSocket.setReuseAddress(true);

            System.out.println("Waiting for connections on port " + serverSocket.getLocalPort() + "...");
            // noinspection InfiniteLoopStatement
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted a connection from " + socket.getRemoteSocketAddress());
                try {
                    MyPOPServer handler = new MyPOPServer(socket);
                    handler.start();
                } catch (IOException e) {
                    System.err.println("Error setting up an individual client's handler.");
                    e.printStackTrace();
                }
            }
        }
    }
}

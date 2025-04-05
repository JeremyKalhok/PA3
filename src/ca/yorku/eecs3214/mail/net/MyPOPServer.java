package ca.yorku.eecs3214.mail.net;

import ca.yorku.eecs3214.mail.mailbox.MailMessage;
import ca.yorku.eecs3214.mail.mailbox.Mailbox;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class MyPOPServer extends Thread {

    private final Socket socket;
    private final BufferedReader socketIn;
    private final PrintWriter socketOut;

    // TODO Additional properties, if needed
    private Mailbox mailbox = null;
    private String enteredUser = null;
    private boolean isAuthenticated = false;

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
        String response;

        // Use a try-with-resources block to ensure that the socket is closed
        // when the method returns
        try (this.socket) {
          socketOut.println("+OK POP3 server ready");
            // TODO Complete this method

          while ((response = socketIn.readLine()) != null) {
            if (response.trim().isEmpty()) {
              continue;
            }

            String[] resSplit = response.split("\\s+");
            String command = resSplit[0].toUpperCase();

            if (command.equals("QUIT")) {
              if (isAuthenticated && mailbox != null) {
                List<MailMessage> messagesToDelete = new ArrayList<>();
                try {
                  Iterator<MailMessage> iter = mailbox.iterator();
                  while (iter.hasNext()) {
                    MailMessage m = iter.next();
                    if (m.isDeleted()) {
                      messagesToDelete.add(m);
                    }
                  }
                } catch (Mailbox.MailboxNotAuthenticatedException e) {
                }
                boolean deleteFail = false;

                for (MailMessage m : messagesToDelete) {
                  if (!m.getFile().delete()) {
                    deleteFail = true;
                  }
                }

                if (deleteFail) {
                  socketOut.println("-ERR some deleted messages not removed");
                } else {
                  int messagesLeft = 0;
                  try {
                    Iterator<MailMessage> iter = mailbox.iterator();
                    while (iter.hasNext()) {
                      MailMessage m = iter.next();
                      if (m.getFile().exists()) {
                        messagesLeft++;
                      }
                    }
                  } catch (Mailbox.MailboxNotAuthenticatedException e) {
                  }

                  if (messagesLeft == 0) {
                    socketOut.println("+OK " + mailbox.getUsername() + " POP3 server signing off (mailbox empty)");
                  } else {
                    socketOut.println("+OK " + mailbox.getUsername() + " POP3 server signing off ( " + messagesLeft + " messages left)");
                  }
                }
              } else {
                socketOut.println("+OK Goodbye");
              }
              break;
            }
//--------------------------------------------------------------
            else if (command.equals("USER")) {
              if (resSplit.length != 2) {
                socketOut.println("-ERR requires 1 argument");
              } else {
                String username = resSplit[1];
                if (Mailbox.isValidUser(username)) {
                  mailbox = new Mailbox(enteredUser);
                  enteredUser = username;
                  socketOut.println("+OK " + enteredUser + " is a valid mailbox");
                } else {
                  socketOut.println("-ERR never heard of mailbox name");
                }
              }
            }
//--------------------------------------------------------------
            else if (command.equals("PASS")) {
              if (enteredUser == null) {
                socketOut.println("-ERR need valid USER command");
              } else if (resSplit.length != 2) {
                socketOut.println("-ERR requires 1 argument");
              } else {
                String password = resSplit[1];
                try {
                  mailbox.loadMessages(password);
                  isAuthenticated = true;
                  socketOut.println("+OK " + mailbox.getUsername() + "'s maildrop has " + mailbox.size(false) + " messages");
                } catch (Mailbox.MailboxNotAuthenticatedException e) {
                  socketOut.println("-ERR invalid password");
                }
              }
            }
//--------------------------------------------------------------
            else if (command.equals("STAT")) {
              if (!isAuthenticated) {
                socketOut.println("-ERR Not authenticated");
              } else {
                try {
                  socketOut.println("+OK " + mailbox.size(false) + " " + mailbox.getTotalUndeletedFileSize(false));
                } catch (Mailbox.MailboxNotAuthenticatedException e) {
                  socketOut.println("-ERR mailbox not authenticated");
                }
              }
            }
//--------------------------------------------------------------
            else if (command.equals("LIST")) {
              if (!isAuthenticated) {
                socketOut.println("-ERR Not authenticated");
              } else {
                if (resSplit.length == 1) {
                  socketOut.println("+OK " + mailbox.size(false) + " messages (" + mailbox.getTotalUndeletedFileSize(false) + " octets)");
                  for (int i = 1; i <= mailbox.size(true); i++) {
                    MailMessage m = mailbox.getMailMessage(i);
                    if (!m.isDeleted()) {
                      socketOut.println(i + " " + m.getFileSize());
                    }
                  }
                  socketOut.println(".");
                }
                else if (resSplit.length == 2) {
                  try {
                    int idx = Integer.parseInt(resSplit[1]);
                    if (idx < 1 || idx > mailbox.size(true)) {
                      socketOut.println("-ERR no such message, only " + mailbox.size(false) + " messages in maildrop");
                    } else {
                      if (mailbox.getMailMessage(idx).isDeleted()) {
                        socketOut.println("-ERR no such message");
                      } else {
                        socketOut.println("+OK " + idx + " " + mailbox.getMailMessage(idx).getFileSize());
                      }
                    }
                  } catch (Exception e) {
                    socketOut.println("-ERR argument needs to be a number");
                  }
                }
              }
            }
//--------------------------------------------------------------
            else if (command.equals("RETR")) {
              if (!isAuthenticated) {
                socketOut.println("-ERR Not authenticaated");
              } else {
                try {
                  int idx = Integer.parseInt(resSplit[1]);
                  MailMessage m = mailbox.getMailMessage(idx);
                  if (m.isDeleted()) {
                    socketOut.println("-ERR no such message");
                  } else {
                    File file = m.getFile();
                    socketOut.println("+OK " + m.getFileSize() + " octets");
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String line = br.readLine();
                    while (line != null) {
                      socketOut.println(line);
                      line = br.readLine();
                    }
                    br.close();
                    socketOut.println(".");
                  }
                } catch (Exception e) {
                  socketOut.println("-ERR argument must be a number");
                }
              }
            }
//--------------------------------------------------------------
            else if (command.equals("DELE")) {
              if (!isAuthenticated) {
                socketOut.println("-ERR Not authenticated");
              } else {
                try {
                  int idx = Integer.parseInt(resSplit[1]);
                  MailMessage m = mailbox.getMailMessage(idx);
                  if (m.isDeleted()) {
                    socketOut.println("-ERR message " + idx + " already deleted");
                  } else {
                    m.tagForDeletion();
                    socketOut.println("+OK message " + idx + " deleted");
                  }
                } catch (Exception e) {
                  socketOut.println("-ERR argument must be a number");
                }
              }
            }
//--------------------------------------------------------------
            else if (command.equals("NOOP")) {
              if (!isAuthenticated) {
                socketOut.println("-ERR Not authenticated");
              } else {
                socketOut.println("+OK");
              }
            }
//--------------------------------------------------------------
            else if (command.equals("RSET")) {
              if (!isAuthenticated) {
                socketOut.println("-ERR Not authenticated");
              } else {
                Iterator<MailMessage> iter = mailbox.iterator();
                while (iter.hasNext()) {
                  MailMessage m = iter.next();
                  m.undelete();
                }
                socketOut.println("+OK maildrop has " + mailbox.size(false) + " messages (" + mailbox.getTotalUndeletedFileSize(false) + " octets)");
              }
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

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.LinkedList;


//Creates a Thead that handles connection on a specific socket
//- Takes in a buffer that handles outputting to all clients
//- Takes in a socket that will be dealt with
class ServerThread extends Thread {

    //Define null socket and buffer
    private Socket socket = null;
	private UnboundedBuffer ub = null;
	private String name;
	
    //Default constructor
    public ServerThread(Socket socket, UnboundedBuffer ub) {
    	this.socket = socket;
		this.ub = ub;
    }
    
    //Handle the connection
    public void run() {
       
        boolean isFirstRun = true;
        while(true){
            try {
                
                String input;
                
                //Opens up a BufferedStream from the socket
                BufferedReader socketIn = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                
                //Attempt to read from socket
                try{
                    if((input = socketIn.readLine()) != null){
                        if(isFirstRun) {
                            ub.insert(input + " is connected");
                            name = input;
                            isFirstRun = false;
                        }
                        else{
                            ub.insert(name + ": " + input);
                        }
                    }
                    // Close things
                }catch(SocketException e){
                    //Do not handle exception and terminate thread
                    //Note: If we get to this point, it is assumed
                    //      that the socket is closed. (client termination)
                    
                    ChatServer.numConnections--;
                    System.out.println("Number of connections: " + ChatServer.numConnections);
                    //Send the disconnection message to all clients
                    ub.insert(name + " is disconnecting...");
                    //break out of the loop
                    break;
                }

            } catch (IOException e) {
               e.printStackTrace();
            }
        }
    }
    
}
//The server itself
//- Handles taking in sockets and creating relevent threads.
//- inializes an unbounded buffer
//- Opens connections on port 7777
public class ChatServer {
    //Static variable to track number of connections
    public static int numConnections = 0;
    
    
    public static void main(String[] args) throws IOException {      
        //General declarations and instantiations
        ServerSocket serverSocket = null;
        UnboundedBuffer ub = new UnboundedBuffer();
        MessageConsumer messageTaker = new MessageConsumer(ub);
        
        try {
            //Attempt to listen on port 7777
            serverSocket = new ServerSocket(7777);
        } catch (IOException e) {
            System.err.println("Could not listen on port: 7777");
            //Exit with opcode 1
            System.exit(1);
        }
        System.out.println("Sucessfully connected to port: 7777");
		messageTaker.start();
        
        while (true) {
            //Print out the inital number of connections
            System.out.println("Number of connections: " + ChatServer.numConnections);
            
            //Accept a new connection
			Socket temp = serverSocket.accept();
            
            //Send the socket to the consumer
			messageTaker.addSocket(temp);
            
            //Start a thread with the socket
			new ServerThread(temp, ub).start();
            
            //Increment number of connections
            ChatServer.numConnections++;
        }
    }
}

//An unbounded buffer of strings that enforces mutual exclusion
//- We used an arrayList specificly (see design.txt)
class UnboundedBuffer{
    private LinkedList<String> messageList;
    private int numMessages;

    public UnboundedBuffer(){
        messageList = new LinkedList<>();
        numMessages = 0;
    
    }
    //Inserts message into the buffer
    // - Becuase list is unbounded does not need to check if it's full
    // - Notify's all waiting theads that information is available
    public synchronized void insert(String message){
        messageList.addFirst(message);
        numMessages++;
        notifyAll();
    }
    
    
    //Removes the first message placed into the buffer
    // - Will wait if buffer is empty
    // - Notifys waiting threads when buffer has space available 
    //The latter condition might not apply because this is a LinkedList and therefore
    //unbounded.
    public synchronized String remove() throws InterruptedException{
        while(numMessages <= 0){
            wait();
        }
        numMessages--;
        String temp = messageList.removeLast();
        return temp;
    }
    
}


//Takes a message and throws it out to all known sockets
//- Socket's are stored in an an ArrayList for random access
//- We chose to use an unbounded buffer (see design.txt)
class MessageConsumer extends Thread{

    private UnboundedBuffer ub;
    private ArrayList<Socket> socketList;
    
    //Standard constructor, builds Consumer with a known buffer
    MessageConsumer(UnboundedBuffer ub){
        this.ub = ub;
        socketList = new ArrayList<>();
    }
    //Overloaded constructor, also adds a socket
    MessageConsumer(UnboundedBuffer ub, Socket startingSocket){
        this(ub);
        addSocket(startingSocket);
    }
    
    //Start thread
    public void run(){
        
        while(true){
            String input;
            //Attempt to remove a message from buffer
            try{
                //Will wait until data is available
                if((input = ub.remove()) != null)
                    sendMessage(input);
            }catch(InterruptedException e){
                e.printStackTrace();
            }
        }
    }
    
    /* 
    * Takes a string and attempts to send it to all known sockets.
    * - Constructs a tempororay printwriter to output string.
    * - If we cannot open the output stream, then we must assume
    *   that the socket has been closed elsewhere and therefore
    *   we can remove it from the arraylist.
    */
    private void sendMessage(String toSend){

        for(int i = 0; i < socketList.size(); i++){
            try{
                PrintWriter tmp = new PrintWriter(socketList.get(i).getOutputStream(), true);  
                tmp.println(toSend);

            }catch(IOException e){//Potential error here?
                socketList.remove(i);
            }
        }
    }
    
    //Adds a potential client to the arraylist
    // - Creates/adds nothing if input is null
    public void addSocket(Socket toAdd){
        if(toAdd == null)
            return;
        socketList.add(toAdd);
    }
    
    //Removes a socket from the arrayList
    public void removeSocket(int index){
        //Check if the index is in bounds
        if(index >= socketList.size() || index < 0)
            return;
        socketList.remove(index);
    }
}
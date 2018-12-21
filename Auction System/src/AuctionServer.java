import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.security.PrivateKey;

/**
 * @Author Lewis Linaker
 * @Description Main AuctionServer Program to allow the Bidder and Seller Clients to communicate
 * over an RMI interface
 */

public class AuctionServer {

    public static PrivateKey serverPrivateKey;

    /**
     * Creates an instance of the AuctionServer
     */
    public static void main(String args[]) { new AuctionServer(); }

    /**
     * An RMI Server used to bind the auctionInterface and the auctionImplementation to the
     * RMI Server, to allow the server and the clients to communicate.
     */
    public AuctionServer() {

        serverPrivateKey= GenerateKeys.getPrivateKey(GenerateKeys.SERVER_KEY);

        // Try Catch block to create RMI service based of the AuctionImpl class
        // Sets the port of the RMI registry to start on, to automatically start the RMI registry
        try {
            LocateRegistry.createRegistry(1099);
            AuctionInterface auctionInterface = new AuctionImpl();
            Naming.rebind("rmi://localhost/AuctionServer", auctionInterface);
            System.out.println("AuctionServer Started");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

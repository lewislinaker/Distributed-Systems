import javax.crypto.SealedObject;
import java.rmi.RemoteException;
import java.security.SignedObject;

/**
 * @Description: An auction interface which is used to implement a dependable auction program
 */

public interface AuctionInterface extends java.rmi.Remote {

    /**
     * Interfaces to be used by the Seller Client. These are used to
     * create and close an auction.
     */
    public String createAuction(String userId, SealedObject sealedAuction) throws RemoteException;
    public String closeAuction(SignedObject signedRequesterId, int auctionId) throws RemoteException;

    /**
     * Interface to be used by the Buyer Client. This is used to
     * allow a bidding client to bid on an auction.
     */
    public String bid(SignedObject bidDetails) throws java.rmi.RemoteException;

    /**
     * Interfaces to be used by both the Seller and BuyerClient.
     * These contain a list of all the auctions. Also allows both
     * clients to send and receive a challenge to and from the server.
     */
    public String getAuctions() throws RemoteException;
    public SealedObject challengeServer(String id, SealedObject challenge) throws RemoteException;
    public boolean answerChallenge(String id, SealedObject response) throws RemoteException;

}

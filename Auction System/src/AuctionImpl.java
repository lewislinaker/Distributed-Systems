import org.jgroups.*;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.mux.MuxRpcDispatcher;
import org.jgroups.util.RspList;

import javax.crypto.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.rmi.RemoteException;

import java.security.*;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class AuctionImpl extends java.rmi.server.UnicastRemoteObject implements AuctionInterface, Receiver {

    /**
     * Private variables used to implement the AuctionInterface
     */
    private JChannel channel;
    private MuxRpcDispatcher dispatcher;
    private HashMap<String, SecretKey> sessionKeys;
    private HashMap<String, ServerChallenge> waitingToSolve;
    private HashMap<String,Boolean> challengesCompleted;
    private AtomicInteger auctionIDCounter;

    /**
     * @Name: AuctionImpl
     * @Description: Method AuctionImpl is used to implement the auction interface.
     */
    public AuctionImpl() throws RemoteException {

        // Variables used to keep track of sessionKeys, keys waiting to be solved, keys solved
        // and an auctionID counter.
        sessionKeys = new HashMap<>();
        waitingToSolve = new HashMap<>();
        challengesCompleted = new HashMap<>();
        auctionIDCounter = new AtomicInteger(1);

        // Try block used to setup a JChannel for the auction server
        try {

            channel = new JChannel("toa.xml");
            channel.setReceiver(this);
            channel.setDiscardOwnMessages(true);
            channel.setName("FrontEnd");
            channel.connect("AuctionServer");
            dispatcher = new MuxRpcDispatcher((short) 1, channel, this, this, this);
            RspList rspList = dispatcher.callRemoteMethods(null, "getIdCounter", new Object[]{}, new Class[]{}, new RequestOptions(ResponseMode.GET_FIRST, 5000));

            // Checks the rsp list for the first counter available
            if (rspList.getFirst() != null) {
                int counter = (int) rspList.getFirst();

                // if the counter is greater than 1 then it increments the counter
                if (counter > 1) {
                    auctionIDCounter.set(counter + 1);
                    System.out.println("Got the counter from a replica " + (counter + 1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Method used to create an auction and keep track of the amount of auction
     * items.
     *
     * @param userID
     * @param sealedAuction
     * @return Successfully added to auction or error message
     * @throws RemoteException
     */
    @Override
    public String createAuction(String userID, SealedObject sealedAuction) throws RemoteException {

        // Sends a message to the server of the user trying to create an auction
        p (userID + " is trying to create an Auction");

        // Checks to see if the user is not authenticated, if not the server will
        // ignore the request and tell the user they are not authenticated.
        if (!sessionKeys.containsKey(userID)) {
            System.out.println(userID + " is not authenticated");
            System.out.println("You are not authenticated");
        }

        // Creates a session key for the auction using the user's userID
        SecretKey sessionKey = sessionKeys.get(userID);
        Auction auction = null;

        // try block to attempt to decrypt the session key
        try {
            Cipher cipher = Cipher.getInstance(sessionKey.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, sessionKey);
            auction = (Auction) sealedAuction.getObject(cipher);

            // Checks to see if the auction ID is available
            if (auction.isIdSet()) {
                System.out.println("The auction has been tempered with, discarded");
            } else {
                auction.setAuctionID(auctionIDCounter.getAndAdd(1));
            }

        } catch (NoSuchAlgorithmException nsa) {
            nsa.printStackTrace();
        } catch (NoSuchPaddingException nsp) {
            nsp.printStackTrace();
        } catch (InvalidKeyException ik) {
            ik.printStackTrace();
        } catch (ClassNotFoundException cnf) {
            cnf.printStackTrace();
        } catch (BadPaddingException bp) {
            System.out.println("Encrypted with a different key");
        } catch (IllegalBlockSizeException ibs) {
            ibs.printStackTrace();
        } catch (IOException io) {
            io.printStackTrace();
        }

        // Try block statement to generate responses from all the members
        try {
            RspList rspList = dispatcher.callRemoteMethods(null, "addAuction", new Object[]{auction}, new Class[]{Auction.class}, new RequestOptions(ResponseMode.GET_ALL, 5000));
            Set<Address> addresses = rspList.keySet();
            Boolean[] answers = new Boolean[rspList.size()];

            for (Address address : addresses) {
                Boolean response = (Boolean) rspList.get(address).getValue();
            }

            // Prints out log crash if the server has crashed
            if (rspList.size() == 0) {
                return logCrash();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Checks to make sure the auction does have an id and then prints out the
        // auction ID
        if (auction != null) {
            return "Successfully added an auction with ID " + auction.getAuctionID();
        } else
            return "An error has occurred";
    }

    /**
     * A method used to close an auction and performs a check to see if the user is the
     * owner of an auction. It returns the winner of the auction if the reserve was met,
     * if the reserve isn't met the auction is closed without a winner.
     *
     * @param signedID
     * @param auctionID
     * @throws RemoteException
     */
    @Override
    public String closeAuction(SignedObject signedID, int auctionID) throws RemoteException {

        try {

            // Variables used to get a list of the auctions and the owners of the current
            // corresponding auction items
            RspList rspList = dispatcher.callRemoteMethods(null, "getAuction", new Object[]{auctionID}, new Class[]{int.class}, new RequestOptions(ResponseMode.GET_FIRST, 5000));
            Auction a = (Auction) rspList.getFirst();
            String trueOwner = a.getOwnerID();

            // Prints out who the owner of the auction is
            System.out.println("Trying to close " + auctionID + " , the true owner is " + trueOwner);
            System.out.println("Trying to get the session key of the user with ID " + trueOwner + " \n " + sessionKeys.keySet());

            // Checks the public key of the owner and checks it against the person
            // trying to close the auction
            PublicKey trueOwnerPublicKey = GenerateKeys.getPublicKey(trueOwner);
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(trueOwnerPublicKey);
            boolean isTheRequesterTheOwner = signedID.verify(trueOwnerPublicKey, signature);
            String requesterID = (String)signedID.getObject();

            // Checks to see if the current user is not authenticated on the server
            if (!challengesCompleted.containsKey(requesterID)) {
                System.out.println("handshake uncompleted, not authenticated");
            } else {
                // Checks to see if the user has not completed the required challenges
                if(!challengesCompleted.get(requesterID)){
                    System.out.println("You have not completed the required challenge");
                }
            }

            // Allows the auction to be closed if the user is the owner of the auction
            if (isTheRequesterTheOwner) {
                System.out.println("You are the true owner, auction closed " + a.getItemDescription());
                RspList responseList = dispatcher.callRemoteMethods(null, "closeAuction", new Object[]{requesterID, auctionID}, new Class[]{String.class, int.class}, new RequestOptions(ResponseMode.GET_ALL, 5000));

                // Checks to see if the server has crashed
                if (rspList.size() == 0) {
                    return logCrash();
                }

                HashMap<String, Integer> majority = new HashMap<>();
                Set<Address> addresses = responseList.keySet();

                for (Address address : addresses) {
                    String response = (String) responseList.get(address).getValue();

                    if (majority.containsKey(response))
                        majority.put(response, majority.get(response) + 1);
                    else
                        majority.put(response, 1);
                }

                String majorityResponse = "";
                int maxShowUps = 0;

                for (String s : majority.keySet()) {
                    if (majority.get(s) > maxShowUps) {
                        maxShowUps = majority.get(s);
                        majorityResponse = s;
                    }
                }
                return majorityResponse;

            } else {
                System.out.println("The user is not the owner");
                return "You are not the owner of this auction";
            }

        } catch (BadPaddingException bp) {
            return "You are not authenticated";
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Unexpected Error has occurred";
    }

    /**
     * Method used to allow the Buyer Client to bid on active items
     *
     * @param auctionDetails
     */
    @Override
    public String bid(SignedObject auctionDetails) throws RemoteException {

        try {

            // variables used to get the details about a given auction object
            Object[] details = (Object[]) auctionDetails.getObject();
            String userID = (String) details[0];
            int auctionID = (int) details[1];
            double amount = (double) details[2];

            // Checks weather or not the user has completed the authentication handshake
            if (!challengesCompleted.containsKey(userID)) {
                System.out.println("handshake not completed, user is not authenticated");
            } else {

                if(!challengesCompleted.get(userID)){
                    System.out.println("You have not yet completed the associated handshake");
                }
            }

            System.out.println("Trying to bid " + amount + " on " + auctionID + " by " + userID);

            // Checks weather or not the user is authenticated using the users public key
            PublicKey requesterPublicKey = GenerateKeys.getPublicKey(userID);
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(requesterPublicKey);
            boolean isTheRequesterVerified = auctionDetails.verify(requesterPublicKey, signature);

            // Checks to see if the request is not verified with the correct public key
            if (!isTheRequesterVerified) {
                System.out.println("Bid unsuccessful, wrong public key");
            }

            RspList responseList = dispatcher.callRemoteMethods(null, "bid", new Object[]{auctionID, amount, userID}, new Class[]{int.class, double.class, String.class}, new RequestOptions(ResponseMode.GET_ALL, 5000));

            // Returns log crash if response from dispatcher is unsuccessful
            if (responseList.size() == 0) {
                return logCrash();
            }

            // Holds the Servers responses
            HashMap<String, Integer> majority = new HashMap<>();
            Set<Address> addresses = responseList.keySet();

            for (Address address : addresses) {
                String response = (String) responseList.get(address).getValue();

                if (majority.containsKey(response)) {
                    majority.put(response, majority.get(response) + 1);
                } else
                    majority.put(response, 1);
            }

            String majorityResponse = "";
            int maxShowUps = 0;

            for (String s : majority.keySet()) {

                if (majority.get(s) > maxShowUps) {
                    maxShowUps = majority.get(s);
                    System.out.println("resp :" + s);
                    majorityResponse = s;
                }
            }

            return majorityResponse;

        } catch (BadPaddingException bp) {
            System.out.println("Stop trying to user spoof, not going work");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Bidding unsuccessful, badly formatted request";
    }

    /**
     * Method used to get a list of all the auctions
     *
     * @throws RemoteException
     */
    @Override
    public String getAuctions() throws RemoteException {

        RspList rspList = null;

        // Try block to try and get a list of all the current auction items
        try {
            rspList = dispatcher.callRemoteMethods(null, "getAuctionList", new Object[]{}, new Class[]{}, new RequestOptions(ResponseMode.GET_FIRST, 5000));
        } catch (Exception e) {
            e.printStackTrace();
        }
        String a = (String) rspList.getFirst();

        return a;
    }

    /**
     * Method used to decrypt a challenge sent by the server and then send
     * a challenge for the server to solve.
     *
     * @param ID
     * @param challenge
     * @return response
     * @throws RemoteException
     */
    @Override
    public SealedObject challengeServer(String ID, SealedObject challenge) throws RemoteException {

        // Used to get the public key of the user ID
        PublicKey challengerPublicKey = GenerateKeys.getPublicKey(ID);

        // If statement in case the public key does not exist
        if (challengerPublicKey == null) {
            System.err.println("Unknown user");

            return null;
        }

        // Stores the answer to the servers challenge in a string, so that the answer can be sent
        // back to the server
        String answerToChallenge;

        // Attempts to solve the challenge sent by the server, if successful then it prints the
        // answer, if not prints out an error message saying the decryption has failed.
        try {
            Cipher decryptCipher = Cipher.getInstance(AuctionServer.serverPrivateKey.getAlgorithm());
            decryptCipher.init(Cipher.DECRYPT_MODE, AuctionServer.serverPrivateKey);
            answerToChallenge = (String) challenge.getObject(decryptCipher);
            System.out.println(answerToChallenge);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Decryption Failed");

            return null;
        }

        // stores solved challenges and challenges that needs to be completed
        ServerChallenge serverChallenge = new ServerChallenge(answerToChallenge);
        waitingToSolve.put(ID, serverChallenge);
        challengesCompleted.put(ID,false);

        // Creates a response with the answer to the challenge, a challenge to send back and a session ID.
        SealedObject response = null;
        try {
            Cipher encryptCipher = Cipher.getInstance(challengerPublicKey.getAlgorithm());
            encryptCipher.init(Cipher.ENCRYPT_MODE, challengerPublicKey);
            response = new SealedObject(serverChallenge, encryptCipher);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
    }

    /**
     * Method used to answer a given challenge
     *
     * @param iD
     * @param response
     * @return true if challenge is answered and false if it hasn't
     * @throws RemoteException
     */
    @Override
    public boolean answerChallenge(String iD, SealedObject response) throws RemoteException {

        // gets the current server challenge that needs to be solved
        ServerChallenge serverChallenge = waitingToSolve.get(iD);

        // Checks to see if the challenge exits
        if (serverChallenge == null) {
            System.out.println("Challenge does not exist");
            return false;
        }

        // try block which attempts to decrypt the server challenge and to get the session
        // key, before storing a response to send back to the server.
        try {
            Cipher decryptCipher = Cipher.getInstance(serverChallenge.getSessionKey().getAlgorithm());
            decryptCipher.init(Cipher.DECRYPT_MODE, serverChallenge.getSessionKey());
            String answer = (String) response.getObject(decryptCipher);

            // if both the server's and client's challenge are successfully resolved, it confirms the
            // client's identity
            if (answer.equals(serverChallenge.getClientChallenge())) {

                System.out.println("Client's identity confirmed");
                challengesCompleted.put(iD,true);
                sessionKeys.put(iD, serverChallenge.getSessionKey());
                waitingToSolve.remove(iD);

                return true;
            }

        } catch (BadPaddingException bp) {
            System.out.println("Another user is trying to solve " + iD + "'s challenge");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Prints out a message
     *
     * @param msg
     */
    public void p(String msg) { System.out.println("AuctionImpl : " + msg); }

    /**
     * Prints out a table view
     * @param view
     */
    @Override
    public void viewAccepted(View view) { System.out.println(view); }

    /**
     * Log crash message when the server is down
     */
    public String logCrash() { return "AuctionServer is down!"; }


   @Override
    public void suspect(Address address) { }

   @Override
   public void block() { }

   @Override
   public void unblock() { }

  @Override
   public void receive(Message message) { }

   @Override
    public void getState(OutputStream outputStream) throws Exception { }

    @Override
    public void setState(InputStream inputStream) throws Exception { }
}


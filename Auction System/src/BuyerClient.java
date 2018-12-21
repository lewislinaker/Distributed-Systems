import javax.crypto.*;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Autthor Lewis Linaker
 * @Description BuyerClient program which allows a buying user to authenticate them self with
 * the server, allows the authenticated user to  bid on the available auctions
 */
public class BuyerClient {

    // Private variables used by the BuyerClient
    AuctionInterface buyerInterface;
    SecretKey sessionKey;
    PublicKey serverPublicKey;
    PrivateKey myPrivateKey;
    private SecureRandom rnd;
    protected String ID;

    /**
     * Main method which calls an instance of the buyerClient
     *
     * @param args
     */
    public static void main(String[] args) {

        try {
            new BuyerClient();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * BuyerClient which is called by main. Used to scan a user ID and authenticate
     * that user ID with the server
     */
    public BuyerClient() {
        // Allows a user ID to be scanned to be authenticated with the server
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your id?");
        ID = scanner.nextLine();

        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Used to call inputDetected method when a user inputs something into the
        // client program.
        System.out.println("1. Bid on an Item: bid (item_id, value)");
        System.out.println("2. List Auctions: list_auctions");

        while (scanner.hasNextLine()) {
            List<String> slittedWord = new ArrayList<String>();
            Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
            Matcher regexMatcher = regex.matcher(scanner.nextLine());

            while (regexMatcher.find()) {
                slittedWord.add(regexMatcher.group().replace("\"", ""));
            }

            inputDetected(slittedWord);
        }
    }

    /**
     * Method used to check the detected input to allow a user to
     * bid or list on all current active auctions
     *
     * @param input
     */
    protected void inputDetected(List<String> input) {

            switch (input.get(0)) {

                // Allows the user to bid on an given item
                case "bid":

                    // Checks to make the sure the correct number of parameters is passed
                    if (input.size() < 3 || input.size() > 3) {
                        System.out.println("Wrong Format");
                        break;
                    }

                    // Checks to make sure that the BidID and Bid Value are numbers
                    if (!isInt(input.get(1)) || !isInt(input.get(2))) {
                        System.out.println("Bid ID and Bid Vale must be a number");
                        break;
                    }

                    // Passes the user input to the server and then generates a suitable response
                    try {
                        String response = bid(Integer.parseInt(input.get(1)), Double.parseDouble(input.get(2)));
                        System.out.println(response);
                    } catch (RemoteException re) {
                        re.printStackTrace();
                    } catch (NumberFormatException nfe) {
                        System.out.println("Wrong Format");
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;

                // Case to allow the user list the current auctions
                case "list_auctions":
                    try {
                        System.out.println(buyerInterface.getAuctions());
                    } catch (RemoteException re) {
                        re.printStackTrace();
                    }
                    break;

                default:
                    System.err.println("No such command");
            }
        }

    /**
     * Method to allow a user to bid on a client.
     *
     * @param auctionID
     * @param value
     * @return buyerInterface.bid(signedDetails)
     * @throws Exception
     */
    public String bid(int auctionID, double value) throws Exception {

        // Sends the bid details, the bidder's private key and the signature tto the server
        Signature signature = Signature.getInstance("SHA1withRSA");
        signature.initSign(myPrivateKey);
        Object[] bidDetails = new Object[]{ID,auctionID, value};
        SignedObject signedDetails = new SignedObject(bidDetails, myPrivateKey, signature);

        return   buyerInterface.bid(signedDetails);
    }

    /**
     * Method to generate and load the required public and private keys
     *
     * @throws Exception
     */
    public void init() throws Exception {

        myPrivateKey = GenerateKeys.getPrivateKey(ID);

        if (myPrivateKey == null) {
            System.out.println("User not registered");
            System.exit(1);
        }

        System.out.println("Loaded your PrivateKey");
        serverPublicKey = GenerateKeys.getPublicKey(GenerateKeys.SERVER_KEY);
        System.out.println("Loaded servers PublicKey");
        buyerInterface = (AuctionInterface) Naming.lookup("rmi://localhost/AuctionServer");
        System.out.println("Connected To The AuctionServer");
        rnd = new SecureRandom();
        handshake();
    }

    /**
     * Method used to do the handshake with the server
     */
    public void handshake() {

        String challenge = new BigInteger(128, rnd).toString(32); //
        System.out.println("Challenge for server " + challenge);

        try {
            Cipher challengeEncryptCipher;
            challengeEncryptCipher = Cipher.getInstance(serverPublicKey.getAlgorithm());
            challengeEncryptCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
            SealedObject sealedChallengeForServer = new SealedObject(challenge, challengeEncryptCipher);
            SealedObject serverAnswer = buyerInterface.challengeServer(ID, sealedChallengeForServer);
            Cipher decryptCipher = Cipher.getInstance(myPrivateKey.getAlgorithm());
            decryptCipher.init(Cipher.DECRYPT_MODE, myPrivateKey);
            ServerChallenge serverChallenge = (ServerChallenge) serverAnswer.getObject(decryptCipher);
            System.out.print("AuctionServer answered: " + serverChallenge.getAnswer() + ", ");

            if (serverChallenge.getAnswer().equals(challenge)) {
                System.out.println("AuctionServer authenticated successfully");
            } else
                return;

            sessionKey = serverChallenge.getSessionKey();
            Cipher answerEncryptCipher = Cipher.getInstance(sessionKey.getAlgorithm());
            answerEncryptCipher.init(Cipher.ENCRYPT_MODE, sessionKey);
            SealedObject sealedAnswer = new SealedObject(serverChallenge.getClientChallenge(), answerEncryptCipher);
            buyerInterface.answerChallenge(ID, sealedAnswer);

        } catch (IOException io) {
            io.printStackTrace();
        } catch (NoSuchAlgorithmException nsa) {
            nsa.printStackTrace();
        } catch (InvalidKeyException ik) {
            ik.printStackTrace();
        } catch (NoSuchPaddingException nsp) {
            nsp.printStackTrace();
        } catch (BadPaddingException bp) {
            System.err.println("AuctionServer authentication failed");
            System.exit(1);
        } catch (ClassNotFoundException cnf) {
            cnf.printStackTrace();
        } catch (IllegalBlockSizeException ibs) {
            ibs.printStackTrace();
        }
    }

    /**
     * Method used to check if an value is an Integer
     *
     * @param s
     * @return false or true
     */
    public boolean isInt(String s) {
        try {
            int i = Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

}

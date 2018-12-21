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
 * @Author Lewis Linaker
 * @Description SellerClient program which allows a seller to authenticate them self
 * with the server. Allows an authenticated user to sell an item.
 */
public class SellerClient {

    // Private variables used by the seller client
    AuctionInterface serverInterface;
    SecretKey sessionKey;
    PublicKey serverPublicKey;
    PrivateKey myPrivateKey;
    private SecureRandom rnd;

    protected String ID;

    /**
     * Main method used to create an instance of the Seller Client
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            new SellerClient();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * SellerClient which is called by main. Used to scan the User ID and
     * authenticate that user ID with the server.
     */
    public SellerClient() {
        // Allows a user ID to be scanned to be authenticated with the server
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your id?");
        ID = scanner.nextLine();

        try {
            init();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Used to call inputDetected when a user inputs something into the
        // client program
        System.out.println("1. Create an Auction:  add_auction (name, Start Price, Reserve Price)");
        System.out.println("2. List Auctions: list_auctions");
        System.out.println("3. End an auction: end_auction (auction ID)");


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
     * Method used to detect user input and allow the user to create, cancel and
     * list all current auctions.
     *
     * @param input
     */
    protected void inputDetected(List<String> input) {

        switch (input.get(0)) {

            // Allows the user to add an auction
            case "add_auction":
                if (input.size() < 3 || input.size() > 4) {
                    System.err.println("Wrong Format");
                    break;
                }

                // Methods to check if the inputs are a number
                if (!isDouble(input.get(2)))
                    System.err.println(input.get(2) + "Start Price needs to be a number");
                else if (input.size() > 3 && !isDouble(input.get(3)))
                    System.err.println(input.get(3) + "Reserve price needs to be a number");
                else {
                    try {

                        // Checks if the reserve is higher or lower than the min price
                        if (input.size() > 3 && Double.parseDouble(input.get(3)) < Double.parseDouble(input.get(2))) {
                            System.err.println("Reserve cannot be lower than start price");
                            break;
                        }

                    } catch (NumberFormatException nfe) {
                        System.out.println("Wrong Format");
                        break;
                    }

                    // Checks to make sure the start price is higher than 0
                    if (input.size() > 3) {
                        try {
                            if (Double.parseDouble(input.get(2)) <= 0) {
                                System.out.println("Start price must be higher than 0");
                                break;
                            }

                            // Prints out a server response
                            String response = addAuction(input.get(1), Double.parseDouble(input.get(2)), Double.parseDouble(input.get(3)));
                            System.out.println(response);

                        } catch (NumberFormatException nfe) {
                            System.out.println("Wrong Format");
                            break;
                        }

                    } else {
                        // If a reserve is not set, set it to the min price
                        try {
                            String response = addAuction(input.get(1), Double.parseDouble(input.get(2)), Double.parseDouble(input.get(2)));
                            System.out.println(response);
                        } catch (NumberFormatException nfe) {
                            System.out.println("Wrong Format");
                            break;
                        }
                    }
                }

                break;

            // Allows an auction to be closed by the correct authenticated user
            case "end_auction":

                // Checks the correct number of values is being passed
                if (input.size() < 2 || input.size() > 2) {
                    System.err.println("Wrong Format");
                    break;
                }

                // Checks if the AuctionID is a number
                if (!isInt(input.get(1))) {
                    System.err.println(input.get(1) + "AuctionID must be a number");
                    break;
                }

                try {
                    String response = cancelAuction(Integer.parseInt(input.get(1)));
                    System.out.println(response);
                    break;
                } catch (NumberFormatException nfe) {
                    System.out.println("Wrong Format");
                    break;
                }

            // Lists all of the current auctions
            case "list_auctions":

                try {
                    System.out.println(serverInterface.getAuctions());
                } catch (RemoteException re) {
                    re.printStackTrace();
                }
                break;

            // Message to display when a command doesn't exist
            default:
                System.err.println("No such command");
        }
    }


    /**
     * Method used to allow a user to add an auction
     *
     * @param itemDescription
     * @param startPrice
     * @param reservePrice
     * @return response
     */
    public String addAuction(String itemDescription, double startPrice, double reservePrice) {

        String response = "No response";

        try {

            // Creates an auction with the itemDescription, the start price, the reserve price
            // and registers the creator of the auction as the auction owner
            Auction auction = new Auction(itemDescription, startPrice, reservePrice, ID);
            Cipher cipher = Cipher.getInstance(sessionKey.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey);
            SealedObject sealedAuction = new SealedObject(auction, cipher);
            response = serverInterface.createAuction(ID, sealedAuction);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
    }

    /**
     * Method used to allow a user to cancel an auction
     *
     * @param auctionId
     * @return response
     */
    public String cancelAuction(int auctionId) {

        String response = "";

        try {

            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(myPrivateKey);
            SignedObject signedId = new SignedObject(ID, myPrivateKey, signature);
            response = "Trying to cancel auction with ID " + auctionId + serverInterface.closeAuction(signedId, auctionId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
    }


    /**
     * Method to generate the required Public and Private Keys by an instance of
     * an client
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
        serverInterface = (AuctionInterface) Naming.lookup("rmi://localhost/AuctionServer");
        System.out.println("Connected To The AuctionServer");
        rnd = new SecureRandom();
        handshake();
    }

    /**
     * Method to do an handshake between an instance of the SellerClient and
     * the server
     */
    public void handshake() {

        String challenge = new BigInteger(128, rnd).toString(32); //
        System.out.println("Challenge for server " + challenge);

        try {
            Cipher challengeEncryptCipher = Cipher.getInstance(serverPublicKey.getAlgorithm());
            challengeEncryptCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
            SealedObject sealedChallengeForServer = new SealedObject(challenge, challengeEncryptCipher);
            SealedObject serverAnswer = serverInterface.challengeServer(ID, sealedChallengeForServer);
            Cipher decryptCipher = Cipher.getInstance(myPrivateKey.getAlgorithm());
            decryptCipher.init(Cipher.DECRYPT_MODE, myPrivateKey);
            ServerChallenge serverChallenge = (ServerChallenge) serverAnswer.getObject(decryptCipher);
            System.out.print("AuctionServer answered: " + serverChallenge.getAnswer() + ", ");

            if (serverChallenge.getAnswer().equals(challenge)) {
                System.out.println("AuctionServer authentication successful");
            } else
                return;

            sessionKey = serverChallenge.getSessionKey();
            Cipher answerEncryptCipher = Cipher.getInstance(sessionKey.getAlgorithm());
            answerEncryptCipher.init(Cipher.ENCRYPT_MODE, sessionKey);
            SealedObject sealedAnswer = new SealedObject(serverChallenge.getClientChallenge(), answerEncryptCipher);
            serverInterface.answerChallenge(ID, sealedAnswer);

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
     * Method to check if the user input is a double
     *
     * @param s
     * @return boolean false or true
     */
    public boolean isDouble(String s) {
        try {
            double d = Double.parseDouble(s);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    /**
     * Method to check is the user input is an Integer
     *
     * @param s
     * @return boolean true or false
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

import java.io.Serializable;

public class Auction implements Serializable {

   // Private variables used to create and describe an auction item
    private Integer auctionID;

    private String currentWinner;
    private String itemDescription;
    private String ownerID;
    private String history = "";

    private double price;
    private double reserve;
    private double currentBid;

    private boolean won;
    private boolean auctionClosed =  false;

    /**
     *
     * @param itemDescription
     * @param price
     * @param reserve
     * @param ownerID
     *
     * The requirements to make an auction
     */
    public Auction(String itemDescription, double price, double reserve, String ownerID) {

        this.ownerID = ownerID;
        this.price = price;
        this.reserve = reserve;
        this.currentBid = price;
        this.itemDescription = itemDescription;
    }

    /**
     * @return currentWinner
     */
    public String getCurrentWinner() { return currentWinner; }

    /**
     * @return itemDescription
     */
    public String getItemDescription() { return itemDescription; }

    /**
     * @return getOwnerID
     */
    public String getOwnerID() { return ownerID; }

    /**
     * @return getCurrentBid
     */
    public double getCurrentBid() { return currentBid; }

    /**
     * @return getAuctionID
     */
    public int getAuctionID() { return auctionID; }


    /**
     * Set method for auctionID
     * @param auctionID
     */
    public void setAuctionID(int auctionID) { this.auctionID = auctionID; }

    /**
     * @param value
     * @param bidderID
     *
     * Method used to a place a bid on an an item and performs various
     * checks on the bis.
     */
    public String bid(double value, String bidderID) {

        // Performs a check to see if the auction is closed or open
        if (auctionClosed) {
            System.out.println("The auction is closed");
        }

        // Performs a check to see if the attempted bid is higher than the current highest bid
        if (value <= currentBid) {
            System.out.println("Your bid must be higher than the current bid");
        }

        currentBid = value;
        currentWinner = bidderID;
        history = bidderID + " bid " + value + " on " + itemDescription + "\n" + history;

        return "Successful bid of " + value + " on " + itemDescription + " you are currently the highest bidder";
    }

    // Checks if an auctionID is taken
    public boolean isIdSet() { return auctionID != null; }

    // Checks to see if an auction is closed
    public boolean isAuctionClosed() { return auctionClosed; }

    // Checks to see if an auction is won
    public boolean isWon() { return won; }

    /**
     *
     * @param ownerID
     *
     * Method used to close an item and check weather the client attempting to close
     * the auction is the owner of the auction
     */
    public String closeAuction (String ownerID) {

        String winnerName;

        System.err.println(ownerID + " @ " + this.ownerID + "  = " + ownerID.equals(this.ownerID));

        // Checks to see if the client is not the owner of the auction
        if(!ownerID.equals(this.ownerID)) {
            System.out.println("You are not the owner of this auction");
        }

        // Checks to see if the auction is already closed
        if(auctionClosed) {
            System.out.println("The auction is already closed");
        }
        auctionClosed = true;

        // Checks to see if the highest bid is greater than the reserve price, if so
        // sets the winnerName to the highest bidder
        if (currentBid > reserve) {
            winnerName = currentWinner;
            won = true;

            return  "Auction won by " + winnerName + " with the highest bid of" + currentBid;

        } else {
            winnerName = "-1";
            won = false;
            return "Auction closed with no winner ";
        }
    }
}


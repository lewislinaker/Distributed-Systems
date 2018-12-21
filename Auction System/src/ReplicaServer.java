import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.blocks.mux.MuxRpcDispatcher;
import org.jgroups.util.Util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * @Author Lewis Linaker
 * @Descripon: ReplicaServer class which contains backup information in case od a server crash
 */
public class ReplicaServer extends ReceiverAdapter {

    // private variables used by the ReplicaServer
    private JChannel channel;
    private RpcDispatcher dispatcher;
    HashMap<Integer,Auction> state = new HashMap<>();
    int counter;
    String replicaID;

    /**
     * Main Class which calls an instance of the replica
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception { new ReplicaServer(); }

    /**
     * ReplicaServer class used to stored a replica of the JChannels which are used
     * within the server
     */
    public ReplicaServer() throws Exception {

        // variables used by the replicaServer
        channel = new JChannel("toa.xml");
        channel.setReceiver(this);
        Random rnd = new Random();
        replicaID = "Replica_" +  rnd.nextInt(10000);
        channel.setName(replicaID);
        channel.connect("AuctionServer");
        View view = channel.getView();
        List<Address> addresses = view.getMembers();

        // Try's to get the state of an channel
        for(Address address : addresses) {

            try {
                channel.getState(address,5000);
            } catch (Exception e) {
                System.err.println("Cannot get state from: " + address.toString());
                continue;
            }

            if(address == channel.getAddress()){
                System.out.println("First ReplicaServer");
            } else {
                System.out.println("Got state from "  +address.toString());
                System.out.println(getAuctionList());
            }

            break;
        }

        dispatcher = new MuxRpcDispatcher((short)1,channel,this,this,this);
        p("Started successfully!");
    }

    /**
     * Adds an auction item to the current auction listings
     *
     * @param auction
     * @return true
     */
    public boolean addAuction(Auction auction){

        counter = auction.getAuctionID();
        p("Trying to add auction");
        state.put(auction.getAuctionID(), auction);
        p("Auction "+ auction.getItemDescription() + " successfully added with ID " + auction.getAuctionID() + " By " + auction.getOwnerID());

        return true;
    }

    /**
     * Method to allow a bid to be placed on an auction
     *
     * @param auctionID
     * @param value
     * @param bidderID
     * @return auctionBid or No Such Auction Exits
     */
    public String bid (int auctionID,double value, String bidderID) {

        if(state.containsKey(auctionID)) {

            return state.get(auctionID).bid(value, bidderID);
        } else {

            return "No such auction exists";
        }
    }

    /**
     * Method to allow an auction to be closed
     *
     * @param clientID
     * @param auctionID
     * @return auction Closed or No Such Auction Exists
     */
    public String closeAuction(String clientID, int auctionID) {

        if(state.containsKey(auctionID)) {
            return state.get(auctionID).closeAuction(clientID);
        } else {
            return "No such auction exists";
        }
    }

    /**
     * @return counter
     */
    public int getIdCounter() { return counter; }

    /**
     * Method used to print out the auction list when requested
     * @return
     */
    public String getAuctionList(){

        String response =  String.format ("%-15s %-20s %-10s %-10s %-15s %-20s %n",
                "Auction ID","Item Name","Closed","Won","Highest Bid","Highest Bidder");
        for (int i : state.keySet()) {

            Auction auction = state.get(i);
            String line = String.format("%-15s %-20s %-10s %-10s %-15s %-20s %n",
                    auction.getAuctionID(),auction.getItemDescription(),auction.isAuctionClosed(),auction.isWon(),auction.getCurrentBid(),auction.getCurrentWinner());
            response += line;
        }

        return response;
    }

    /**
     * Method to get the state of an auction
     *
     * @param auctionID
     * @return state.get(auctionID) or null
     */
    public Auction getAuction(int auctionID ){

        if(state.containsKey(auctionID)) {

            return state.get(auctionID);
        } else
            return null;
    }

    /**
     * Method to get the state of an object
     *
     * @param output
     * @throws Exception
     */
    @Override
    public void getState(OutputStream output) throws Exception {

        synchronized (state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }

    /**
     * Method to set the state of an Object
     *
     * @param input
     * @throws Exception
     */
    @Override
    public void setState(InputStream input) throws Exception {

        HashMap<Integer,Auction> hm = (HashMap) Util.objectFromStream(new DataInputStream(input));

        synchronized (state) {
            state.clear();
            state.putAll(hm);
        }
        p("Done getting the state!");
    }

    /**
     * Method used to get a view of the auction
     * @param view
     */
    @Override
    public void viewAccepted(View view) { System.out.println(view.toString()); }

    public void p(String msg) { System.out.println("> " + replicaID + " : "  +msg); }
}


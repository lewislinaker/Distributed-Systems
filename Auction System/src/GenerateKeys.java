import java.io.*;
import java.security.*;

/**
 * @Author Lewis Linaker
 * @Description GenerateKeys class used to generate Public and Private Keys
 * for the Server and the Clients
 */
public class GenerateKeys {

    /**
     * Class used to generate Public and Private Keys for both the clients
     * and server
     */
    public final static String SERVER_KEY ="SERVER";

    public static void main(String[] args) {

        // A list of users to create keys for
        createKey(SERVER_KEY);
        createKey("lewis");
        createKey("zw");
        createKey("ye");
    }

    /**
     * Method used to generate key pairs
     *
     * @param userName
     */
    public static void createKey(String userName) {

        try {

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(4096);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            try {
                saveKeys(keyPair,userName);
            } catch (IOException io) {
                io.printStackTrace();
            }
        } catch (NoSuchAlgorithmException nsa) {
            nsa.printStackTrace();
        }
    }

    /**
     * Method used to save generated keys to a file
     *
     * @param keyPair
     * @param userID
     * @throws IOException
     */
    private static void saveKeys(KeyPair keyPair, String userID) throws IOException {

        File file = new File("./Keys/"+userID);
        System.out.println("Does key user already exist?: " + !file.mkdirs());

        FileOutputStream fos = new FileOutputStream(file.getAbsolutePath()+ "/PublicKey.key");
        ObjectOutputStream publicKeyObjectStream = new ObjectOutputStream(fos);
        publicKeyObjectStream.writeObject(keyPair.getPublic());

        fos = new FileOutputStream(file.getAbsolutePath()+"/PrivateKey.key");
        ObjectOutputStream privateKeyObjectStream = new ObjectOutputStream(fos);
        privateKeyObjectStream.writeObject(keyPair.getPrivate());

        fos.close();
    }

    /**
     * Method used to getPrivateKey when required either by a user
     * or by the server
     *
     * @param id
     * @return privateKey
     */
    public static PrivateKey getPrivateKey(String id) {

        System.out.println("KEyUtil: trying to get private key for " + id);
        PrivateKey privateKey = null;

        try {
            FileInputStream fileInputStream = new FileInputStream("./Keys/"+id + "/PrivateKey.key");
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            privateKey = (PrivateKey)objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
        } catch (FileNotFoundException fnf) {
            return null;
        } catch (ClassNotFoundException cnf) {
            cnf.printStackTrace();
        } catch (IOException io) {
            io.printStackTrace();
        }

        return privateKey;
    }

    /**
     * Method used to getPublicKey when required either by a user or by
     * a server
     *
     * @param id
     * @return publicKey
     */
    public static PublicKey getPublicKey(String id) {

        PublicKey publicKey = null;

        try {
            FileInputStream fileInputStream = new FileInputStream("./Keys/"+id + "/PublicKey.key");
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            publicKey = (PublicKey)objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
        } catch (FileNotFoundException fnf) {
            return null;
        } catch (ClassNotFoundException cnf) {
            cnf.printStackTrace();
        } catch (IOException io) {
            io.printStackTrace();
        }

        return publicKey;
    }

}


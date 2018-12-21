import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class ServerChallenge implements Serializable {

    // Private Variables used to generate challenge answers and the
    // challenge for the client
    private String answer;
    private String clientChallenge;
    private SecretKey sessionKey;

    /**
     * Method used to generate a session key
     *
     * @param answer
     */
    public ServerChallenge(String answer) {

        this.answer = answer;
        SecureRandom rnd = new SecureRandom();
        this.clientChallenge =  new BigInteger(64,rnd).toString(32);
        KeyGenerator keyGen = null;

        try {
            keyGen = KeyGenerator.getInstance("DES");
        } catch (NoSuchAlgorithmException nsa) {
            nsa.printStackTrace();
        }

        keyGen.init(56);
        sessionKey= keyGen.generateKey();
    }

    // get methods for the private variables
    public String getAnswer() { return answer; }
    public String getClientChallenge() { return clientChallenge; }
    public SecretKey getSessionKey() { return sessionKey; }
}
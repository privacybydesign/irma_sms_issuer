package foundation.privacybydesign.sms.smssender;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import foundation.privacybydesign.sms.SMSConfiguration;

import javax.ws.rs.InternalServerErrorException;
import java.io.*;
import java.net.URL;

/**
 * Send a SMS token to a phone using REST, over a SSH tunnel.
 */
public class SSHTunnelRESTSender extends Sender {

    public void send(String phone, String token) throws IOException {
        SMSConfiguration conf = SMSConfiguration.getInstance();

        byte[] out = getMessage(phone, token);

        try {
            // Set up the HTTP connection
            JSch jsch = new JSch();
            String knownHosts = conf.getSMSSenderHost() + " ssh-rsa " + conf.getSMSSenderHostRsaKey();
            jsch.setKnownHosts(new ByteArrayInputStream(knownHosts.getBytes()));
            // Unfortunately, JSch doesn't support ed25519 keys.
            // https://sourceforge.net/p/jsch/feature-requests/7/
            jsch.addIdentity(conf.getSMSSenderKeyPath(), conf.getSMSSenderKeyPassphrase());
            Session session = jsch.getSession(conf.getSMSSenderUser(), conf.getSMSSenderHost());
            session.connect();

            PipedInputStream chanIs = new PipedInputStream();
            PipedOutputStream os = new PipedOutputStream(chanIs);
            PipedOutputStream chanOs = new PipedOutputStream();
            PipedInputStream is = new PipedInputStream(chanOs);

            // Open a socket on the remote host, like ssh -L
            URL url = new URL(conf.getSMSSenderAddress());
            Channel chan = session.getStreamForwarder(url.getHost(), url.getPort());
            chan.setInputStream(chanIs);
            chan.setOutputStream(chanOs);
            chan.connect();

            // Send the HTTP request
            // TODO use a real HTTP client
            os.write(("POST " + url.getPath() + " HTTP/1.1\r\n").getBytes());
            os.write(("Content-Type: application/x-www-form-urlencoded; charset=UTF-8\r\n").getBytes());
            os.write(("Content-Length: " + out.length + "\r\n").getBytes());
            os.write(("Host: " + url.getHost() + "\r\n").getBytes());
            os.write("\r\n".getBytes());
            os.write(out);

            // Read (part of) the HTTP response
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line = bis.readLine();
            if (line == null) {
                throw new IOException("EOF has been reached before HTTP response could be read");
            }
            String[] parts = line.split(" +", 3);
            if (parts.length < 3) {
                throw new IOException("HTTP error: invalid response");
            }
            if (!(parts[0].equals("HTTP/1.1") || parts[0].equals("HTTP/1.0"))) {
                throw new IOException("HTTP error: HTTP version");
            }
            if (!parts[1].equals("200")) {
                throw new IOException("HTTP error: " + parts[1]);
            }
            os.close();
            is.close();
            chan.disconnect();
            session.disconnect();
        } catch (java.net.MalformedURLException e) {
            throw new InternalServerErrorException("cannot parse configured URL");
        } catch (JSchException e) {
            throw new IOException("JSch error: " + e.getMessage());
        }
    }
}

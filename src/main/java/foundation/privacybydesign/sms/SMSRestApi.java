package foundation.privacybydesign.sms;

import foundation.privacybydesign.sms.ratelimit.MemoryRateLimit;
import foundation.privacybydesign.sms.ratelimit.RateLimit;
import org.irmacard.api.common.ApiClient;
import org.irmacard.api.common.CredentialRequest;
import org.irmacard.api.common.issuing.IdentityProviderRequest;
import org.irmacard.api.common.issuing.IssuingRequest;
import org.irmacard.credentials.info.CredentialIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API for use by the web client.
 */
@Path("")
public class SMSRestApi {
    private static final String ERR_ADDRESS_MALFORMED = "error:address-malformed";
    private static final String ERR_CANNOT_VALIDATE = "error:cannot-validate-token";
    private static final String ERR_RATE_LIMITED = "error:ratelimit";
    private static final String ERR_INTERNAL = "error:internal";
    private static final String ERR_SENDING_SMS = "error:sending-sms";
    private static final String OK_RESPONSE = "OK"; // value doesn't really matter

    // pattern made package-private for testing
    static final String PHONE_PATTERN = "(0|\\+31|0031)6[1-9][0-9]{7}";

    RateLimit rateLimiter;
    private static final Logger logger = LoggerFactory.getLogger(SMSRestApi.class);

    public SMSRestApi() {
        rateLimiter = MemoryRateLimit.getInstance();
    }

    @POST
    @Path("send-sms-token")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendSmsCode(@Context HttpServletRequest req,
                                @FormParam("phone") String phone) {
        if (!phone.matches(PHONE_PATTERN)) {
            return Response.status(Response.Status.BAD_REQUEST).entity
                    (ERR_ADDRESS_MALFORMED).build();
        }

        long retryAfter = rateLimiter.rateLimited(req.getRemoteAddr(), phone);
        if (retryAfter > 0) {
            // 429 Too Many Requests
            // https://tools.ietf.org/html/rfc6585#section-4
            return Response.status(429)
                    .entity(ERR_RATE_LIMITED)
                    .header("Retry-After", (int) Math.ceil(retryAfter / 1000.0))
                    .build();
        }

        // TODO: use canonical phone number
        String token = TokenManager.getInstance().generate(phone, req.getRemoteAddr());

        // Send the SMS token
        // https://stackoverflow.com/a/35013372/559350
        // TODO abstract this away
        Map<String, String> arguments = new HashMap<>();
        // TODO: use the normalized phone number
        arguments.put("number", phone);
        // TODO: add URL to verify on the phone itself (if possible in 160
        // chars)
        arguments.put("message", "IRMA verify token: " + token);
        arguments.put("token", SMSConfiguration.getInstance().getSMSSenderToken());
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            try {
                if (builder.length() != 0) {
                    builder.append("&");
                }
                builder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                builder.append("=");
                builder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                // unreachable
                throw new RuntimeException("Invalid encoding?");
            }
        }
        byte[] out = builder.toString().getBytes(StandardCharsets.UTF_8);

        String senderAddress = SMSConfiguration.getInstance().getSMSSenderAddress();
        OutputStream os = null;
        try {
            URL url = new URL(senderAddress);
            URLConnection con = url.openConnection();

            HttpURLConnection http = (HttpURLConnection) con;
            try {
                http.setRequestMethod("POST");
            } catch (ProtocolException e) {
                // unreachable
                throw new RuntimeException("cannot set POST as request method?");
            }
            http.setDoOutput(true);

            http.setFixedLengthStreamingMode(out.length);
            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            http.connect();
            os = http.getOutputStream();
            os.write(out);
        } catch (MalformedURLException e) {
            if (senderAddress.length() == 0) {
                logger.error("Empty REST URL for SMS API");
            } else {
                logger.error("Invalid REST URL for SMS API: " + senderAddress);
            }
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ERR_INTERNAL).build();
        } catch (IOException e) {
            logger.error("Failed to send SMS: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ERR_SENDING_SMS).build();
        } finally {
            if (os != null) {
                // The connection was opened.
                try {
                    // Try to close it.
                    os.close();
                } catch (IOException e) {
                    // We did our best, but if it fails, just give up...
                }
            }
        }

        return Response.status(Response.Status.OK).entity(OK_RESPONSE).build();
    }

    @POST
    @Path("verify-sms-token")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendSmsCode(@Context HttpServletRequest req,
                                @FormParam("phone") String phone,
                                @FormParam("token") String token)
            throws KeyManagementException {
        SMSConfiguration conf = SMSConfiguration.getInstance();

        // TODO use canonical phone number
        if (!TokenManager.getInstance().verify(phone, req.getRemoteAddr(), token)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ERR_CANNOT_VALIDATE).build();
        }
        // The phone number is validated. Now build the issuing JWT.

        ArrayList<CredentialRequest> credentials = new ArrayList<>(1);
        HashMap<String, String> attrs = new HashMap<>(1);
        attrs.put(conf.getSMSAttribute(), phone);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        credentials.add(new CredentialRequest((int)CredentialRequest
                .floorValidityDate(calendar.getTimeInMillis(), true),
                new CredentialIdentifier(
                        conf.getSchemeManager(),
                        conf.getSMSIssuer(),
                        conf.getSMSCredential()
                ),
                attrs
        ));

        IdentityProviderRequest ipRequest = new IdentityProviderRequest("",
                new IssuingRequest(null, null, credentials), 120);
        String jwt = ApiClient.getSignedIssuingJWT(ipRequest,
                conf.getServerName(),
                conf.getHumanReadableName(),
                conf.getJwtAlgorithm(),
                conf.getPrivateKey());
        return Response.status(Response.Status.OK).entity(jwt).build();
    }
}

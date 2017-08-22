package foundation.privacybydesign.sms;

import foundation.privacybydesign.sms.ratelimit.InvalidPhoneNumberException;
import foundation.privacybydesign.sms.ratelimit.MemoryRateLimit;
import foundation.privacybydesign.sms.ratelimit.RateLimit;
import foundation.privacybydesign.sms.smssender.SSHTunnelRESTSender;
import foundation.privacybydesign.sms.smssender.Sender;
import foundation.privacybydesign.sms.smssender.SimpleRESTSender;
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
import java.security.KeyManagementException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

/**
 * REST API for use by the web client.
 */
@Path("")
public class SMSRestApi {
    private static final String ERR_ADDRESS_MALFORMED = "error:address-malformed";
    private static final String ERR_CANNOT_VALIDATE = "error:cannot-validate-token";
    private static final String ERR_RATE_LIMITED = "error:ratelimit";
    private static final String ERR_SENDING_SMS = "error:sending-sms";
    private static final String OK_RESPONSE = "OK"; // value doesn't really matter

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
        try {
            phone = RateLimit.canonicalPhoneNumber(phone);

            long retryAfter = rateLimiter.rateLimited(req.getRemoteAddr(), phone);
            if (retryAfter > 0) {
                // 429 Too Many Requests
                // https://tools.ietf.org/html/rfc6585#section-4
                return Response.status(429)
                        .entity(ERR_RATE_LIMITED)
                        .header("Retry-After", (int) Math.ceil(retryAfter / 1000.0))
                        .build();
            }
        } catch (InvalidPhoneNumberException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ERR_ADDRESS_MALFORMED).build();
        }

        String token = TokenManager.getInstance().generate(phone, req.getRemoteAddr());

        Sender sender;
        switch (SMSConfiguration.getInstance().getSMSSenderBackend()) {
            case "rest":
                sender = new SimpleRESTSender();
                break;
            case "ssh-rest":
                sender = new SSHTunnelRESTSender();
                break;
            default:
                throw new RuntimeException("Unknown SMS sender backend");
        }
        try {
            sender.send(phone, token);
        } catch (IOException e) {
            logger.error("Failed to send SMS: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ERR_SENDING_SMS).build();
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

        try {
            phone = RateLimit.canonicalPhoneNumber(phone);
        } catch (InvalidPhoneNumberException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ERR_ADDRESS_MALFORMED).build();
        }

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

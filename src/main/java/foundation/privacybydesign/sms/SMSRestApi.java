package foundation.privacybydesign.sms;

import foundation.privacybydesign.sms.ratelimit.MemoryRateLimit;
import foundation.privacybydesign.sms.ratelimit.RateLimit;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST API for use by the web client.
 */
@Path("")
public class SMSRestApi {
    private static final String ERR_ADDRESS_MALFORMED = "error:address-malformed";
    private static final String ERR_RATE_LIMITED = "error:ratelimit";
    private static final String OK_RESPONSE = "OK"; // value doesn't really matter

    // made public for testing
    public static final String PHONE_PATTERN = "(0|\\+31|0031)6[1-9][0-9]{7}";

    RateLimit rateLimiter;

    public SMSRestApi() {
        rateLimiter = MemoryRateLimit.getInstance();
    }

    @POST
    @Path("send-sms-code")
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
                    .header("Retry-After", (int)Math.ceil(retryAfter/1000.0))
                    .build();
        }

        System.out.println("phone number: " + phone);
        return Response.status(Response.Status.OK).entity
                (OK_RESPONSE).build();
    }
}

package foundation.privacybydesign.sms;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * REST API for use by the web client.
 */
@Path("")
public class SMSRestApi {
    private static final String OK_RESPONSE = "OK"; // value doesn't really matter

    @POST
    @Path("send-sms-code")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendSmsCode(@FormParam("phone") String phone) {
        System.out.println("phone number: " + phone);
        return Response.status(Response.Status.OK).entity
                (OK_RESPONSE).build();
    }
}

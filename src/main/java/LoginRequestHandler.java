import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;

public class LoginRequestHandler implements HttpRequestHandler {
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        if (!(request instanceof HttpEntityEnclosingRequest)) {
            // FIXME: update to align with api spec
            System.out.println("Failed to cast request into HttpEntityEnclosingRequest");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
        HttpEntityEnclosingRequest entityEnclosingRequest = (HttpEntityEnclosingRequest) request;
        HttpEntity entity = entityEnclosingRequest.getEntity();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(entity.getContent());
        String username = root.get("username").asText();
        String password = root.get("password").asText();
        if (username.isEmpty() || password.isEmpty()) {
            // FIXME: update to align with api spec
            System.out.println("Username or Password empty");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
        System.out.println("Username: "+ username + " Password: " + password);
        String token;
        try {
            token = DbHelper.getInstance().signIn(username, password);
        } catch (DbHelper.SignInConflictException e) {
            // FIXME: update to align with api spec
            System.out.println("Duplicate signin");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_CONFLICT);
            return;
        } catch (DbHelper.SignInBadRequestException e) {
            // FIXME: update to align with api spec
            System.out.println("Incorrect username or pw");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setStatusCode(HttpStatus.SC_OK);
        StringEntity body = new StringEntity(
        "{\"Token\": \""+ token +"\"}",
            ContentType.APPLICATION_JSON);
        response.setEntity(body);
    }
}

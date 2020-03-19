import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;

public class LoginRequestHandler implements HttpRequestHandler {
     public static class RequestBody {
        @JsonProperty("Username")
        public String username;

        @JsonProperty("Password")
        public String password;

        RequestBody() {}

        RequestBody(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @JsonIgnore
        public boolean isEmpty() {
            return this.username == null || this.username.isEmpty() || this.password == null || this.password.isEmpty();
        }
    }
    public static class ResponseBody {
        @JsonProperty("Token")
        public String token;

        ResponseBody() {}

        ResponseBody(String token) {
            this.token = token;
        }
    }
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        RequestBody requestBody;
        try {
            requestBody = ParsingHelper.parseRequestBody(request, RequestBody.class);
        } catch (Exception e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
        if (requestBody == null || requestBody.isEmpty()) {
            // FIXME: update to align with api spec
            System.out.println("Username or Password empty");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        try {
            response.setStatusCode(HttpStatus.SC_OK);
            ResponseBody responseBody = new ResponseBody(DbHelper.getInstance().signIn(requestBody.username, requestBody.password));
            ObjectMapper mapper = new ObjectMapper();
            StringEntity body = new StringEntity(
                mapper.writeValueAsString(responseBody),
                ContentType.APPLICATION_JSON
            );
            response.setEntity(body);
        } catch (DbHelper.SignInConflictException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_CONFLICT);
        } catch (DbHelper.SignInBadRequestException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}

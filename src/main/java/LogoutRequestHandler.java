import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;

public class LogoutRequestHandler implements HttpRequestHandler {
    public static class RequestBody {
        @JsonProperty("Token")
        public String token;

        RequestBody() {}

        RequestBody(String token) {
            this.token = token;
        }

        @JsonIgnore
        public boolean isEmpty() {
            return this.token == null || this.token.isEmpty();
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
        if (requestBody.isEmpty()) {
            // FIXME: update to align with api spec
            System.out.println("Token empty");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
        try {
            if (DbHelper.getInstance().signOut(requestBody.token)) {
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
            } else { // should not happen
                System.out.println("this should not happen: returning logout false without exception");
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (DbHelper.SignOutBadRequestException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}

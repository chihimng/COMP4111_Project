import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;

public class BooksRequestHandler implements HttpRequestHandler {
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
        // TODO: validate token
        if (request.getRequestLine().getMethod().equals("POST")) {
            handleCreate(request, response, context);
        }
    }

    public void handleCreate(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        if (!(request instanceof HttpEntityEnclosingRequest)) {
            // FIXME: update to align with api spec
            System.out.println("Failed to cast request into HttpEntityEnclosingRequest");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
        HttpEntityEnclosingRequest entityEnclosingRequest = (HttpEntityEnclosingRequest) request;
        HttpEntity entity = entityEnclosingRequest.getEntity();
        ObjectMapper mapper = new ObjectMapper();
        Book requestBody;
        try {
            requestBody = mapper.readValue(entity.getContent(), Book.class);
        } catch (Exception e) { // parsing failed
            // FIXME: update to align with api spec
            e.printStackTrace();
            System.out.println("Body parsing failed");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
        if (requestBody == null || !requestBody.isValid()) {
            // FIXME: update to align with api spec
            System.out.println("Book data invalid");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        try {
            response.setStatusCode(HttpStatus.SC_CREATED);
            int id = DbHelper.getInstance().createBook(requestBody);
            response.setHeader(HttpHeaders.LOCATION, "/books/" + id);
        } catch (DbHelper.CreateBookConflictException e) {
            // TODO: get book id and return location
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_CONFLICT);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}

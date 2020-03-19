import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        switch (request.getRequestLine().getMethod()) {
            case "POST":
                handleCreate(request, response, context);
                break;
            case "GET":
                handleSearch(request, response, context);
                break;
            case "PUT":
                handleAvailability(request, response, context);
                break;
            case "DELETE":
                handleDeletion(request, response, context);
                break;
            default:
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }

    public void handleCreate(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        Book requestBody;
        try {
            requestBody = ParsingHelper.parseRequestBody(request, Book.class);
        } catch (Exception e) {
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
            int id = -1;
            try {
                id = DbHelper.getInstance().findDuplicateBook(requestBody);
            } catch (Exception e1) {
                // error while finding duplicate
                e1.printStackTrace();
                // FIXME: update to align with api spec
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_CONFLICT);
            response.setHeader(HttpHeaders.LOCATION, "/books/" + id);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    public static class SearchResponseBody {
        @JsonProperty("FoundBooks")
        public int foundBooks() {
            if (this.results == null) return 0;
            return this.results.size();
        }
        @JsonProperty("Results")
        public List<Book> results;
        SearchResponseBody() {}

        SearchResponseBody(List<Book> results) {
            this.results = results;
        }
    }

    public void handleSearch(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        Map<String, String> param = new HashMap<String, String>();

        URI uri = URI.create(request.getRequestLine().getUri());
        String query = uri.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] split = pair.split("=");
                if (split.length >= 2) {
                    param.put(URLDecoder.decode(split[0], "UTF-8"), URLDecoder.decode(split[1], "UTF-8"));
                }
            }
        }

        try {
            response.setStatusCode(HttpStatus.SC_CREATED);
            List<Book> books = DbHelper.getInstance().searchBook(param.get("id"), param.get("title"), param.get("author"), param.get("sort"), param.get("order"), param.get("limit"));
            response.setStatusCode(HttpStatus.SC_OK);
            SearchResponseBody responseBody = new SearchResponseBody(books);
            ObjectMapper mapper = new ObjectMapper();
            StringEntity body = new StringEntity(
                mapper.writeValueAsString(responseBody),
                ContentType.APPLICATION_JSON
            );
            response.setEntity(body);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public static class AvailabilityRequestBody {
        @JsonProperty("Available")
        public boolean isAvailable;

        AvailabilityRequestBody() {}

        AvailabilityRequestBody(Boolean isAvailable) {
            this.isAvailable = isAvailable;
        }
    }

    public void handleAvailability(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        AvailabilityRequestBody requestBody;
        try {
            requestBody = ParsingHelper.parseRequestBody(request, AvailabilityRequestBody.class);
        } catch (Exception e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
        if (requestBody == null) {
            // FIXME: update to align with api spec
            System.out.println("Availability of book not set");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        try {
            URI uri = URI.create(request.getRequestLine().getUri());
            String path = uri.getPath();
            String idStr = path.substring(path.lastIndexOf('/') + 1);
            int id = Integer.parseInt(idStr);
            DbHelper.getInstance().modifyBookAvailability(id, requestBody.isAvailable);
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
        } catch (NumberFormatException e) {
            System.out.println("Unable to parse number");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (DbHelper.ModifyBookNotFoundException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND, "No book record");
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void handleDeletion(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        try {
            URI uri = URI.create(request.getRequestLine().getUri());
            String path = uri.getPath();
            String idStr = path.substring(path.lastIndexOf('/') + 1);
            int id = Integer.parseInt(idStr);
            DbHelper.getInstance().deleteBook(id);
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
        } catch (NumberFormatException e) {
            System.out.println("Unable to parse number");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (DbHelper.DeleteBookNotFoundException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND, "No book record");
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}

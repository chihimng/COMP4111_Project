import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.protocol.*;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class BooksRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {
    public static class ResponseBody {
        @JsonProperty("Token")
        public String token;

        ResponseBody() {}

        ResponseBody(String token) {
            this.token = token;
        }
    }

    @Override
    public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {
        // Buffer request content in memory for simplicity
        return new BasicAsyncRequestConsumer();
    }

    @Override
    public void handle(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
        new Thread(() -> {
            HttpResponse response = httpExchange.getResponse();
            try {
                String token = ParsingHelper.getTokenFromRequest(request);
                if (token == null || !DbHelper.getInstance().validateToken(token)) {
                    // Invalid token
                    response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
                    httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
                return;
            }

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
                    break;
            }
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }).start();
    }

    public void handleCreate(HttpRequest request, HttpResponse response, HttpContext context) {
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
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_CREATED);
            int id = DbHelper.getInstance().createBook(requestBody);
            response.setHeader(HttpHeaders.LOCATION, "/books/" + id);
        } catch (DbHelper.CreateBookConflictException e) {
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
            response.setHeader("Duplicate record", "/books/" + id);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    public static class SearchResponseBody {
        @JsonProperty("FoundBooks")
        public int foundBooks() {
            return this.results != null ? this.results.size() : 0;
        }
        @JsonProperty("Results")
        public List<Book> results;
        SearchResponseBody() {}

        SearchResponseBody(List<Book> results) {
            this.results = results;
        }
    }

    public void handleSearch(HttpRequest request, HttpResponse response, HttpContext context) {
        Map<String, String> param;
        try {
            param = ParsingHelper.parseRequestQuery(request);
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_CREATED);
            List<Book> books = DbHelper.getInstance().searchBook(param.get("id"), param.get("title"), param.get("author"), param.get("sortby"), param.get("order"), param.get("limit"));
            if (books.size() <= 0) {
                // no book found, return 204
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT);
                return;
            }
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
            SearchResponseBody responseBody = new SearchResponseBody(books);
            ObjectMapper mapper = new ObjectMapper();
            StringEntity body = new StringEntity(
                    mapper.writeValueAsString(responseBody),
                    ContentType.APPLICATION_JSON
            );
            response.setEntity(body);
        } catch (DbHelper.SearchBookBadRequestException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
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

    public void handleAvailability(HttpRequest request, HttpResponse response, HttpContext context) {
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

        URI uri = URI.create(request.getRequestLine().getUri());
        String path = uri.getPath();
        String idStr = path.substring(path.lastIndexOf('/') + 1);
        int id = Integer.parseInt(idStr);

        try {
            DbHelper.getInstance().modifyBookAvailability(id, requestBody.isAvailable);
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
        } catch (NumberFormatException e) {
            System.out.println("Unable to parse number");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (DbHelper.ModifyBookNotFoundException e) { // either id not found or already borrowed
            try {
                List<Book> foundBooks = DbHelper.getInstance().searchBook(idStr, null, null, null, null, null);
                if (foundBooks.isEmpty()) {
                    // book not found
                    response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND, "No book record");
                } else {
                    // book have same state as requested (double borrow/loan)
                    response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
                }
            } catch (Exception e1) {
                e1.printStackTrace();
                // FIXME: update to align with api spec
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void handleDeletion(HttpRequest request, HttpResponse response, HttpContext context) {
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

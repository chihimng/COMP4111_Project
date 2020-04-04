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

public class TransactionRequestHandler implements HttpRequestHandler {
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        // TODO: validate token
        switch (request.getRequestLine().getMethod()) {
            case "POST":
                handleTransactionManagement(request, response, context);
                break;
            case "PUT":
                handleTransactionAction(request, response, context);
                break;
            default:
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }

    public enum TransactionOperation {
        @JsonProperty("commit")
        COMMIT,

        @JsonProperty("cancel")
        CANCEL;
    }

    public static class TransactionPostRequestBody {
        @JsonProperty("Transaction")
        public int transactionId;

        @JsonProperty("Operation")
        public TransactionOperation operation;

        TransactionPostRequestBody() {
        }

        TransactionPostRequestBody(int id, TransactionOperation operation) {
            this.transactionId = id;
            this.operation = operation;
        }
    }

    public enum TransactionPutAction {
        @JsonProperty("loan")
        LOAN,

        @JsonProperty("return")
        RETURN;
    }

    public static class TransactionPutRequestBody {
        @JsonProperty("Transaction")
        public int transactionId;

        @JsonProperty("Book")
        public int bookId;

        @JsonProperty("Action")
        public TransactionPutAction action;

        TransactionPutRequestBody() {
        }

        TransactionPutRequestBody(int transactionId, int bookId, TransactionPutAction action) {
            this.transactionId = transactionId;
            this.bookId = bookId;
            this.action = action;
        }
    }

    public static class TransactionIdResponseBody {
        @JsonProperty("Transaction")
        public int transactionId;

        TransactionIdResponseBody() {}

        TransactionIdResponseBody(int transactionId) {
            this.transactionId = transactionId;
        }
    }

    public void handleTransactionManagement(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        try {
            HttpEntityEnclosingRequest entityEnclosingRequest = ParsingHelper.castHttpEntityRequest(request);
            // If POST body has no content, request new transaction ID
            if(entityEnclosingRequest.getEntity().getContent().available() == 0) {
                handleRequestTransaction(request, response, context);
            } else {
                handleTransactionOperation(request, response, context);
            }
        } catch (ClassCastException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        }
    }

    public void handleRequestTransaction(HttpRequest request, HttpResponse response, HttpContext context) {
        try {
            String token = ParsingHelper.getTokenFromRequest(request);
            TransactionIdResponseBody responseBody = new TransactionIdResponseBody(DbHelper.getInstance().requestTransactionId(token));
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
            ObjectMapper mapper = new ObjectMapper();
            StringEntity body = new StringEntity(
                    mapper.writeValueAsString(responseBody),
                    ContentType.APPLICATION_JSON
            );
            response.setEntity(body);
        } catch (DbHelper.TransactionException e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void handleTransactionAction(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        TransactionPutRequestBody requestBody;
        String token = ParsingHelper.getTokenFromRequest(request);
        try {
            requestBody = ParsingHelper.parseRequestBody(request, TransactionPutRequestBody.class);
        } catch (Exception e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (requestBody == null) {
            System.out.println("Request body is null");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        try {
            DbHelper.getInstance().performTransactionAction(token, requestBody);
        } catch (DbHelper.TransactionException | DbHelper.ModifyBookNotFoundException e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    public void handleTransactionOperation(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        TransactionPostRequestBody requestBody;
        String token = ParsingHelper.getTokenFromRequest(request);

        try {
            requestBody = ParsingHelper.parseRequestBody(request, TransactionPostRequestBody.class);
        } catch (Exception e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (requestBody == null) {
            System.out.println("Request body is null");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        try {
            DbHelper.getInstance().performTransactionOperation(token, requestBody);
        } catch (DbHelper.TransactionException e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            // FIXME: update to align with api spec
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}

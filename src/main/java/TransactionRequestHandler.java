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
        try {
            String token = ParsingHelper.getTokenFromRequest(request);
            if (token == null || !DbHelper.getInstance().validateToken(token)) {
                // Invalid token
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        switch (request.getRequestLine().getMethod()) {
            case "POST":
                handleManage(request, response, context);
                break;
            case "PUT":
                handleAppend(request, response, context);
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

    public void handleManage(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        try {
            HttpEntityEnclosingRequest entityEnclosingRequest = ParsingHelper.castHttpEntityRequest(request);
            // If POST body has no content, request new transaction ID
            if(entityEnclosingRequest.getEntity().getContent().available() == 0) {
                handleCreate(request, response, context);
            } else {
                handleOperation(request, response, context);
            }
        } catch (ClassCastException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
    }

    public void handleCreate(HttpRequest request, HttpResponse response, HttpContext context) {
        try {
            String token = ParsingHelper.getTokenFromRequest(request);
            TransactionIdResponseBody responseBody = new TransactionIdResponseBody(DbHelper.getInstance().createTransaction(token));
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
            ObjectMapper mapper = new ObjectMapper();
            StringEntity body = new StringEntity(
                mapper.writeValueAsString(responseBody),
                ContentType.APPLICATION_JSON
            );
            response.setEntity(body);
            return;
        } catch (DbHelper.CreateTransactionException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }
    }

    public void handleOperation(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        String token = ParsingHelper.getTokenFromRequest(request);

        TransactionPostRequestBody requestBody;
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

        if (requestBody.operation == TransactionOperation.COMMIT) {
            try {
                DbHelper.getInstance().executeTransaction(requestBody.transactionId, token);
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
                return;
            } catch (DbHelper.ExecuteTransactionExpiredException e) {
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
                return;
            } catch (DbHelper.ExecuteTransactionNotFoundException e) {
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
                return;
            } catch (DbHelper.ExecuteTransactionRejectedException e) {
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                return;
            }
        } else if (requestBody.operation == TransactionOperation.CANCEL) {
            try {
                DbHelper.getInstance().deleteTransaction(requestBody.transactionId, token);
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
                return;
            } catch (DbHelper.DeleteTransactionNotFoundException e) {
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
                return;
            }
        } else {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }
    }

    public void handleAppend(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
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
            DbHelper.getInstance().appendTransaction(requestBody.transactionId, token, requestBody.action, requestBody.bookId);
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
            return;
        } catch (DbHelper.AppendTransactionNotFoundException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }
    }
}

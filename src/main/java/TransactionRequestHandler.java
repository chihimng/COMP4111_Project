import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.protocol.*;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;

public class TransactionRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {

    @Override
    public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {
        // Buffer request content in memory for simplicity
        return new BasicAsyncRequestConsumer();
    }

    @Override
    public void handle(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
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
                handleManage(request, httpExchange, context);
                return;
            case "PUT":
                handleAppend(request, httpExchange, context);
                return;
            default:
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_IMPLEMENTED);
                httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
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

    public void handleManage(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
        HttpResponse response = httpExchange.getResponse();
        try {
            HttpEntityEnclosingRequest entityEnclosingRequest = ParsingHelper.castHttpEntityRequest(request);
            // If POST body has no content, request new transaction ID
            if (entityEnclosingRequest.getEntity().getContent().available() == 0) {
                handleCreate(request, httpExchange, context);
            } else {
                handleOperation(request, httpExchange, context);
            }
        } catch (ClassCastException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }
    }

    public void handleCreate(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
        HttpResponse response = httpExchange.getResponse();
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
        } catch (DbHelper.CreateTransactionException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }
    }

    public void handleOperation(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
        HttpResponse response = httpExchange.getResponse();

        String token = ParsingHelper.getTokenFromRequest(request);

        TransactionPostRequestBody requestBody;
        try {
            requestBody = ParsingHelper.parseRequestBody(request, TransactionPostRequestBody.class);
        } catch (Exception e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
            return;
        }

        if (requestBody == null) {
            System.out.println("Request body is null");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
            return;
        }

        if (requestBody.operation == TransactionOperation.COMMIT) {
            try {
                DbHelper.getInstance().executeTransaction(requestBody.transactionId, token);
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
                try {
                    DbHelper.getInstance().deleteTransaction(requestBody.transactionId, token);
                } catch (Exception e) {
                    System.out.println("Transaction delete failed after successful commit");
                }
            } catch (DbHelper.ExecuteTransactionException e) {
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } finally {
                httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
            }
        } else if (requestBody.operation == TransactionOperation.CANCEL) {
            try {
                DbHelper.getInstance().deleteTransaction(requestBody.transactionId, token);
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
            } catch (DbHelper.DeleteTransactionNotFoundException e) {
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            } catch (Exception e) {
                e.printStackTrace();
                response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } finally {
                httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
            }
        } else {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }
    }

    public void handleAppend(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
        HttpResponse response = httpExchange.getResponse();
        TransactionPutRequestBody requestBody;
        String token = ParsingHelper.getTokenFromRequest(request);
        try {
            requestBody = ParsingHelper.parseRequestBody(request, TransactionPutRequestBody.class);
        } catch (Exception e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
            return;
        }

        if (requestBody == null) {
            System.out.println("Request body is null");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
            return;
        }

        try {
            DbHelper.getInstance().appendTransaction(requestBody.transactionId, token, requestBody.action, requestBody.bookId);
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK);
        } catch (DbHelper.AppendTransactionNotFoundException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } finally {
            httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
        }
    }
}

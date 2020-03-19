import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.http.*;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;

public class TransactionRequestHandler implements HttpRequestHandler {
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        // TODO: validate token
        switch (request.getRequestLine().getMethod()) {
            case "POST":
                handleTransactionManagement(request, response, context);
                break;
            case "PUT":
                handleTransactionTask(request, response, context);
                break;
        }
    }

    public enum TransactionOperation {
        @JsonProperty("commit")
        COMMIT,

        @JsonProperty("cancel")
        CANCEL;
    }

    public static class TransactionRequestBody {
        @JsonProperty("Transaction")
        public int transactionId;

        @JsonProperty("Operation")
        public TransactionOperation transactionOperation;

        TransactionRequestBody() {
        }

        TransactionRequestBody(int id, TransactionOperation operation) {
            this.transactionId = id;
            this.transactionOperation = operation;
        }
    }

    public void handleTransactionManagement(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        try {
            HttpEntityEnclosingRequest entityEnclosingRequest = ParsingHelper.castHttpEntityRequest(request);
            if(entityEnclosingRequest.getEntity().getContent().available() == 0) {
                handleRequestTransaction(request, response, context);
            } else {
                handleTransactionAction(request, response, context);
            }
        } catch (ClassCastException e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
        }
    }

    public void handleRequestTransaction(HttpRequest request, HttpResponse response, HttpContext context) {
        System.out.println("request");
    }

    public void handleTransactionAction(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {

        HttpEntityEnclosingRequest entityEnclosingRequest = ParsingHelper.castHttpEntityRequest(request);

        TransactionRequestBody requestBody;
        try {
            requestBody = ParsingHelper.parseRequestBody(request, TransactionRequestBody.class);
        } catch (Exception e) {
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (requestBody == null) {
            System.out.println("Request body is null");
            response.setStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        //TODO;
    }

    public void handleTransactionTask(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {

    }
}

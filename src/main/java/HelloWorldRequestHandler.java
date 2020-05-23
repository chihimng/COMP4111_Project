import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.protocol.*;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

public class HelloWorldRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {
    @Override
    public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {
        // Buffer request content in memory for simplicity
        return new BasicAsyncRequestConsumer();
    }

    @Override
    public void handle(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
        HttpResponse response = httpExchange.getResponse();
        response.setStatusCode(HttpStatus.SC_OK);
        String dbTestResult;
        try {
            dbTestResult = DbHelper.getInstance().test();
        } catch (Exception e) {
            dbTestResult = e.toString();
        }
        StringEntity body = new StringEntity(
                "<html><body><h1>Hello World OwO</h1><h2>"+dbTestResult+"</h2></body></html>",
                ContentType.create("text/html", "UTF-8"));
        response.setEntity(body);
        httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
    }
}

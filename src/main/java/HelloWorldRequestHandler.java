import org.apache.http.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;

public class HelloWorldRequestHandler implements HttpRequestHandler {
    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
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
    }
}

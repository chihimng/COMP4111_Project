import org.apache.http.*;
import org.apache.http.nio.protocol.*;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

public class LogoutRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {
    @Override
    public HttpAsyncRequestConsumer<HttpRequest> processRequest(HttpRequest request, HttpContext context) throws HttpException, IOException {
        // Buffer request content in memory for simplicity
        return new BasicAsyncRequestConsumer();
    }

    @Override
    public void handle(HttpRequest request, HttpAsyncExchange httpExchange, HttpContext context) throws HttpException, IOException {
        new Thread(() -> {
            HttpResponse response = httpExchange.getResponse();
            String token;
            try {
                token = ParsingHelper.getTokenFromRequest(request);
                if (token == null) {
                    // missing token
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

            try {
                if (DbHelper.getInstance().signOut(token)) {
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
            } finally {
                httpExchange.submitResponse(new BasicAsyncResponseProducer(response));
            }
        }).start();
    }
}

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class ParsingHelper {
    static HttpEntityEnclosingRequest castHttpEntityRequest(HttpRequest request) throws ClassCastException {
        if (!(request instanceof HttpEntityEnclosingRequest)) {
            // FIXME: update to align with api spe
            System.out.println("Failed to cast request into HttpEntityEnclosingRequest");
            throw new ClassCastException("Failed to cast request into HttpEntityEnclosingRequest");
        }
        return (HttpEntityEnclosingRequest) request;
    }

    static <T> T parseRequestBody(HttpRequest request, Class<T> valueType) throws ClassCastException, IOException {
        HttpEntityEnclosingRequest entityEnclosingRequest = castHttpEntityRequest(request);
        HttpEntity entity = entityEnclosingRequest.getEntity();
        ObjectMapper mapper = new ObjectMapper();
        T requestBody;
        try {
            requestBody = mapper.readValue(entity.getContent(), valueType);
        } catch (IOException e) { // parsing failed
            // FIXME: update to align with api spec
            e.printStackTrace();
            System.out.println("Body parsing failed");
            throw e;
        }
        return requestBody;
    }

    static Map<String, String> parseRequestQuery(HttpRequest request) throws UnsupportedEncodingException {
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

        return param;
    }


    static String getTokenFromRequest(HttpRequest request) throws UnsupportedEncodingException {
        return parseRequestQuery(request).get("token");
    }
}

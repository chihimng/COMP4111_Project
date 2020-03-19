import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.apache.http.*;

import java.io.IOException;

public class ParsingHelper {
    static <T> T parseRequestBody(HttpRequest request, Class<T> valueType) throws ClassCastException, IOException {
        if (!(request instanceof HttpEntityEnclosingRequest)) {
            // FIXME: update to align with api spe
            System.out.println("Failed to cast request into HttpEntityEnclosingRequest");
            throw new ClassCastException("Failed to cast request into HttpEntityEnclosingRequest");
        }
        HttpEntityEnclosingRequest entityEnclosingRequest = (HttpEntityEnclosingRequest) request;
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
}

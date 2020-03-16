import org.apache.http.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


public class ApiServer {
    public static void main(String[] args) {
        int port = 8080;

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(15000)
                .setTcpNoDelay(true)
                .build();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setServerInfo("ApiServer/1.0")
                .setSocketConfig(socketConfig)
                .setSslContext(null)
                .setExceptionLogger(ExceptionLogger.STD_ERR)
                .registerHandler("*", new HelloWorldRequestHandler())
                .create();


        try {
            server.start();
            System.out.println("API Server listening on port " + port);
            server.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("API Server is shutting down with 5 seconds grace period");
            server.shutdown(5, TimeUnit.SECONDS);
        }));
    }
}

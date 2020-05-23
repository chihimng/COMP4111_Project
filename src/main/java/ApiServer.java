import org.apache.http.ExceptionLogger;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.reactor.IOReactorConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


public class ApiServer {
    public static void main(String[] args) {
        int port = 8080;
        try {
            port = Integer.parseInt(System.getenv("PORT"));
        } catch (Exception e) {
            System.out.println("Failed to obtain port config from env, reverting to 8080.");
        }

        final IOReactorConfig config = IOReactorConfig.custom()
            .setSoTimeout(15000)
            .setTcpNoDelay(true)
            .build();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setServerInfo("ApiServer/1.0")
                .setIOReactorConfig(config)
                .setSslContext(null)
                .setExceptionLogger(ExceptionLogger.STD_ERR)
                .registerHandler("/", new HelloWorldRequestHandler())
                .registerHandler("/BookManagementService/login", new LoginRequestHandler())
                .registerHandler("/BookManagementService/logout", new LogoutRequestHandler())
                .registerHandler("/BookManagementService/books*", new BooksRequestHandler())
                .registerHandler("/BookManagementService/transaction*", new TransactionRequestHandler())
                .create();

        try {
            server.start();
            System.out.println("API Server listening on port " + port);
            server.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("API Server is shutting down after 5 seconds grace period");
                server.shutdown(5, TimeUnit.SECONDS);
            }
        });
    }
}

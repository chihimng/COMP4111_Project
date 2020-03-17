import java.sql.*;
import java.util.UUID;

// Notice, do not import com.mysql.jdbc.*
// or you will have problems!

public class DbHelper {
    // Singleton
    private static DbHelper _singleton = null;

    public static DbHelper getInstance() throws Exception{
        if (_singleton == null) {
            _singleton = new DbHelper();
        }
        return _singleton;
    }

    // Properties & Constructor
    private String dbUrl = "jdbc:mysql://localhost:3306/comp4111?user=comp4111&password=comp4111&useSSL=false";

    private DbHelper() throws Exception{
        // Import MySQL Driver
        // The newInstance() call is a work around for some broken Java implementations
        Class.forName("com.mysql.jdbc.Driver").newInstance();

        // Import Config
        try {
            String env = System.getenv("DB_URL");
            if (env != null) dbUrl = "jdbc:" + env;
        } catch ( Exception e ) {
            System.out.println("Failed to load db host from env, reverting to default");
        }
    }

    public String test() throws Exception {
        Connection conn = DriverManager.getConnection(dbUrl);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'version%';");
        String result = "";
        while (rs.next()) {
            result += rs.getString(1) + "\t" + rs.getString(2) + "\n";
        }
        conn.close();
        return result;
    }

    public static class SignInException extends Exception {
        SignInException(String s) {
            super(s);
        }
    }
    public static class SignInConflictException extends SignInException {
        SignInConflictException(String s) {
            super(s);
        }
    }
    public static class SignInBadRequestException extends SignInException {
        SignInBadRequestException(String s) {
            super(s);
        }
    }

    public String signIn(String username, String password) throws Exception {
        Connection conn = DriverManager.getConnection(dbUrl);
        PreparedStatement stmt = conn.prepareStatement("SELECT username FROM user WHERE username = ? AND password = ?;");
        stmt.setString(1, username);
        stmt.setString(2, password);
        ResultSet rs = stmt.executeQuery();
        if (rs.next() && rs.getString(1).equals(username)) {
            // success
            String token = UUID.randomUUID().toString();
            stmt.close();
            stmt = conn.prepareStatement("INSERT INTO session (username, access_token) VALUES (?, ?);");
            stmt.setString(1, username);
            stmt.setString(2, token);
            int rowsAffected = 0;
            try {
                rowsAffected = stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (rowsAffected > 0) { // success
                rs.close();
                stmt.close();
                conn.close();
                return token;
            } else { // failed, assume to be dup login
                rs.close();
                stmt.close();
                conn.close();
                throw new SignInConflictException("Duplicate Sign In");
            }
        } else {
            // failed, username/pw wrong
            rs.close();
            stmt.close();
            conn.close();
            throw new SignInBadRequestException("Incorrect Username or Password");
        }
    }
}
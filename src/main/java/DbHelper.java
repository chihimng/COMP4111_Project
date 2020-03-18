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

    public String signIn(String username, String password) throws SignInException {
        String token = UUID.randomUUID().toString();
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(dbUrl);
            stmt = conn.prepareStatement("INSERT INTO session (username, token) VALUES ((SELECT username FROM user WHERE username = ? AND password = UNHEX(SHA2(CONCAT(?, salt), 256))), ?)");
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, token);

            if (stmt.executeUpdate() > 0) { // success
                return token;
            } else { // this should not happen
                throw new SignInException("this should now happen: updated 0 rows without exception");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Column 'username' cannot be null")) {
                // failed, username/pw wrong
                throw new SignInBadRequestException("Incorrect Username or Password");
            } else if (e.getMessage().contains("Duplicate entry")) {
                // failed, dup login
                throw new SignInConflictException("Duplicate Sign In");
            } else {
                throw new SignInException(e.getMessage());
            }
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (Exception e) {}
        }
    }

    public static class SignOutException extends Exception {
        SignOutException(String s) {
            super(s);
        }
    }

    public static class SignOutBadRequestException extends SignOutException {
        SignOutBadRequestException(String s) {
            super(s);
        }
    }

    public boolean signOut(String token) throws SignOutException {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(dbUrl);
            stmt = conn.prepareStatement("DELETE FROM session WHERE token = ?;");
            stmt.setString(1, token);
            if (stmt.executeUpdate() > 0) { // success
                return true;
            } else { // failed: token not found
                throw new SignOutBadRequestException("Token not found");
            }
        } catch (SQLException e) {
            throw new SignOutException(e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (Exception e) {}
        }
    }
}
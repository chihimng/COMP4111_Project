import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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
            } catch (Exception e) {
            }
        }
    }

    public static class CreateBookException extends Exception {
        CreateBookException(String s) {
            super(s);
        }
    }
    public static class CreateBookConflictException extends CreateBookException {
        CreateBookConflictException(String s) {
            super(s);
        }
    }

    public int createBook(Book book) throws CreateBookException {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(dbUrl);
            stmt = conn.prepareStatement("INSERT INTO book (title, author, publisher, year) VALUES (?, ?, ?, ?);");
            stmt.setString(1, book.title);
            stmt.setString(2, book.author);
            stmt.setString(3, book.publisher);
            stmt.setInt(4, book.year);
            if (stmt.executeUpdate() > 0) { // success
                stmt.close();
                stmt = conn.prepareCall("SELECT LAST_INSERT_ID();");
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new CreateBookException("failed to get last inserted id");
                }
            } else { // failed
                throw new CreateBookException("this should now happen: updated 0 rows without exception");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate entry")) {
                throw new CreateBookConflictException("Duplicate found");
            }
            throw new CreateBookException(e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (Exception e) {
            }
        }
    }

    public static class FindDuplicateBookException extends Exception {
        FindDuplicateBookException(String s) {
            super(s);
        }
    }

    public static class FindDuplicateNotFoundBookException extends FindDuplicateBookException {
        FindDuplicateNotFoundBookException(String s) {
            super(s);
        }
    }


    public int findDuplicateBook(Book book) throws FindDuplicateBookException {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(dbUrl);
            stmt = conn.prepareStatement("SELECT * FROM book WHERE title = ? AND author = ? AND publisher = ? AND year = ?");
            stmt.setString(1, book.title);
            stmt.setString(2, book.author);
            stmt.setString(3, book.publisher);
            stmt.setInt(4, book.year);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            } else { // failed
                throw new FindDuplicateNotFoundBookException("duplicate book not found");
            }
        } catch (SQLException e) {
            throw new FindDuplicateBookException(e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (Exception e) {
            }
        }
    }

    public static class SearchBookException extends Exception {
        SearchBookException(String s) {
            super(s);
        }
    }

    public List<Book> searchBook(String id, String title, String author, String sort, String order, String limit) throws SearchBookException {
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(dbUrl);
            List<String> clauses = new ArrayList<String>();
            String query = "SELECT * FROM book";
            if (id != null) {
                clauses.add("id = ?");
            }
            if (title != null && !title.isEmpty()) {
                clauses.add("title LIKE ?");
            }
            if (author != null && !author.isEmpty()) {
                clauses.add("author LIKE ?");
            }
            if (!clauses.isEmpty()) {
                query += " WHERE " + String.join(" AND ", clauses);
            }
            if (sort != null && !sort.isEmpty()) {
                String orderClause = null;
                if (sort.equals("id")) {
                    orderClause = "ORDER BY id";
                } else if (sort.equals("title")) {
                    orderClause = "ORDER BY title";
                } else if (sort.equals("author")) {
                    orderClause = "ORDER BY author";
                }
                if (orderClause != null) {
                    if (order != null) {
                        if (order.contains("asc")) {
                            orderClause += " ASC";
                        } else if (order.contains("desc")) {
                            orderClause += " DESC";
                        }
                    }
                    query += " " + orderClause;
                }
            }
            if (limit != null && !limit.isEmpty()) {
                Integer limitCount = null;
                try {
                    limitCount = Integer.parseInt(limit);
                } catch (Exception e) {}
                if (limitCount != null) {
                    query += " LIMIT " + limitCount.toString();
                }
            }
            query += ";";
            System.out.println(clauses.toString());
            System.out.println(query);
            stmt = conn.prepareStatement(query);
            int i = 1;
            if (query.contains("id = ?")) {
                stmt.setInt(i, Integer.parseUnsignedInt(id));
                i += 1;
            }
            if (query.contains("title LIKE ?")) {
                stmt.setString(i, "%" + title + "%");
                i += 1;
            }
            if (query.contains("author LIKE ?")) {
                stmt.setString(i, "%" + author + "%");
                i += 1;
            }
            ResultSet rs = stmt.executeQuery();
            List<Book> books = new ArrayList<Book>();
            while (rs.next()) {
                books.add(new Book(
                    rs.getString("title"),
                    rs.getString("author"),
                    rs.getString("publisher"),
                    rs.getInt("year")
                ));
            }
            return books;
        } catch (SQLException e) {
            throw new SearchBookException(e.getMessage());
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (Exception e) {
            }
        }
    }
}
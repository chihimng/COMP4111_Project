import com.mysql.jdbc.MySQLConnection;
import com.mysql.jdbc.MysqlErrorNumbers;
import com.mysql.jdbc.jdbc2.optional.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Notice, do not import com.mysql.jdbc.*
// or you will have problems!

public class DbHelper {
    // Singleton
    private static DbHelper _singleton = null;

    public static DbHelper getInstance() throws Exception {
        if (_singleton == null) {
            _singleton = new DbHelper();
        }
        return _singleton;
    }

    public class ConnectionKey {
        private int id;
        private String token;

        public ConnectionKey(int id, String token) {
            this.id = id;
            this.token = token;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ConnectionKey other = (ConnectionKey) obj;
            return id == other.id && token.equals(other.token);
        }
    }

    private MysqlDataSource dataSource;
    private ConcurrentHashMap<ConnectionKey, MySQLConnection> txConnectionMap = new ConcurrentHashMap<>();

    private static int TRANSACTION_TIMEOUT_MILLIS = 120000;
    private Timer timer = new Timer();
    private ConcurrentHashMap<Integer, TimerTask> transactionTimeoutTaskMap = new ConcurrentHashMap<>();

    private DbHelper() throws Exception {
        // Import MySQL Driver
        // The newInstance() call is a work around for some broken Java implementations
        Class.forName("com.mysql.jdbc.Driver").newInstance();
        String dbUrl = "jdbc:mysql://localhost:3306/comp4111?user=comp4111&password=comp4111&useSSL=false";

        // Import Config
        try {
            String env = System.getenv("DB_URL");
            // Properties & Constructor
            if (env != null) dbUrl = "jdbc:" + env;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to load db host from env, reverting to default");
        }

        this.dataSource = new MysqlDataSource();
        this.dataSource.setUrl(dbUrl);
    }

    public String test() throws Exception {
        Connection conn = this.dataSource.getConnection();
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
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO session (username, token) VALUES ((SELECT username FROM user WHERE username = ? AND password = UNHEX(SHA2(CONCAT(?, salt), 256))), ?)")) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, token);

            if (stmt.executeUpdate() > 0) { // success
                return token;
            } else { // this should not happen
                throw new SignInException("this should not happen: updated 0 rows without exception");
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
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM session WHERE token = ?")) {
            stmt.setString(1, token);
            if (stmt.executeUpdate() > 0) { // success
                return true;
            } else { // failed: token not found
                throw new SignOutBadRequestException("Token not found");
            }
        } catch (SQLException e) {
            throw new SignOutException(e.getMessage());
        }
    }

    public static class ValidateTokenException extends Exception {
        ValidateTokenException(String s) {
            super(s);
        }
    }

    public boolean validateToken(String token) throws ValidateTokenException {
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM session WHERE token = ?")) {
            stmt.setString(1, token);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new ValidateTokenException(e.getMessage());
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
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO book (title, author, publisher, year) VALUES (?, ?, ?, ?)"); PreparedStatement getIdStmt = conn.prepareCall("SELECT LAST_INSERT_ID()")) {
            stmt.setString(1, book.title);
            stmt.setString(2, book.author);
            stmt.setString(3, book.publisher);
            stmt.setInt(4, book.year);
            if (stmt.executeUpdate() > 0) { // success
                ResultSet rs = getIdStmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new CreateBookException("failed to get last inserted id");
                }
            } else { // failed
                throw new CreateBookException("this should not happen: updated 0 rows without exception");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate entry")) {
                throw new CreateBookConflictException("Duplicate found");
            }
            throw new CreateBookException(e.getMessage());
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
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM book WHERE title = ?")) {
            stmt.setString(1, book.title);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            } else { // failed
                throw new FindDuplicateNotFoundBookException("duplicate book not found");
            }
        } catch (SQLException e) {
            throw new FindDuplicateBookException(e.getMessage());
        }
    }

    public static class SearchBookException extends Exception {
        SearchBookException(String s) {
            super(s);
        }
    }

    public static class SearchBookBadRequestException extends SearchBookException {
        SearchBookBadRequestException(String s) {
            super(s);
        }
    }

    public List<Book> searchBook(String id, String title, String author, String sort, String order, String limit) throws SearchBookException {
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
            } catch (Exception e) {
                e.printStackTrace();
                throw new SearchBookBadRequestException("failed to parse limit to int");
            }
            if (limitCount < 0) {
                throw new SearchBookBadRequestException("limit not positive int");
            }
            query += " LIMIT " + limitCount.toString();
        }
        query += ";";
        System.out.println(clauses.toString());
        System.out.println(query);
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
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
                        rs.getInt("year"),
                        rs.getBoolean("isAvailable")
                ));
            }
            return books;
        } catch (SQLException e) {
            throw new SearchBookException(e.getMessage());
        }
    }

    public static class ModifyBookException extends Exception {
        ModifyBookException(String s) {
            super(s);
        }
    }

    public static class ModifyBookNotFoundException extends ModifyBookException {
        ModifyBookNotFoundException(String s) {
            super(s);
        }
    }

    public void modifyBookAvailability(int id, boolean isAvailable) throws ModifyBookException {
        // Try with resources to leverage AutoClosable implementation
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement("UPDATE book SET isAvailable = ? WHERE id = ? AND isAvailable != ?")) {
            stmt.setBoolean(1, isAvailable);
            stmt.setInt(2, id);
            stmt.setBoolean(3, isAvailable);
            if (stmt.executeUpdate() <= 0) { // failed
                throw new ModifyBookNotFoundException("No book record");
            }
        } catch (SQLException e) {
            throw new ModifyBookException(e.getMessage());
        }
    }

    public static class DeleteBookException extends Exception {
        DeleteBookException(String s) {
            super(s);
        }
    }

    public static class DeleteBookNotFoundException extends DeleteBookException {
        DeleteBookNotFoundException(String s) {
            super(s);
        }
    }

    public void deleteBook(int id) throws DeleteBookException {
        // Try with resources to leverage AutoClosable implementation
        try (Connection conn = this.dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM book WHERE id = ?")) {
            stmt.setInt(1, id);
            if (stmt.executeUpdate() <= 0) { // failed
                throw new DeleteBookNotFoundException("No book record");
            }
        } catch (SQLException e) {
            throw new DeleteBookException(e.getMessage());
        }
    }

    public static class CreateTransactionException extends Exception {
        CreateTransactionException(String s) {
            super(s);
        }
    }

    public int createTransaction(String token) throws CreateTransactionException {
        try {
            Map.Entry<ConnectionKey, MySQLConnection> entry = this.txConnectionMap.entrySet().stream().filter(e -> e.getKey().token.equals(token)).findFirst().orElse(null);
            if(entry != null && !entry.getValue().isClosed()) {
                return entry.getKey().id;
            }

            MySQLConnection conn = ((MySQLConnection) this.dataSource.getConnection());
            this.txConnectionMap.put(new ConnectionKey((int) conn.getId(), token), conn);
            conn.setAutoCommit(false);

            this.resetTransactionTimeout(conn, token);
            return (int) conn.getId();
        } catch (Exception e) {
            e.printStackTrace();
            throw new CreateTransactionException(e.getMessage());
        }
    }

    public static class AppendTransactionException extends Exception {
        AppendTransactionException(String s) {
            super(s);
        }
    }

    public static class AppendTransactionInvalidException extends AppendTransactionException {
        AppendTransactionInvalidException(String s) {
            super(s);
        }
    }

    public static class AppendTransactionDeadlockException extends AppendTransactionException {
        AppendTransactionDeadlockException(String s) {
            super(s);
        }
    }

    public static class AppendTransactionNotFoundException extends AppendTransactionException {
        AppendTransactionNotFoundException(String s) {
            super(s);
        }
    }

    public void appendTransaction(int transactionId, String token, TransactionRequestHandler.TransactionPutAction action, int bookId) throws AppendTransactionException {
        try {
            MySQLConnection conn = this.txConnectionMap.get(new ConnectionKey(transactionId, token));
            if(conn == null) {
                throw new AppendTransactionNotFoundException("Transaction not found");
            }
            conn.prepareStatement("LOCK TABLES book WRITE").execute();
            PreparedStatement stmt = conn.prepareStatement("UPDATE book SET isAvailable = ? WHERE id = ? AND isAvailable != ?");
            stmt.setBoolean(1, action == TransactionRequestHandler.TransactionPutAction.RETURN);
            stmt.setInt(2, bookId);
            stmt.setBoolean(3, action == TransactionRequestHandler.TransactionPutAction.RETURN);
            boolean success = stmt.executeUpdate() > 0;
            conn.prepareStatement("UNLOCK TABLES").execute();
            resetTransactionTimeout(conn, token);
            if(!success) {
                throw new AppendTransactionInvalidException("Invalid actions");
            }
        } catch (SQLException e) {
            if(e.getErrorCode() == MysqlErrorNumbers.ER_LOCK_DEADLOCK) {
                System.out.println("DEADLOCK");
                throw new AppendTransactionDeadlockException(e.getMessage());
            } else {
                e.printStackTrace();
                throw new AppendTransactionException(e.getMessage());
            }
        }
    }

    public static class ExecuteTransactionException extends Exception {
        ExecuteTransactionException(String s) {
            super(s);
        }
    }

    public static class ExecuteTransactionNotFoundException extends ExecuteTransactionException {
        ExecuteTransactionNotFoundException(String s) {
            super(s);
        }
    }

    public void executeTransaction(int transactionId, String token, boolean commit) throws ExecuteTransactionException {
        try {
            Connection conn = this.txConnectionMap.get(new ConnectionKey(transactionId, token));
            if(conn == null) {
                throw new ExecuteTransactionNotFoundException("Transaction not found");
            }
            if(commit) {
                conn.commit();
            } else {
                conn.rollback();
            }
            conn.close();
            this.txConnectionMap.remove(new ConnectionKey(transactionId, token));
            this.cancelTransactionTimeout(transactionId);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ExecuteTransactionException(e.getMessage());
        }
    }

    private void resetTransactionTimeout(MySQLConnection conn, String token) {
        this.cancelTransactionTimeout((int) conn.getId());
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    if(!conn.isClosed()) {
                        conn.close();
                    }
                    txConnectionMap.remove(new ConnectionKey((int) conn.getId(), token));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        };
        this.transactionTimeoutTaskMap.put((int) conn.getId(), task);
        this.timer.schedule(task, TRANSACTION_TIMEOUT_MILLIS);
    }

    private void cancelTransactionTimeout(int id) {
        TimerTask task = this.transactionTimeoutTaskMap.get(id);
        if(task != null) {
            task.cancel();
        }
    }
}
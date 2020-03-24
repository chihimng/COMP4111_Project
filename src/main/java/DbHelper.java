import com.mysql.jdbc.jdbc2.optional.JDBC4MysqlXAConnection;
import com.mysql.jdbc.jdbc2.optional.MysqlXAConnection;
import com.mysql.jdbc.jdbc2.optional.MysqlXADataSource;
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.net.Inet4Address;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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

    // Properties & Constructor
    private String dbUrl = "jdbc:mysql://localhost:3306/comp4111?user=comp4111&password=comp4111&useSSL=false";

    private DbHelper() throws Exception {
        // Import MySQL Driver
        // The newInstance() call is a work around for some broken Java implementations
        Class.forName("com.mysql.jdbc.Driver").newInstance();

        // Import Config
        try {
            String env = System.getenv("DB_URL");
            if (env != null) dbUrl = "jdbc:" + env;
        } catch (Exception e) {
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement stmt = conn.prepareStatement("INSERT INTO session (username, token) VALUES ((SELECT username FROM user WHERE username = ? AND password = UNHEX(SHA2(CONCAT(?, salt), 256))), ?)")){
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement stmt = conn.prepareStatement("DELETE FROM session WHERE token = ?;")){
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement stmt = conn.prepareStatement("INSERT INTO book (title, author, publisher, year) VALUES (?, ?, ?, ?);"); PreparedStatement getIdStmt = conn.prepareCall("SELECT LAST_INSERT_ID();")) {
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
                throw new CreateBookException("this should now happen: updated 0 rows without exception");
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM book WHERE title = ? AND author = ? AND publisher = ? AND year = ?");) {
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
        }
    }

    public static class SearchBookException extends Exception {
        SearchBookException(String s) {
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
            } catch (Exception e) {}
            if (limitCount != null) {
                query += " LIMIT " + limitCount.toString();
            }
        }
        query += ";";
        System.out.println(clauses.toString());
        System.out.println(query);
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement stmt = conn.prepareStatement(query)){
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
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement stmt = conn.prepareStatement("UPDATE book SET isAvailable = ? WHERE id = ?;")) {
            stmt.setBoolean(1, isAvailable);
            stmt.setInt(2, id);
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

    public static class DeleteBookNotFoundException extends DeleteBookException{
        DeleteBookNotFoundException(String s) { super(s); }
    }

    public void deleteBook(int id) throws DeleteBookException {
        // Try with resources to leverage AutoClosable implementation
        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement stmt = conn.prepareStatement("DELETE FROM book WHERE id = ?;")) {
            stmt.setInt(1, id);
            if (stmt.executeUpdate() <= 0) { // failed
                throw new DeleteBookNotFoundException("No book record");
            }
        } catch (SQLException e) {
            throw new DeleteBookException(e.getMessage());
        }
    }

    public static class TransactionException extends Exception {
        TransactionException(String s) {
            super(s);
        }
    }

    public static class TransactionNewIdException extends TransactionException {
        TransactionNewIdException(String s) { super(s); }
    }

    public int requestTransactionId() throws Exception {
        MysqlXADataSource xaDataSource = new MysqlXADataSource();
        xaDataSource.setURL(dbUrl);
        XAConnection xaConn = xaDataSource.getXAConnection();
        try (Connection con = xaConn.getConnection(); PreparedStatement stmt = con.prepareStatement("INSERT INTO xid (id,formatId, gtrid, bqual) VALUES (?, ?, ?, ?)")) {
            // Get a unique Xid object for testing.
            XAResource xaRes = null;
            Xid xid = null;
            xid = XidImpl.getUniqueXid();
            int txnUniqueID = XidImpl.txnUniqueID;

            // Get the XAResource object and set the timeout value.
            xaRes = xaConn.getXAResource();
            xaRes.setTransactionTimeout(0);

            // Perform the XA transaction.
            System.out.println("Write -> xid = " + xid.toString());
            xaRes.start(xid, XAResource.TMNOFLAGS);
            xaRes.end(xid, XAResource.TMSUSPEND);
            stmt.setInt(1, txnUniqueID);
            stmt.setInt(2, xid.getFormatId());
            stmt.setString(3, HexBin.encode(xid.getGlobalTransactionId()));
            stmt.setString(4, HexBin.encode(xid.getBranchQualifier()));
            stmt.executeUpdate();

            return txnUniqueID;
        } catch (SQLException e) {
            throw new TransactionNewIdException(e.getMessage());
        } finally {
            xaConn.close();
        }
    }
}

class XidImpl implements Xid {

    public int formatId;
    public byte[] gtrid;
    public byte[] bqual;

    public byte[] getGlobalTransactionId() {
        return gtrid;
    }

    public byte[] getBranchQualifier() {
        return bqual;
    }

    public int getFormatId() {
        return formatId;
    }

    XidImpl(int formatId, byte[] gtrid, byte[] bqual) {
        this.formatId = formatId;
        this.gtrid = gtrid;
        this.bqual = bqual;
    }

    public String toString() {
        int hexVal;
        StringBuffer sb = new StringBuffer(512);
        sb.append("formatId=" + formatId);
        sb.append(" gtrid(" + gtrid.length + ")={0x");
        for (int i = 0; i < gtrid.length; i++) {
            hexVal = gtrid[i] & 0xFF;
            if (hexVal < 0x10)
                sb.append("0" + Integer.toHexString(gtrid[i] & 0xFF));
            else
                sb.append(Integer.toHexString(gtrid[i] & 0xFF));
        }
        sb.append("} bqual(" + bqual.length + ")={0x");
        for (int i = 0; i < bqual.length; i++) {
            hexVal = bqual[i] & 0xFF;
            if (hexVal < 0x10)
                sb.append("0" + Integer.toHexString(bqual[i] & 0xFF));
            else
                sb.append(Integer.toHexString(bqual[i] & 0xFF));
        }
        sb.append("}");
        return sb.toString();
    }

    // Returns a globally unique transaction id.
    static byte[] localIP = null;
    static int txnUniqueID = 0;

    static Xid getUniqueXid() {

        Random rnd = new Random(System.currentTimeMillis());
        txnUniqueID++;
        int txnUID = txnUniqueID;
        int tidID = 1;
        int randID = rnd.nextInt();
        byte[] gtrid = new byte[64];
        byte[] bqual = new byte[64];
        if (null == localIP) {
            try {
                localIP = Inet4Address.getLocalHost().getAddress();
            } catch (Exception ex) {
                localIP = new byte[] {0x01, 0x02, 0x03, 0x04};
            }
        }
        System.arraycopy(localIP, 0, gtrid, 0, 4);
        System.arraycopy(localIP, 0, bqual, 0, 4);

        // Bytes 4 -> 7 - unique transaction id.
        // Bytes 8 ->11 - thread id.
        // Bytes 12->15 - random number generated by using seed from current time in milliseconds.
        for (int i = 0; i <= 3; i++) {
            gtrid[i + 4] = (byte) (txnUID % 0x100);
            bqual[i + 4] = (byte) (txnUID % 0x100);
            txnUID >>= 8;
            gtrid[i + 8] = (byte) (tidID % 0x100);
            bqual[i + 8] = (byte) (tidID % 0x100);
            tidID >>= 8;
            gtrid[i + 12] = (byte) (randID % 0x100);
            bqual[i + 12] = (byte) (randID % 0x100);
            randID >>= 8;
        }
        return new XidImpl(0x1234, gtrid, bqual);
    }
}

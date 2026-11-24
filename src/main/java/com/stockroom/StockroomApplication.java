package com.stockroom;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class StockroomApplication {
    private static final Gson JSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int PASSWORD_ITERATIONS = 160_000;
    private static final int SESSION_DAYS = 7;
    private static final String SESSION_COOKIE = "stockroom_session";
    private static String dbUrl, dbUser, dbPassword;

    private record Identity(long userId, long storeId, String name, String email,
                            String role, String storeName, String storeLocation) {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        dbUrl = env("DB_URL", "jdbc:mysql://localhost:3306/stockroom?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        dbUser = env("DB_USER", "root");
        dbPassword = env("DB_PASSWORD", "");
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/", StockroomApplication::handleApi);
        server.createContext("/", StockroomApplication::servePage);
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
        System.out.printf("Stockroom is running on port %d%n", port);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS stores (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(160) NOT NULL, location VARCHAR(200) NOT NULL DEFAULT '', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (id BIGINT PRIMARY KEY AUTO_INCREMENT, store_id BIGINT NOT NULL, name VARCHAR(160) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, role VARCHAR(40) NOT NULL DEFAULT 'Store owner', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT fk_users_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sessions (token_hash CHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_sessions_expiry (expires_at), CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS products (id BIGINT PRIMARY KEY AUTO_INCREMENT, store_id BIGINT NOT NULL, sku VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL, category VARCHAR(60) NOT NULL, price DECIMAL(12,2) NOT NULL DEFAULT 0, stock INT NOT NULL DEFAULT 0, expiry_date DATE NULL, icon VARCHAR(12) NOT NULL DEFAULT '📦', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY uq_products_store_sku (store_id, sku), INDEX idx_products_store (store_id), INDEX idx_products_expiry (store_id, expiry_date), INDEX idx_products_category (store_id, category), CONSTRAINT fk_products_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS suppliers (id BIGINT PRIMARY KEY AUTO_INCREMENT, store_id BIGINT NOT NULL, name VARCHAR(160) NOT NULL, category VARCHAR(60) NOT NULL, phone VARCHAR(40) NOT NULL DEFAULT '', email VARCHAR(160) NOT NULL DEFAULT '', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_suppliers_store (store_id), CONSTRAINT fk_suppliers_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS sales (id BIGINT PRIMARY KEY AUTO_INCREMENT, store_id BIGINT NOT NULL, product_id BIGINT NULL, product_name VARCHAR(160) NOT NULL, units INT NOT NULL, unit_price DECIMAL(12,2) NOT NULL, amount DECIMAL(12,2) NOT NULL, sold_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_sales_store_date (store_id, sold_at), CONSTRAINT fk_sales_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE, CONSTRAINT fk_sales_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS store_settings (store_id BIGINT NOT NULL, setting_key VARCHAR(80) NOT NULL, setting_value TEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (store_id, setting_key), CONSTRAINT fk_settings_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE)");
        }
        migrateStoreColumn("products");
        migrateStoreColumn("suppliers");
        ensureStoreScopedSkuIndex();
    }

    private static void ensureStoreScopedSkuIndex() throws SQLException {
        try (Connection c = connection(); PreparedStatement q = c.prepareStatement(
                "SELECT index_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='products' AND non_unique=0 GROUP BY index_name HAVING COUNT(*)=1 AND MAX(column_name)='sku'")) {
            List<String> legacyIndexes = new ArrayList<>();
            try (ResultSet r = q.executeQuery()) { while (r.next()) legacyIndexes.add(r.getString(1)); }
            try (Statement s = c.createStatement()) {
                for (String index : legacyIndexes) s.executeUpdate("ALTER TABLE products DROP INDEX `" + index.replace("`", "``") + "`");
                try (PreparedStatement exists = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='products' AND index_name='uq_products_store_sku'")) {
                    try (ResultSet r = exists.executeQuery()) { r.next(); if (r.getInt(1) == 0) s.executeUpdate("CREATE UNIQUE INDEX uq_products_store_sku ON products(store_id, sku)"); }
                }
            }
        }
    }

    private static void migrateStoreColumn(String table) throws SQLException {
        try (Connection c = connection(); PreparedStatement q = c.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name='store_id'")) {
            q.setString(1, table);
            try (ResultSet r = q.executeQuery()) {
                r.next();
                if (r.getInt(1) == 0) {
                    try (Statement s = c.createStatement()) {
                        s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN store_id BIGINT NULL");
                        s.executeUpdate("CREATE INDEX idx_" + table + "_store ON " + table + "(store_id)");
                    }
                }
            }
        }
    }

    private static void handleApi(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (path.equals("/api/health")) { send(ex, 200, Map.of("ok", true, "database", "connected")); return; }
            if (path.equals("/api/auth/signup") && method.equals("POST")) { signup(ex); return; }
            if (path.equals("/api/auth/login") && method.equals("POST")) { login(ex); return; }
            if (path.equals("/api/auth/logout") && method.equals("POST")) { logout(ex); return; }
            if (path.equals("/api/auth/me") && method.equals("GET")) { send(ex, 200, identityMap(requireIdentity(ex))); return; }
            Identity identity = requireIdentity(ex);
            if (path.equals("/api/auth/profile") && method.equals("PUT")) { updateProfile(ex, identity); return; }
            if (path.equals("/api/store") && method.equals("PUT")) { updateStore(ex, identity); return; }
            if (path.equals("/api/sales") && method.equals("GET")) { send(ex, 200, readSales(identity.storeId())); return; }
            if (path.equals("/api/sales") && method.equals("POST")) { recordSale(ex, identity); return; }
            if (path.equals("/api/products") || path.equals("/api/products/")) { products(ex, method, identity); return; }
            if (path.startsWith("/api/products/")) { productById(ex, method, path.substring("/api/products/".length()), identity); return; }
            if (path.equals("/api/suppliers") && method.equals("GET")) { send(ex, 200, readSuppliers(identity.storeId())); return; }
            if (path.equals("/api/suppliers") && method.equals("POST")) { createSupplier(ex, identity); return; }
            if (path.startsWith("/api/suppliers/") && method.equals("DELETE")) { deleteSupplier(ex, path.substring("/api/suppliers/".length()), identity); return; }
            if (path.equals("/api/settings") && method.equals("GET")) { send(ex, 200, readSettings(identity.storeId())); return; }
            if (path.equals("/api/settings") && method.equals("PUT")) { updateSettings(ex, identity); return; }
            send(ex, 404, Map.of("error", "API endpoint not found"));
        } catch (Unauthenticated e) {
            send(ex, 401, Map.of("error", "Sign in to continue", "code", "AUTH_REQUIRED"));
        } catch (IllegalArgumentException e) {
            send(ex, 400, Map.of("error", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace(); send(ex, 500, Map.of("error", "Database operation failed"));
        } catch (Exception e) {
            e.printStackTrace(); send(ex, 500, Map.of("error", "Unexpected server error"));
        }
    }

    private static void signup(HttpExchange ex) throws Exception {
        JsonObject p = body(ex);
        String name = required(p, "name", "Your name is required").trim();
        String email = required(p, "email", "Email is required").trim().toLowerCase(Locale.ROOT);
        String password = required(p, "password", "Password is required");
        String storeName = required(p, "storeName", "Store name is required").trim();
        String location = value(p, "storeLocation", "").trim();
        if (name.length() > 160 || storeName.length() > 160 || location.length() > 200) throw new IllegalArgumentException("Please shorten the name or store details");
        if (!EMAIL.matcher(email).matches() || email.length() > 254) throw new IllegalArgumentException("Enter a valid email address");
        if (password.length() < 8 || password.length() > 200) throw new IllegalArgumentException("Use a password between 8 and 200 characters");
        String passwordHash = hashPassword(password.toCharArray());
        long storeId, userId;
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement("INSERT INTO stores(name,location) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, storeName); s.setString(2, location); s.executeUpdate();
                    try (ResultSet keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Store was not created"); storeId = keys.getLong(1); }
                }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO users(store_id,name,email,password_hash) VALUES(?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    s.setLong(1, storeId); s.setString(2, name); s.setString(3, email); s.setString(4, passwordHash); s.executeUpdate();
                    try (ResultSet keys = s.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Account was not created"); userId = keys.getLong(1); }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                if (e.getErrorCode() == 1062) throw new IllegalArgumentException("An account with that email already exists");
                throw e;
            } finally { c.setAutoCommit(true); }
        }
        String token = createSession(userId);
        setSessionCookie(ex, token, SESSION_DAYS * 24 * 60 * 60);
        Identity identity = new Identity(userId, storeId, name, email, "Store owner", storeName, location);
        send(ex, 201, identityMap(identity));
    }

    private static void login(HttpExchange ex) throws Exception {
        JsonObject p = body(ex);
        String email = required(p, "email", "Email is required").trim().toLowerCase(Locale.ROOT);
        String password = required(p, "password", "Password is required");
        Identity identity; String savedHash;
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement(
                "SELECT u.id,u.store_id,u.name,u.email,u.role,u.password_hash,st.name AS store_name,st.location FROM users u JOIN stores st ON st.id=u.store_id WHERE u.email=?")) {
            s.setString(1, email);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Email or password is incorrect");
                identity = new Identity(r.getLong("id"), r.getLong("store_id"), r.getString("name"), r.getString("email"), r.getString("role"), r.getString("store_name"), r.getString("location"));
                savedHash = r.getString("password_hash");
            }
        }
        if (!verifyPassword(password.toCharArray(), savedHash)) throw new IllegalArgumentException("Email or password is incorrect");
        String token = createSession(identity.userId());
        setSessionCookie(ex, token, SESSION_DAYS * 24 * 60 * 60);
        send(ex, 200, identityMap(identity));
    }

    private static void logout(HttpExchange ex) throws IOException, SQLException {
        String token = cookie(ex, SESSION_COOKIE);
        if (token != null) try (Connection c = connection(); PreparedStatement s = c.prepareStatement("DELETE FROM sessions WHERE token_hash=?")) { s.setString(1, sha256(token)); s.executeUpdate(); }
        setSessionCookie(ex, "", 0);
        send(ex, 200, Map.of("ok", true));
    }

    private static void updateProfile(HttpExchange ex, Identity identity) throws Exception {
        JsonObject p = body(ex);
        String name = required(p, "name", "Your name is required").trim();
        String email = required(p, "email", "Email is required").trim().toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.length() > 160) throw new IllegalArgumentException("Enter a valid name");
        if (!EMAIL.matcher(email).matches() || email.length() > 254) throw new IllegalArgumentException("Enter a valid email address");
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement("UPDATE users SET name=?,email=? WHERE id=?")) {
            s.setString(1, name); s.setString(2, email); s.setLong(3, identity.userId()); s.executeUpdate();
        } catch (SQLException e) { if (e.getErrorCode() == 1062) throw new IllegalArgumentException("An account with that email already exists"); throw e; }
        send(ex, 200, currentIdentity(identity.userId()));
    }

    private static void updateStore(HttpExchange ex, Identity identity) throws Exception {
        JsonObject p = body(ex);
        String name = required(p, "name", "Store name is required").trim();
        String location = value(p, "location", "").trim();
        if (name.isBlank() || name.length() > 160 || location.length() > 200) throw new IllegalArgumentException("Enter valid store details");
        try (Connection c = connection(); PreparedStatement s = c.prepareStatement("UPDATE stores SET name=?,location=? WHERE id=?")) {
            s.setString(1, name); s.setString(2, location); s.setLong(3, identity.storeId()); s.executeUpdate();
        }
        send(ex, 200, currentIdentity(identity.userId()));
    }

    private static Map<String,Object> currentIdentity(long userId) throws SQLException {
        try (Connection c=connection(); PreparedStatement s=c.prepareStatement("SELECT u.id,u.store_id,u.name,u.email,u.role,st.name AS store_name,st.location FROM users u JOIN stores st ON st.id=u.store_id WHERE u.id=?")) {
            s.setLong(1,userId); try(ResultSet r=s.executeQuery()){if(!r.next())throw new SQLException("Account not found");return identityMap(new Identity(r.getLong("id"),r.getLong("store_id"),r.getString("name"),r.getString("email"),r.getString("role"),r.getString("store_name"),r.getString("location")));}
        }
    }

    private static Map<String,Object> identityMap(Identity i) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("user",Map.of("id",i.userId(),"name",i.name(),"email",i.email(),"role",i.role()));
        m.put("store",Map.of("id",i.storeId(),"name",i.storeName(),"location",i.storeLocation()==null?"":i.storeLocation()));
        return m;
    }

    private static String createSession(long userId) throws SQLException {
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)")){
            s.setString(1,sha256(token));s.setLong(2,userId);s.setTimestamp(3,Timestamp.from(java.time.Instant.now().plus(SESSION_DAYS,ChronoUnit.DAYS)));s.executeUpdate();
        }return token;
    }

    private static Identity requireIdentity(HttpExchange ex) throws SQLException, Unauthenticated {
        String token=cookie(ex,SESSION_COOKIE);if(token==null||token.isBlank())throw new Unauthenticated();
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT u.id,u.store_id,u.name,u.email,u.role,st.name AS store_name,st.location FROM sessions se JOIN users u ON u.id=se.user_id JOIN stores st ON st.id=u.store_id WHERE se.token_hash=? AND se.expires_at>CURRENT_TIMESTAMP")){
            s.setString(1,sha256(token));try(ResultSet r=s.executeQuery()){if(!r.next())throw new Unauthenticated();return new Identity(r.getLong("id"),r.getLong("store_id"),r.getString("name"),r.getString("email"),r.getString("role"),r.getString("store_name"),r.getString("location"));}
        }
    }

    private static String cookie(HttpExchange ex,String name) {
        List<String> cookies=ex.getRequestHeaders().get("Cookie");if(cookies==null)return null;
        for(String header:cookies)for(String part:header.split(";")){String[] pair=part.trim().split("=",2);if(pair.length==2&&pair[0].equals(name))return pair[1];}
        return null;
    }
    private static void setSessionCookie(HttpExchange ex,String token,int maxAge) {
        String secure="https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto"))?"; Secure":"";
        ex.getResponseHeaders().add("Set-Cookie",SESSION_COOKIE+"="+token+"; Path=/; HttpOnly; SameSite=Lax; Max-Age="+maxAge+secure);
    }

    private static String hashPassword(char[] password) {
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);byte[] hash=derive(password,salt,PASSWORD_ITERATIONS);
        return "pbkdf2$"+PASSWORD_ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(hash);
    }
    private static boolean verifyPassword(char[] password,String saved) {
        try {String[] parts=saved.split("\\$",4);if(parts.length!=4||!parts[0].equals("pbkdf2"))return false;int iterations=Integer.parseInt(parts[1]);byte[] salt=Base64.getDecoder().decode(parts[2]),expected=Base64.getDecoder().decode(parts[3]);return MessageDigest.isEqual(expected,derive(password,salt,iterations));}
        catch(RuntimeException e){return false;}
    }
    private static byte[] derive(char[] password,byte[] salt,int iterations) {
        try {PBEKeySpec spec=new PBEKeySpec(password,salt,iterations,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();Arrays.fill(password,'\0');}}
        catch(Exception e){throw new IllegalStateException("Password hashing is unavailable",e);}
    }
    private static String sha256(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static String required(JsonObject p,String key,String message){if(!p.has(key)||p.get(key).isJsonNull()||p.get(key).getAsString().isBlank())throw new IllegalArgumentException(message);return p.get(key).getAsString();}

    private static void products(HttpExchange ex,String method,Identity identity)throws Exception{
        if(method.equals("GET")){send(ex,200,readProducts(identity.storeId()));return;}
        if(method.equals("POST")){JsonObject p=body(ex);validateProduct(p);send(ex,201,createProduct(p,identity.storeId()));return;}
        send(ex,405,Map.of("error","Method not allowed"));
    }
    private static List<Map<String,Object>> readProducts(long storeId)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT id,sku,name,category,price,stock,expiry_date,icon FROM products WHERE store_id=? ORDER BY created_at DESC")){s.setLong(1,storeId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(productMap(r));}}return out;
    }
    private static Map<String,Object> createProduct(JsonObject p,long storeId)throws SQLException{
        String sku=value(p,"sku","SKU-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT));
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO products(store_id,sku,name,category,price,stock,expiry_date,icon) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            s.setLong(1,storeId);s.setString(2,sku);s.setString(3,p.get("name").getAsString());s.setString(4,p.get("category").getAsString());s.setBigDecimal(5,p.get("price").getAsBigDecimal());s.setInt(6,p.get("stock").getAsInt());setExpiry(s,7,p);s.setString(8,value(p,"icon","📦"));s.executeUpdate();try(ResultSet k=s.getGeneratedKeys()){if(k.next())return readProduct(k.getLong(1),storeId);}}
        throw new SQLException("Product insert did not return an ID");
    }
    private static void productById(HttpExchange ex,String method,String rawId,Identity identity)throws Exception{
        long id=parseId(rawId);long storeId=identity.storeId();
        if(method.equals("GET")){Map<String,Object> p=readProduct(id,storeId);if(p==null)send(ex,404,Map.of("error","Product not found"));else send(ex,200,p);return;}
        if(method.equals("PUT")||method.equals("PATCH")){JsonObject p=body(ex);validateProduct(p);try(Connection c=connection();PreparedStatement s=c.prepareStatement("UPDATE products SET name=?,category=?,price=?,stock=?,expiry_date=?,icon=? WHERE id=? AND store_id=?")){s.setString(1,p.get("name").getAsString());s.setString(2,p.get("category").getAsString());s.setBigDecimal(3,p.get("price").getAsBigDecimal());s.setInt(4,p.get("stock").getAsInt());setExpiry(s,5,p);s.setString(6,value(p,"icon","📦"));s.setLong(7,id);s.setLong(8,storeId);if(s.executeUpdate()==0){send(ex,404,Map.of("error","Product not found"));return;}}send(ex,200,readProduct(id,storeId));return;}
        if(method.equals("DELETE")){try(Connection c=connection();PreparedStatement s=c.prepareStatement("DELETE FROM products WHERE id=? AND store_id=?")){s.setLong(1,id);s.setLong(2,storeId);if(s.executeUpdate()==0){send(ex,404,Map.of("error","Product not found"));return;}}send(ex,200,Map.of("deleted",true));return;}
        send(ex,405,Map.of("error","Method not allowed"));
    }
    private static Map<String,Object> readProduct(long id,long storeId)throws SQLException{
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT id,sku,name,category,price,stock,expiry_date,icon FROM products WHERE id=? AND store_id=?")){s.setLong(1,id);s.setLong(2,storeId);try(ResultSet r=s.executeQuery()){return r.next()?productMap(r):null;}}
    }
    private static Map<String,Object> productMap(ResultSet r)throws SQLException{
        Map<String,Object> p=new LinkedHashMap<>();p.put("id",r.getLong("id"));p.put("sku",r.getString("sku"));p.put("name",r.getString("name"));p.put("category",r.getString("category"));p.put("price",r.getBigDecimal("price"));p.put("stock",r.getInt("stock"));java.sql.Date d=r.getDate("expiry_date");p.put("expiry",d==null?null:d.toLocalDate().toString());p.put("icon",r.getString("icon"));return p;
    }
    private static void setExpiry(PreparedStatement s,int index,JsonObject p)throws SQLException{if(!p.has("expiry")||p.get("expiry").isJsonNull()||p.get("expiry").getAsString().isBlank())s.setNull(index,Types.DATE);else s.setDate(index,java.sql.Date.valueOf(LocalDate.parse(p.get("expiry").getAsString())));}
    private static void validateProduct(JsonObject p){for(String k:List.of("name","category","price","stock"))if(!p.has(k)||p.get(k).isJsonNull())throw new IllegalArgumentException("Missing required field: "+k);if(p.get("name").getAsString().isBlank()||p.get("name").getAsString().length()>160)throw new IllegalArgumentException("Enter a valid product name");if(p.get("price").getAsBigDecimal().signum()<0||p.get("stock").getAsInt()<0)throw new IllegalArgumentException("Price and quantity cannot be negative");if(p.has("expiry")&&!p.get("expiry").isJsonNull()&&!p.get("expiry").getAsString().isBlank())LocalDate.parse(p.get("expiry").getAsString());}
    private static long parseId(String x){try{return Long.parseLong(x);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid record ID");}}
    private static JsonObject body(HttpExchange ex)throws IOException{try(InputStream in=ex.getRequestBody()){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}}
    private static String value(JsonObject p,String key,String fallback){return p.has(key)&&!p.get(key).isJsonNull()?p.get(key).getAsString():fallback;}

    private static List<Map<String,Object>> readSuppliers(long storeId)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT id,name,category,phone,email FROM suppliers WHERE store_id=? ORDER BY name")){s.setLong(1,storeId);try(ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getLong("id"));m.put("name",r.getString("name"));m.put("category",r.getString("category"));m.put("phone",r.getString("phone"));m.put("email",r.getString("email"));out.add(m);}}}return out;
    }
    private static void createSupplier(HttpExchange ex,Identity identity)throws Exception{
        JsonObject p=body(ex);String name=required(p,"name","Supplier name is required").trim(),category=required(p,"category","Supplier category is required").trim();
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO suppliers(store_id,name,category,phone,email) VALUES(?,?,?,?,?)")){s.setLong(1,identity.storeId());s.setString(2,name);s.setString(3,category);s.setString(4,value(p,"phone",""));s.setString(5,value(p,"email",""));s.executeUpdate();}send(ex,201,readSuppliers(identity.storeId()));
    }
    private static void deleteSupplier(HttpExchange ex,String raw,Identity identity)throws Exception{
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("DELETE FROM suppliers WHERE id=? AND store_id=?")){s.setLong(1,parseId(raw));s.setLong(2,identity.storeId());if(s.executeUpdate()==0){send(ex,404,Map.of("error","Supplier not found"));return;}}send(ex,200,Map.of("deleted",true));
    }
    private static Map<String,String> readSettings(long storeId)throws SQLException{
        Map<String,String> m=new HashMap<>();try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT setting_key,setting_value FROM store_settings WHERE store_id=?")){s.setLong(1,storeId);try(ResultSet r=s.executeQuery()){while(r.next())m.put(r.getString(1),r.getString(2));}}m.putIfAbsent("lowStockThreshold","15");return m;
    }
    private static void updateSettings(HttpExchange ex,Identity identity)throws Exception{
        JsonObject p=body(ex);if(p.has("lowStockThreshold")){int threshold;try{threshold=p.get("lowStockThreshold").getAsInt();}catch(RuntimeException e){throw new IllegalArgumentException("Low stock threshold must be a whole number between 1 and 500");}if(threshold<1||threshold>500)throw new IllegalArgumentException("Low stock threshold must be between 1 and 500");p.addProperty("lowStockThreshold",String.valueOf(threshold));}try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO store_settings(store_id,setting_key,setting_value) VALUES(?,?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)")){for(String key:List.of("lowStockThreshold")){if(p.has(key)){s.setLong(1,identity.storeId());s.setString(2,key);s.setString(3,p.get(key).getAsString());s.addBatch();}}s.executeBatch();}send(ex,200,readSettings(identity.storeId()));
    }

    private static Map<String,Object> readSales(long storeId)throws SQLException{
        Map<String,Object> result=new LinkedHashMap<>();
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT COUNT(*),COALESCE(SUM(units),0),COALESCE(SUM(amount),0) FROM sales WHERE store_id=?")){s.setLong(1,storeId);try(ResultSet r=s.executeQuery()){r.next();result.put("count",r.getLong(1));result.put("units",r.getLong(2));result.put("revenue",r.getBigDecimal(3));}}
        List<Map<String,Object>> recent=new ArrayList<>();
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT id,product_name,units,unit_price,amount,sold_at FROM sales WHERE store_id=? ORDER BY sold_at DESC,id DESC LIMIT 50")){s.setLong(1,storeId);try(ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> sale=new LinkedHashMap<>();sale.put("id",r.getLong("id"));sale.put("product",r.getString("product_name"));sale.put("units",r.getInt("units"));sale.put("unitPrice",r.getBigDecimal("unit_price"));sale.put("amount",r.getBigDecimal("amount"));sale.put("soldAt",r.getTimestamp("sold_at").toString());recent.add(sale);}}}
        result.put("recent",recent);return result;
    }

    private static void recordSale(HttpExchange ex,Identity identity)throws Exception{
        JsonObject p=body(ex);if(!p.has("productId")||!p.has("units"))throw new IllegalArgumentException("Choose a product and sale quantity");
        long productId=p.get("productId").getAsLong();int units=p.get("units").getAsInt();if(productId<=0||units<1||units>100000)throw new IllegalArgumentException("Enter a valid product and quantity");
        try(Connection c=connection()){
            c.setAutoCommit(false);
            try(PreparedStatement product=c.prepareStatement("SELECT name,price,stock FROM products WHERE id=? AND store_id=? FOR UPDATE")){
                product.setLong(1,productId);product.setLong(2,identity.storeId());
                try(ResultSet r=product.executeQuery()){
                    if(!r.next()){c.rollback();throw new IllegalArgumentException("Product not found in this store");}
                    String name=r.getString("name");BigDecimal price=r.getBigDecimal("price");int stock=r.getInt("stock");
                    if(units>stock){c.rollback();throw new IllegalArgumentException("Only "+stock+" units are currently in stock");}
                    BigDecimal amount=price.multiply(BigDecimal.valueOf(units));
                    try(PreparedStatement update=c.prepareStatement("UPDATE products SET stock=stock-? WHERE id=? AND store_id=?");PreparedStatement insert=c.prepareStatement("INSERT INTO sales(store_id,product_id,product_name,units,unit_price,amount) VALUES(?,?,?,?,?,?)")){
                        update.setInt(1,units);update.setLong(2,productId);update.setLong(3,identity.storeId());update.executeUpdate();
                        insert.setLong(1,identity.storeId());insert.setLong(2,productId);insert.setString(3,name);insert.setInt(4,units);insert.setBigDecimal(5,price);insert.setBigDecimal(6,amount);insert.executeUpdate();
                    }
                }
            }
            c.commit();
        }
        send(ex,201,readSales(identity.storeId()));
    }

    private static void servePage(HttpExchange ex)throws IOException{
        String path=ex.getRequestURI().getPath();
        if(path.equals("/")||path.equals("/index.html")){sendHtml(ex,200,"<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Stockroom</title></head><body><div id=\"app\"></div><script src=\"/gwt/stockroom/stockroom.nocache.js\"></script></body></html>");return;}
        if(path.contains("..")){send(ex,400,Map.of("error","Invalid path"));return;}
        try(InputStream in=StockroomApplication.class.getResourceAsStream("/public"+path)){if(in==null){send(ex,404,Map.of("error","Page not found"));return;}byte[] data=in.readAllBytes();String type=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".svg")?"image/svg+xml":"application/octet-stream";ex.getResponseHeaders().set("Content-Type",type);ex.getResponseHeaders().set("Cache-Control",path.contains("nocache")?"no-cache":"public, max-age=31536000, immutable");ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);}finally{ex.close();}
    }
    private static void sendHtml(HttpExchange ex,int code,String page)throws IOException{byte[] bytes=page.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(code,bytes.length);ex.getResponseBody().write(bytes);ex.close();}
    private static void send(HttpExchange ex,int code,Object payload)throws IOException{
        byte[] bytes=JSON.toJson(payload).getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(code,bytes.length);ex.getResponseBody().write(bytes);ex.close();
    }
    private static final class Unauthenticated extends Exception {}
}

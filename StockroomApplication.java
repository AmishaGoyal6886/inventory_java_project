package com.stockroom;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class StockroomApplication {
    private static final Gson JSON = new Gson();
    private static String dbUrl, dbUser, dbPassword;

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        dbUrl = env("DB_URL", "jdbc:mysql://localhost:3306/stockroom?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        dbUser = env("DB_USER", "root");
        dbPassword = env("DB_PASSWORD", "");
        initializeDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/", StockroomApplication::handleApi);
        server.createContext("/", StockroomApplication::servePage);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(12));
        server.start();
        System.out.printf("Stockroom is running at http://localhost:%d%n", port);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null ? fallback : value;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private static void initializeDatabase() throws SQLException {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS products (id BIGINT PRIMARY KEY AUTO_INCREMENT, sku VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL, category VARCHAR(60) NOT NULL, price DECIMAL(12,2) NOT NULL DEFAULT 0, stock INT NOT NULL DEFAULT 0, expiry_date DATE NULL, icon VARCHAR(12) NOT NULL DEFAULT '📦', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX idx_products_expiry (expiry_date), INDEX idx_products_category (category))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS suppliers (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(160) NOT NULL, category VARCHAR(60) NOT NULL, phone VARCHAR(40) NOT NULL DEFAULT '', email VARCHAR(160) NOT NULL DEFAULT '', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS app_settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value TEXT NOT NULL, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        }
    }

    private static void handleApi(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (path.equals("/api/health")) { send(ex, 200, Map.of("ok", true, "database", "connected")); return; }
            if (path.equals("/api/products") || path.equals("/api/products/")) { products(ex, method); return; }
            if (path.startsWith("/api/products/")) { productById(ex, method, path.substring("/api/products/".length())); return; }
            if (path.equals("/api/suppliers") && method.equals("GET")) { send(ex, 200, readSuppliers()); return; }
            if (path.equals("/api/suppliers") && method.equals("POST")) { createSupplier(ex); return; }
            if (path.startsWith("/api/suppliers/") && method.equals("DELETE")) { deleteSupplier(ex, path.substring("/api/suppliers/".length())); return; }
            if (path.equals("/api/settings") && method.equals("GET")) { send(ex, 200, readSettings()); return; }
            if (path.equals("/api/settings") && method.equals("PUT")) { updateSettings(ex); return; }
            send(ex, 404, Map.of("error", "API endpoint not found"));
        } catch (IllegalArgumentException e) {
            send(ex, 400, Map.of("error", e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace(); send(ex, 500, Map.of("error", "Database operation failed"));
        } catch (Exception e) {
            e.printStackTrace(); send(ex, 500, Map.of("error", "Unexpected server error"));
        }
    }

    private static void products(HttpExchange ex, String method) throws Exception {
        if (method.equals("GET")) { send(ex, 200, readProducts()); return; }
        if (method.equals("POST")) { JsonObject p = body(ex); validateProduct(p, true); send(ex, 201, createProduct(p)); return; }
        send(ex, 405, Map.of("error", "Method not allowed"));
    }

    private static List<Map<String,Object>> readProducts() throws SQLException {
        List<Map<String,Object>> out = new ArrayList<>();
        try (Connection c=connection(); PreparedStatement s=c.prepareStatement("SELECT id,sku,name,category,price,stock,expiry_date,icon FROM products ORDER BY created_at DESC"); ResultSet r=s.executeQuery()) {
            while(r.next()) out.add(productMap(r));
        } return out;
    }

    private static Map<String,Object> createProduct(JsonObject p) throws SQLException {
        String sku = value(p,"sku", "SKU-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());
        try(Connection c=connection(); PreparedStatement s=c.prepareStatement("INSERT INTO products(sku,name,category,price,stock,expiry_date,icon) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1,sku); s.setString(2,p.get("name").getAsString()); s.setString(3,p.get("category").getAsString()); s.setBigDecimal(4,p.get("price").getAsBigDecimal()); s.setInt(5,p.get("stock").getAsInt()); setExpiry(s,6,p); s.setString(7,value(p,"icon","📦")); s.executeUpdate();
            try(ResultSet k=s.getGeneratedKeys()){if(k.next()) return readProduct(k.getLong(1));}
        } throw new SQLException("Product insert did not return an ID");
    }

    private static void productById(HttpExchange ex,String method,String rawId) throws Exception {
        long id=parseId(rawId);
        if(method.equals("GET")){Map<String,Object> p=readProduct(id);if(p==null)send(ex,404,Map.of("error","Product not found"));else send(ex,200,p);return;}
        if(method.equals("PUT")||method.equals("PATCH")){JsonObject p=body(ex);validateProduct(p,false);try(Connection c=connection();PreparedStatement s=c.prepareStatement("UPDATE products SET name=?,category=?,price=?,stock=?,expiry_date=?,icon=? WHERE id=?")){s.setString(1,p.get("name").getAsString());s.setString(2,p.get("category").getAsString());s.setBigDecimal(3,p.get("price").getAsBigDecimal());s.setInt(4,p.get("stock").getAsInt());setExpiry(s,5,p);s.setString(6,value(p,"icon","📦"));s.setLong(7,id);if(s.executeUpdate()==0){send(ex,404,Map.of("error","Product not found"));return;}}send(ex,200,readProduct(id));return;}
        if(method.equals("DELETE")){try(Connection c=connection();PreparedStatement s=c.prepareStatement("DELETE FROM products WHERE id=?")){s.setLong(1,id);if(s.executeUpdate()==0){send(ex,404,Map.of("error","Product not found"));return;}}send(ex,200,Map.of("deleted",true));return;}
        send(ex,405,Map.of("error","Method not allowed"));
    }

    private static Map<String,Object> readProduct(long id) throws SQLException {
        try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT id,sku,name,category,price,stock,expiry_date,icon FROM products WHERE id=?")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?productMap(r):null;}}
    }
    private static Map<String,Object> productMap(ResultSet r)throws SQLException{
        Map<String,Object> p=new LinkedHashMap<>();p.put("id",r.getLong("id"));p.put("sku",r.getString("sku"));p.put("name",r.getString("name"));p.put("category",r.getString("category"));p.put("price",r.getBigDecimal("price"));p.put("stock",r.getInt("stock"));java.sql.Date d=r.getDate("expiry_date");p.put("expiry",d==null?null:d.toLocalDate().toString());p.put("icon",r.getString("icon"));return p;
    }
    private static void setExpiry(PreparedStatement s,int index,JsonObject p)throws SQLException{if(!p.has("expiry")||p.get("expiry").isJsonNull()||p.get("expiry").getAsString().isBlank())s.setNull(index,Types.DATE);else s.setDate(index,Date.valueOf(LocalDate.parse(p.get("expiry").getAsString())));}
    private static void validateProduct(JsonObject p,boolean creating){for(String k:List.of("name","category","price","stock"))if(!p.has(k)||p.get(k).isJsonNull())throw new IllegalArgumentException("Missing required field: "+k);if(p.get("name").getAsString().isBlank())throw new IllegalArgumentException("Product name is required");if(p.get("price").getAsBigDecimal().signum()<0||p.get("stock").getAsInt()<0)throw new IllegalArgumentException("Price and quantity cannot be negative");if(p.has("expiry")&&!p.get("expiry").isJsonNull()&&!p.get("expiry").getAsString().isBlank())LocalDate.parse(p.get("expiry").getAsString());}
    private static long parseId(String x){try{return Long.parseLong(x);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid record ID");}}
    private static JsonObject body(HttpExchange ex)throws IOException{try(InputStream in=ex.getRequestBody()){return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();}}
    private static String value(JsonObject p,String key,String fallback){return p.has(key)&&!p.get(key).isJsonNull()?p.get(key).getAsString():fallback;}

    private static List<Map<String,Object>> readSuppliers()throws SQLException{List<Map<String,Object>> out=new ArrayList<>();try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT id,name,category,phone,email FROM suppliers ORDER BY name");ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getLong("id"));m.put("name",r.getString("name"));m.put("category",r.getString("category"));m.put("phone",r.getString("phone"));m.put("email",r.getString("email"));out.add(m);}}return out;}
    private static void createSupplier(HttpExchange ex)throws Exception{JsonObject p=body(ex);if(!p.has("name")||p.get("name").getAsString().isBlank()||!p.has("category"))throw new IllegalArgumentException("Supplier name and category are required");try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO suppliers(name,category,phone,email) VALUES(?,?,?,?)")){s.setString(1,p.get("name").getAsString());s.setString(2,p.get("category").getAsString());s.setString(3,value(p,"phone",""));s.setString(4,value(p,"email",""));s.executeUpdate();}send(ex,201,readSuppliers());}
    private static void deleteSupplier(HttpExchange ex,String raw)throws Exception{try(Connection c=connection();PreparedStatement s=c.prepareStatement("DELETE FROM suppliers WHERE id=?")){s.setLong(1,parseId(raw));if(s.executeUpdate()==0){send(ex,404,Map.of("error","Supplier not found"));return;}}send(ex,200,Map.of("deleted",true));}
    private static Map<String,String> readSettings()throws SQLException{Map<String,String> m=new HashMap<>();try(Connection c=connection();PreparedStatement s=c.prepareStatement("SELECT setting_key,setting_value FROM app_settings");ResultSet r=s.executeQuery()){while(r.next())m.put(r.getString(1),r.getString(2));}m.putIfAbsent("lowStockThreshold","15");m.putIfAbsent("storeName","Green Basket Market");m.putIfAbsent("storeLocation","Main store · Mumbai");return m;}
    private static void updateSettings(HttpExchange ex)throws Exception{JsonObject p=body(ex);try(Connection c=connection();PreparedStatement s=c.prepareStatement("INSERT INTO app_settings(setting_key,setting_value) VALUES(?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)")){for(String key:List.of("lowStockThreshold","storeName","storeLocation","ownerName","ownerEmail")){if(p.has(key)){s.setString(1,key);s.setString(2,p.get(key).getAsString());s.addBatch();}}s.executeBatch();}send(ex,200,readSettings());}

    private static void servePage(HttpExchange ex)throws IOException{String path=ex.getRequestURI().getPath();if(path.equals("/"))path="/index.html";if(path.contains("..")){send(ex,400,Map.of("error","Invalid path"));return;}try(InputStream in=StockroomApplication.class.getResourceAsStream("/public"+path)){if(in==null){send(ex,404,Map.of("error","Page not found"));return;}byte[] data=in.readAllBytes();String type=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".svg")?"image/svg+xml":"text/html; charset=utf-8";ex.getResponseHeaders().set("Content-Type",type);ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);}finally{ex.close();}}
    private static void send(HttpExchange ex,int code,Object payload)throws IOException{byte[] bytes=JSON.toJson(payload).getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(code,bytes.length);ex.getResponseBody().write(bytes);ex.close();}
}

package com.gordlby.insy;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import org.json.*;
import java.sql.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * INSY Webshop Server
 */
public class Server {

    /**
     * Port to bind to for HTTP service
     */
    private int port = 8000;

    /**
     * Connect to the database
     * @throws IOException
     */
    Connection setupDB()  {
        String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        Properties dbProps = new Properties();
        try {
            dbProps.load(new FileInputStream(rootPath.replace("Server/INSY_WiSe012/target/classes/", "") + "db.properties"));
            String url = dbProps.getProperty("url");
            String user = dbProps.getProperty("user");
            String password = dbProps.getProperty("password");

            // Connect to the database
            return DriverManager.getConnection(url, user, password);
        } catch (Exception _) {
            System.out.println("Error while connecting to the database");
        }
        return null;
    }

    /**
     * Startup the Webserver
     * @throws IOException
     */
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/articles", new ArticlesHandler());
        server.createContext("/clients", new ClientsHandler());
        server.createContext("/placeOrder", new PlaceOrderHandler());
        server.createContext("/orders", new OrdersHandler());
        server.createContext("/", new IndexHandler());

        server.start();
    }


    public static void main(String[] args) throws Throwable {
        Server webshop = new Server();
        webshop.start();
        System.out.println("Webshop running at http://127.0.0.1:" + webshop.port);
    }


    /**
     * Handler for listing all articles
     */
    class ArticlesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();
            JSONArray res = new JSONArray();

            if (conn != null) {
                try {
                    String sql = "SELECT * FROM articles";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        JSONObject article = new JSONObject();
                        article.put("id", rs.getInt("id"));
                        article.put("description", rs.getString("description"));
                        article.put("price", rs.getInt("price"));
                        article.put("amount", rs.getInt("amount"));
                        res.put(article);
                    }

                    rs.close();
                    stmt.close();
                    conn.close();
                } catch (Exception _) {
                    System.out.println("Error while reading articles");
                }
            }

            answerRequest(t,res.toString());
        }

    }

    /**
     * Handler for listing all clients
     */
    class ClientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();
            JSONArray res = new JSONArray();

            if (conn != null) {
                try {
                    String sql = "SELECT * FROM clients";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        JSONObject clients = new JSONObject();
                        clients.put("id", rs.getInt("id"));
                        clients.put("name", rs.getString("name"));
                        clients.put("address", rs.getString("address"));
                        res.put(clients);
                    }

                    rs.close();
                    stmt.close();
                    conn.close();
                } catch (Exception _) {
                    System.out.println("Error while reading clients");
                }
            }


            answerRequest(t,res.toString());
        }

    }


    /**
     * Handler for listing all orders
     */
    class OrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();

            JSONArray res = new JSONArray();

            if (conn != null) {
                try {
                    String sql = "SELECT orders.id, clients.name, COUNT(order_lines.id) AS lines, SUM(order_lines.amount) AS total FROM clients JOIN orders ON orders.client_id = clients.id JOIN order_lines ON orders.id = order_lines.order_id GROUP BY orders.id, clients.name";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    ResultSet rs = stmt.executeQuery();

                    while (rs.next()) {
                        JSONObject clients = new JSONObject();
                        clients.put("id", rs.getInt("id"));
                        clients.put("client", rs.getString("name"));
                        clients.put("lines", rs.getString("lines"));
                        clients.put("total", rs.getDouble("total"));
                        res.put(clients);
                    }

                    rs.close();
                    stmt.close();
                    conn.close();
                } catch (Exception _) {
                    System.out.println("Error while reading clients");
                }
            }

            answerRequest(t, res.toString());

        }
    }


    /**
     * Handler class to place an order
     */
    class PlaceOrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Dev Test 1");
            Connection conn = setupDB();
            System.out.println("Dev Test 1,5");
            String query = t.getRequestURI().getQuery();
            if (query == null) {
                answerRequest(t, "{\"error\": \"No query parameters provided\"}");
                return;
            }
            Map<String, String> params = queryToMap(query);
            int client_id = Integer.parseInt(params.get("client_id"));

            String response;
            int order_id = -1;
            try {
                if (conn == null) {
                    throw new SQLException("Database connection failed.");
                }

                order_id = getNextOrderId(conn);

                insertNewOrder(conn, order_id, client_id);


                for (int i = 1; i <= (params.size()-1) / 2; ++i ){
                    int article_id = Integer.parseInt(params.get("article_id_"+i));
                    int amount = Integer.parseInt(params.get("amount_"+i));


                    int available = getArticleStock(conn, article_id);


                    if (available < amount)
                        throw new IllegalArgumentException(String.format("Not enough items of article #%d available", article_id));

                    updateArticleStock(conn, article_id, available - amount);

                    insertOrderLine(conn, order_id, article_id, amount);
                }

                response = new JSONObject().put("order_id", order_id).toString();
                System.out.println("Dev Test 6");
            } catch (Exception e) {
                System.out.println("Dev Test Catch 7");
                response = new JSONObject().put("error", e.getMessage()).toString();
                System.out.println("Error while place order");
                System.out.println("Dev Test Catch 8");
                System.out.println(e);
            }
            System.out.println("Dev Test 9");

            answerRequest(t, response);


        }

        private void insertOrderLine(Connection conn, int orderId, int articleId, int amount) throws SQLException {
            String sql = "INSERT INTO order_lines (order_id, article_id, amount) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, orderId);
                stmt.setInt(2, articleId);
                stmt.setInt(3, amount);
                stmt.executeUpdate();
            }
        }

        private void updateArticleStock(Connection conn, int articleId, int i) throws SQLException {
            String sql = "UPDATE articles SET amount = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, i);
                stmt.setInt(2, articleId);
                stmt.executeUpdate();
            }
        }

        private int getArticleStock(Connection conn, int articleId) throws SQLException {
            String sql = "SELECT amount FROM articles WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, articleId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("amount");
                    }
                }
            }
            throw new SQLException(String.format("Article #%d not found.", articleId));
        }

        private void insertNewOrder(Connection conn, int orderId, int clientId) throws SQLException {
            String sql = "INSERT INTO orders (id, client_id) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, orderId);
                stmt.setInt(2, clientId);
                stmt.executeUpdate();
            }
        }

        private int getNextOrderId(Connection conn) throws SQLException {
            String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM orders";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            throw new SQLException("Failed to retrieve next order id");
        }
    }

    /**
     * Handler for listing static index page
     */
    class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "<!doctype html>\n" +
                    "<html><head><title>INSY Webshop</title><link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/water.css@2/out/water.css\"></head>" +
                    "<body><h1>INSY Pseudo-Webshop</h1>" +
                    "<h2>Verf&uuml;gbare Endpoints:</h2><dl>"+
                    "<dt>Alle Artikel anzeigen:</dt><dd><a href=\"http://127.0.0.1:"+port+"/articles\">http://127.0.0.1:"+port+"/articles</a></dd>"+
                    "<dt>Alle Bestellungen anzeigen:</dt><dd><a href=\"http://127.0.0.1:"+port+"/orders\">http://127.0.0.1:"+port+"/orders</a></dd>"+
                    "<dt>Alle Kunden anzeigen:</dt><dd><a href=\"http://127.0.0.1:"+port+"/clients\">http://127.0.0.1:"+port+"/clients</a></dd>"+
                    "<dt>Bestellung abschicken:</dt><dd><a href=\"http://127.0.0.1:"+port+"/placeOrder?client_id=<client_id>&article_id_1=<article_id_1>&amount_1=<amount_1&article_id_2=<article_id_2>&amount_2=<amount_2>\">http://127.0.0.1:"+port+"/placeOrder?client_id=&lt;client_id>&article_id_1=&lt;article_id_1>&amount_1=&lt;amount_1>&article_id_2=&lt;article_id_2>&amount_2=&lt;amount_2></a></dd>"+
                    "</dl></body></html>";

            answerRequest(t, response);
        }

    }


    /**
     * Helper function to send an answer given as a String back to the browser
     * @param t HttpExchange of the request
     * @param response Answer to send
     * @throws IOException
     */
    private void answerRequest(HttpExchange t, String response) throws IOException {
        byte[] payload = response.getBytes();
        t.sendResponseHeaders(200, payload.length);
        OutputStream os = t.getResponseBody();
        os.write(payload);
        os.close();
    }

    /**
     * Helper method to parse query paramaters
     * @param query
     * @return
     */
    public static Map<String, String> queryToMap(String query){
        Map<String, String> result = new HashMap<String, String>();
        for (String param : query.split("&")) {
            String pair[] = param.split("=");
            if (pair.length>1) {
                result.put(pair[0], pair[1]);
            }else{
                result.put(pair[0], "");
            }
        }
        return result;
    }


}

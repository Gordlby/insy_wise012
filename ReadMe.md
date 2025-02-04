# Aufgabenstellung

Im ersten Teil der Übung lest euch in die Thematik [JDBC](https://about:blank/) ein.

Studiert hierfür die beiden angebotenen Unterlagen [JDBC](https://about:blank/).pdf

Versucht dann in einem kurzen (1 Seite A4) Dokument zusammenzufassen, was die technischen Voraussetzungen für [JDBC](https://about:blank/) sind.

Ladet euch gegebenfalls Dinge herunter und nehmt diese in Betrieb. Protokolliert eure Ausführungen.

# [JDBC](https://about:blank/)

## Ausgangslage

Gegeben ist ein fast fertiges Programm, ein Mini-Webshop, der aus einem einfachen Webservice besteht. Das Webservice kann Artikel, Kunden und Bestellungen anzeigen, sowie neue Bestellungen aufgeben. Das Programm ist bis auf ein kleines Detail fertig: Die Anbindung an die Datenbank wurde noch nicht implementiert, das ist nun deine Aufgabe. Die entsprechenden Stellen im Sourcecode sind mit // TODO gekennzeichnet.

Richte zunaechst eine leere Postgres-Datenbank ein und lege die entsprechenden Tabellen und Testdaten an, indem du die gegebene `webshop.sql` ausfuehrst:

```
postgres=# \c webshop
webshop=# \i webshop.sql

```

Danach richte in der `db.properties` die Zugangsdaten zum Webserver ein und starte diesen. Dafuer benoetigst du noch den [JDBC](https://about:blank/)-Treiber fuer Postgres [1] sowie die JSON-Java Library [2]. Du kannst diese mittels Gradle installieren oder die entsprechende JAR-Dateien selbst herunterladen und in dein Projekt einbinden. Alternativ lassen sich die Libraries mittels gradle ueber die folgenden Dependencies einrichten:

```
dependencies {
     implementation 'org.json:json:20171018'
     implementation 'org.postgresql:postgresql:42.2.8'
}

```

## Funktionsweise des Webshops

Standardmaessig laeuft der Webshop auf Port 8000; falls dieser Port bei dir belegt ist, kannst du ihn mittels dem Property `Server.port` aendern. Du kannst das laufende Webservice dann entsprechend unter [http://127.0.0.1:8000](http://127.0.0.1:8000/) aufrufen. Wie du siehst, existieren Methoden zum Anzeigen von Kunden, Bestellungen, und Artikeln und zum Aufgeben von Bestellungen. Alle Methoden koennen im Browser per Adresszeile (d.h. per GET-Request) aufgerufen werden und liefern eine Antwort im JSON-Format.

[1] [https://jdbc.postgresql.org/download.html](https://github.com/stleary/JSON-java)

[2] https://github.com/stleary/JSON-java

# Protokoll

Da wir für diese Aufgabe eine Postgres Datenbank brauchen habe ich zuerst eine

`docker-compose.yml`erstellt:

```yaml
services:
  postgres:
    image: postgres:latest
    container_name: postgresWiSe012
    environment:
      POSTGRES_USER: postgrestgm
      POSTGRES_PASSWORD: gordlbypassword
      POSTGRES_DB: webshop
    ports:
      - "5432:5432"
    volumes:
     - postgres-data:/var/lib/postgresql/data

  pgadmin:
    image: dpage/pgadmin4
    container_name: pgadminWiSe012
    environment:
      PGADMIN_DEFAULT_EMAIL: postgrestgm@gordlby.at
      PGADMIN_DEFAULT_PASSWORD: M5Wv@4Vr*^1r3@85sS3N
    ports:
      - "5050:80"
    depends_on:
      - postgres
    volumes:
      - /Users/marcprochazka/Library/CloudStorage/OneDrive-tgm-DieSchulederTechnik/01_4BHIT/INSY/WiSe012/data/pgadmin:/var/lib/pgadmin

volumes:
  postgres-data:
```

Und um zukünftig Fehler leichter ausbessern zu können habe ich es auf mein [GitHub](https://github.com/gordlby) gepushed.

Ich habe mich danach beim pgadmin angemeldet, die Datenbank verknüpft über die interne IP und über DataGrip habe ich die `webshop.sql` ausgeführt.

Nach Fertigstellung der Datenbank habe ich die `Server.java`  den neuen Begebenheiten angepasst.

Erst die Datenbank verbindung:

## `function setupDB`

```java
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
```

Dann mussten für jede der Unterseiten die dafür erstellten Handler angepasst werden:

## `class ArticlesHandler`

```java
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
```

## `class ClientsHandler`

```java
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
```

## `class OrdersHandler`

```java
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
```

Zuletzt habe ich die Insert Funktion umfunktionieren müssen:

## `class PlaceOrderHandler`

```java
class PlaceOrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            Connection conn = setupDB();
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
```
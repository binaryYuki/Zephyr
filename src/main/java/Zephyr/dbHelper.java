package Zephyr;

import io.github.cdimascio.dotenv.Dotenv;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import jakarta.persistence.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Database helper class for managing database connections, ORM, and migrations.
 * This class uses HikariCP for connection pooling, Hibernate for ORM, and Flyway for database migrations.
 * It also loads environment variables using dotenv.
 *
 * @author binaryYuki
 */
public class dbHelper {

  private static HikariDataSource dataSource;
  private static EntityManagerFactory entityManagerFactory;

  /**
   * Constructor for dbHelper.
   * Initializes HikariCP, Hibernate, and Flyway.
   *
   * @author binaryYuki
   * @param vertx Vertx instance
   */
  public dbHelper(Vertx vertx) {
    // Load the .env file
    Dotenv dotenv = Dotenv.load();

    // HikariCP configuration
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:mysql://" + dotenv.get("DB_HOST") + ":" + dotenv.get("DB_PORT") + "/" + dotenv.get("DB_NAME"));
    config.setUsername(dotenv.get("DB_USER"));
    config.setPassword(dotenv.get("DB_PWD"));
    config.setMaximumPoolSize(2);

    dataSource = new HikariDataSource(config);

    Map<String, String> properties = new HashMap<>();
    properties.put("jakarta.persistence.jdbc.url", dotenv.get("DB_URL"));
    properties.put("jakarta.persistence.jdbc.user", dotenv.get("DB_USER"));
    properties.put("jakarta.persistence.jdbc.password", dotenv.get("DB_PASSWORD"));
    // Create the EntityManagerFactory for JPA (Hibernate)
    entityManagerFactory = Persistence.createEntityManagerFactory("ZephyrPU", properties);

    // Initialize Flyway for database migration
    Flyway flyway = Flyway.configure().dataSource(dataSource).load();
    flyway.baseline();
    flyway.migrate();
  }

  public static synchronized EntityManagerFactory getEntityManagerFactory() {
    if (entityManagerFactory == null || !entityManagerFactory.isOpen()) {
      Dotenv dotenv = Dotenv.load();
      Map<String, String> properties = new HashMap<>();
      properties.put("jakarta.persistence.jdbc.url", dotenv.get("DB_URL"));
      properties.put("jakarta.persistence.jdbc.user", dotenv.get("DB_USER"));
      properties.put("jakarta.persistence.jdbc.password", dotenv.get("DB_PASSWORD"));

      // 创建 EntityManagerFactory
      entityManagerFactory = Persistence.createEntityManagerFactory("ZephyrPU", properties);
    }
    return entityManagerFactory;
  }


  public EntityManager getEntityManager() {
    return entityManagerFactory.createEntityManager();
  }


  public static void closeEntityManagerFactory() {
    if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
      entityManagerFactory.close();
    }
  }

  /**
   * Get a connection from the HikariCP data source.
   *
   * @return Connection object
   * @throws SQLException if a database access error occurs
   */

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }


  public void close() {
    if (dataSource != null && !dataSource.isClosed()) {
      dataSource.close();
    }
    if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
      entityManagerFactory.close();
    }
  }


  // get datasource
  public static HikariDataSource getDataSource() {
    return dataSource;
  }

  /**
   * Asynchronous database initialization.
   * Flyway migration is already handled in the constructor.
   *
   * @param resultHandler Handler for the result of the initialization
   */

  public void init(Handler<AsyncResult<Void>> resultHandler) {
    // Flyway migration is already handled in the constructor
    resultHandler.handle(Future.succeededFuture());
  }
}

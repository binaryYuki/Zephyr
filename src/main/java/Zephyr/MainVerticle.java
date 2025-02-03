package Zephyr;

import Zephyr.caches.ValKeyManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Main Verticle for the application
 * @author binaryYuki
 */
public class MainVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  public static dbHelper dbHelperInstance;
  @Override
  public void start(Promise<Void> startPromise) {
    logger.info("Starting MainVerticle initialization...");

    // 初始化 dbHelper 和 ValKey
    initializeServices()
      .compose(v -> {
        // 配置 HTTP 服务器
        Router router = setupHttpServer();
        vertx.createHttpServer()
          .requestHandler(router)
          .listen(8888);
        return Future.succeededFuture();
      })
      .onSuccess(ok -> {
        startPromise.complete();
        logger.info("MainVerticle started successfully.");
      })
      .onFailure(err -> {
        startPromise.fail(err);
        logger.error("Failed to start MainVerticle: {}");

      });
  }

  private Future<Void> initializeServices() {
    Promise<Void> promise = Promise.promise();

    dbHelperInstance = new dbHelper(vertx);
    ValKeyManager valKeyManager = ValKeyManager.getInstance();

    // 测试 ValKey 连接
    valKeyManager.set("test", "test");
    String test = valKeyManager.get("test");
    if (!"test".equals(test)) {
      logger.error("Failed to connect to ValKey. Value mismatch.");
      promise.fail("Failed to connect to ValKey");
    } else {
      logger.info("ValKey initialized and verified successfully.");
    }

    // 初始化数据库
    dbHelperInstance.init(ar -> {
      if (ar.succeeded()) {
        logger.info("Database initialized successfully.");
        promise.complete();
      } else {
        logger.error("Database initialization failed: {}" + ar.cause().getMessage());
        promise.fail(ar.cause());
      }
    });

    return promise.future();
  }

  private Router setupHttpServer() {
    // Create the main Router
    Router router = Router.router(vertx);

    // 通用路由配置
    router.route().handler(ctx -> {
      String requestId = IdGenerator.generateRequestId();
      ctx.response().putHeader("X-Request-Id", requestId);
      logger.debug("Request ID generated: {}");
      ctx.next();
    });

    // 添加 BodyHandler
    router.route().handler(BodyHandler.create()
      .setBodyLimit(50_000)
      .setDeleteUploadedFilesOnEnd(true)
      .setHandleFileUploads(true)
      .setUploadsDirectory(Paths.get("Zephyr", "uploads").toString())
      .setMergeFormAttributes(true)
    );

    // 配置子路由
    router.route("/api/jack/*").subRouter(new JackRoutes(vertx).getSubRouter());
    router.route("/api/austin/*").subRouter(new AustinRoutes(vertx).getSubRouter());
    router.route("/api/v1/*").subRouter(new YukiRoutes(vertx).getSubRouter());

    // 添加健康检查路由
    router.get("/healthz").handler(this::handleHealthCheck);

    // 错误处理
    setupErrorHandlers(router);

    return router;
  }

  private void handleHealthCheck(RoutingContext ctx) {
    JsonObject responseObject = new JsonObject();

    // 检查数据库状态
    JsonObject dbStatus = new JsonObject();
    try (Connection connection = dbHelper.getDataSource().getConnection()) {
      try (PreparedStatement stmt = connection.prepareStatement("SELECT 1")) {
        ResultSet rs = stmt.executeQuery();
        if (rs.next() && rs.getInt(1) == 1) {
          dbStatus.put("success", true).put("message", "Database is healthy.");
        } else {
          dbStatus.put("success", false).put("message", "Unexpected query result.");
        }
      }
    } catch (SQLException e) {
      dbStatus.put("success", false).put("message", "Database connection failed: " + e.getMessage());
      logger.warn("Database health check failed.", e);
    }

    // 检查 ValKey 状态
    JsonObject valKeyStatus = new JsonObject();
    ValKeyManager valKeyManager = ValKeyManager.getInstance();
    String key = "health@" + System.currentTimeMillis();
    valKeyManager.set(key, "healthy");
    if ("healthy".equals(valKeyManager.get(key))) {
      valKeyStatus.put("success", true).put("message", "ValKey is healthy.");
      valKeyManager.del(key);
    } else {
      valKeyStatus.put("success", false).put("message", "ValKey is not accessible.");
      logger.warn("ValKey health check failed.");
    }

    responseObject.put("status", "ok")
      .put("database", dbStatus)
      .put("valkey", valKeyStatus)
      .put("timestamp", System.currentTimeMillis());

    ctx.response().putHeader("Content-Type", "application/json").end(responseObject.encode());
  }

  private void setupErrorHandlers(Router router) {
    // 500 Internal Server Error
    router.errorHandler(500, ctx -> {
      JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", "Server Side Error")
        .put("message", ctx.failure() != null ? ctx.failure().getMessage() : "Unknown error");
      if (!ctx.response().ended()) {
        ctx.response()
          .setStatusCode(500)
          .putHeader("Content-Type", "application/json")
          .end(error.encode());
      }
    });

    // 404 errors
    router.errorHandler(404, ctx -> {
      JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", "Not Found")
        .put("message", "The requested resource was not found.");
      if (!ctx.response().ended()) {
        ctx.response()
          .setStatusCode(404)
          .putHeader("Content-Type", "application/json")
          .end(error.encode());
      }
    });

    // 403
    router.errorHandler(403, ctx -> {
      JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", "Forbidden")
        .put("message", "You do not have permission to access this resource.");
      if (!ctx.response().ended()) {
        ctx.response()
          .setStatusCode(403)
          .putHeader("Content-Type", "application/json")
          .end(error.encode());
      }
    });

    // 401 Unauthorized
    router.errorHandler(401, ctx -> {
      JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", "Unauthorized")
        .put("message", "You are not authorized to access this resource.");
      if (!ctx.response().ended()) {
        ctx.response()
          .setStatusCode(401)
          .putHeader("Content-Type", "application/json")
          .end(error.encode());
      }
    });

    // 400
    router.errorHandler(400, ctx -> {
      JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", "Bad Request")
        .put("message", "The request was invalid or cannot be served.");
      if (!ctx.response().ended()) {
        ctx.response()
          .setStatusCode(400)
          .putHeader("Content-Type", "application/json")
          .end(error.encode());
      }
    });
  }


}

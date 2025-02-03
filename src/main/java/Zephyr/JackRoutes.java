package Zephyr;

import Zephyr.entities.AcceptedSequences;
import Zephyr.entities.Service;
import Zephyr.entities.Uploads;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.persistence.EntityManager;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static Zephyr.MainVerticle.dbHelperInstance;


/**
 * @author Jingyu Wang
 */
public class JackRoutes {

  private final Vertx vertx;

  // 构造函数，接收 Vert.x 实例
  public JackRoutes(Vertx vertx) {
    this.vertx = vertx;
  }

  // 创建并返回一个子路由器
  public Router getSubRouter() {
    Router router = Router.router(vertx);

    // 定义 "/api/jack" 路径
    router.route("/").handler(this::handleRoot);

    // 定义 "/api/jack/info" 路径
    router.route("/info").handler(this::handleInfo);

    // test orm
    router.route("/testOrm").handler(this::testOrm);

    router.route().handler(BodyHandler.create()
      .setBodyLimit(50000)
      //处理后自动移除
      .setDeleteUploadedFilesOnEnd(true)
      .setHandleFileUploads(true)
      .setUploadsDirectory(Paths.get("Zephyr", "uploads").toString())
      .setMergeFormAttributes(true)
    );

    router.post("/analyze/text/uploads").handler(ctx -> {
      ctx.fail(501);
//      List<FileUpload> uploads = ctx.fileUploads();
//      for(FileUpload u:uploads){
//        String fileName = u.fileName();
//        String tail = fileName.substring(fileName.lastIndexOf("."));
//        if ("text/plain".equals(u.contentType())
//          &&".txt".equals(tail)
//          &&"UTF-8".equals(u.charSet())
//          &&u.size()<=50000)
//          //校验通过，开始处置
//        {
//          handleFileUpload(ctx, u);
//        } else{
//          //校验不通过，强制删除
//          ctx.fail(400);
//
//        }
//      }
    });

    router.get("/analyze/text/uploads").handler(ctx -> {
      // get method return html form for uploading text files
      ctx.response().putHeader("Content-Type", "text/html")
        .end("""
          <form action="/api/jack/analyze/text/uploads" method="post" enctype="multipart/form-data">
            <input type="file" name="file" accept=".txt">
            <input type="submit" value="Upload">
          </form>""");
    });


    /**
     * 添加一个关键词
     * 前端传入Json对象，提取其input字段，写入数据库
     */
    router.post("/submit/keyword").handler(this::handleKeywordSubmit);

    return router;
  }


  // 处理 "/api/jack" 路径的逻辑
  private void handleRoot(RoutingContext ctx) {
    JsonObject response = new JsonObject()
      .put("status", "ok")
      .put("message", "ready")
      .put("timestamp", System.currentTimeMillis());

    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(response.encode());
  }

  // 处理 "/api/jack/info" 路径的逻辑
  private void handleInfo(RoutingContext ctx) {
    JsonObject response = new JsonObject()
      .put("status", "ok")
      .put("message", "This is Jack's info endpoint!")
      .put("timestamp", System.currentTimeMillis());

    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(response.encode());
  }

  private void handleKeywordSubmit(RoutingContext ctx) {
    // 获取请求体
    JsonObject object = ctx.body().asJsonObject();
    String keyword = object.getString("input").toLowerCase();
    JsonObject updateBias = new JsonObject();

    try (Connection connection = dbHelper.getDataSource().getConnection()) {
      // 首先检查是否已经有匹配的记录
      String selectQuery = "SELECT rate FROM accepted_sequences WHERE content = ?";
      try (PreparedStatement selectStmt = connection.prepareStatement(selectQuery)) {
        selectStmt.setString(1, keyword);
        ResultSet rs = selectStmt.executeQuery();

        if (rs.next()) {
          // 如果记录已存在，更新 rate
          int newRate = rs.getInt("rate") + 1;
          String updateQuery = "UPDATE accepted_sequences SET rate = ?, last_updated_at = ? WHERE content = ?";
          try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
            updateStmt.setInt(1, newRate);
            updateStmt.setLong(2, System.currentTimeMillis());
            updateStmt.setString(3, keyword);
            updateStmt.executeUpdate();
          }

          updateBias.put("success", true)
            .put("content", keyword)
            .put("rate", newRate)
            .put("timestamp", System.currentTimeMillis());
        } else {
          // 如果记录不存在，插入新记录
          String insertQuery = "INSERT INTO accepted_sequences (content, rate, created_at, last_updated_at) VALUES (?, ?, ?, ?)";
          try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
            insertStmt.setString(1, keyword);
            insertStmt.setInt(2, 1);
            insertStmt.setLong(3, System.currentTimeMillis());
            insertStmt.setLong(4, System.currentTimeMillis());
            insertStmt.executeUpdate();
          }

          updateBias.put("success", true)
            .put("content", keyword)
            .put("rate", 1)
            .put("timestamp", System.currentTimeMillis());
        }
      }
    } catch (SQLException e) {
      updateBias.put("success", false).put("message", "Database error: " + e.getMessage());
    }

    // 将结果返回给客户端
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(updateBias.encode());
  }

  //关键词检测器 A naive approach of a text-based fraud detector.
  private void handleFileUpload(RoutingContext ctx, FileUpload u){
    Path path = Paths.get("Zephyr", "uploads", u.uploadedFileName());
    EntityManager entityManager = dbHelperInstance.getEntityManager();
    try {
      // Begin a transaction
      entityManager.getTransaction().begin();

      // 查找现有的文件
      Uploads existingFileUpload = entityManager.find(Uploads.class, path.toString());
      //只有已处理文件才能被覆盖
      if (existingFileUpload != null && existingFileUpload.getProcessed()) {
        // 更新现有文件
        existingFileUpload.setFilePath(path.toString());
        //刚上传或更新的文件还未被processFile方法处理
        existingFileUpload.setProcessed(false);
      } else {
        // 如果文件不存在，则存入新文件
        Uploads newUpload = new Uploads();
        newUpload.setFilePath(path.toString());
        newUpload.setProcessed(false);
        entityManager.persist(newUpload);
      }

      // Commit the transaction
      entityManager.getTransaction().commit();
    } catch (Exception e) {
      // Rollback the transaction in case of errors
      if (entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().rollback();
      }
      ctx.response().setStatusCode(500).end("Error: " + e.getMessage());
      return;
    } finally {
      // Close the entity manager
      entityManager.close();
    }
    entityManager = dbHelperInstance.getEntityManager();
    //提取指定文件
    List<Uploads> uploads = entityManager.createQuery
        ("SELECT filePath FROM Uploads WHERE processed = FALSE", Uploads.class).getResultList();
    for(Uploads upload:uploads){
      //如果列表中有指定路径元素，说明提取正确
      if(upload.getFilePath().equals(path.toString())) {
        //发现可疑关键词,且全文平均权重超过指定阈值(10)，返回alarmed状态
        if (processFile(upload.getFilePath())[0]/processFile(upload.getFilePath())[1]>=10) {
          JsonObject response = new JsonObject()
            .put("status", "uploaded")
            .put("dir", path.toString())
            .put("result", "alarmed")
            .put("timestamp", System.currentTimeMillis());

          ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
          //如果全文平均权重未超过指定阈值，返回unalarmed状态
        } else {
          JsonObject response = new JsonObject()
            .put("status", "uploaded")
            .put("dir", path.toString())
            .put("result", "unalarmed")
            .put("timestamp", System.currentTimeMillis());

          ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
        }
        //处理结束, 文件在DB改为可覆盖状态，被BodyHandler从文件夹移除(Line:48)
        upload.setProcessed(true);
        entityManager.close();
      }
    }
    //遍历列表后仍未发现指定文件，处理失败
    ctx.fail(404);
    entityManager.close();
  }

  private int[] processFile(String path) {
    int res = 0;
    int lines = 0;
    List<AcceptedSequences> acceptedSequences;
    try {
      EntityManager entityManager = dbHelperInstance.getEntityManager();
      //提取所有已知的可疑关键词
      acceptedSequences = entityManager
        .createQuery("SELECT a FROM AcceptedSequences a", AcceptedSequences.class)
        .getResultList();
      entityManager.close();
    }
    catch (Exception e){
      throw new RuntimeException(e);
    }
    String line;
    try{
      FileReader reader = new FileReader(path);
      BufferedReader br = new BufferedReader(reader);
      while((line = br.readLine())!=null){
        for (AcceptedSequences sequence: acceptedSequences){
          if (line.contains(sequence.getList().getFirst())){
            //全文任何部分发现关键词，视为检测到关键词
            res+=sequence.getRate();
          }
        }
        lines++;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new int[]{res, lines};
  }

  // orm test
  private void testOrm(RoutingContext ctx) {
    // 假设要查找或更新 ID 为 1 的实体
    Long id = 1L;

    // Get the entity manager
    EntityManager entityManager = dbHelperInstance.getEntityManager();

    try {
      // Begin a transaction
      entityManager.getTransaction().begin();

      // 查找现有的服务
      Service existingService = entityManager.find(Service.class, id);
      if (existingService != null) {
        // 更新现有服务
        existingService.setName("Updated Service Name@" + System.currentTimeMillis());
        existingService.setDescription("Updated Description");
        existingService.setStatus("inactive");
      } else {
        // 如果服务不存在，则创建一个新服务
        Service newService = new Service();
        newService.setName("New Service");
        newService.setDescription("New Description");
        newService.setStatus("active");

        // Persist the new service
        entityManager.persist(newService);
      }

      // Commit the transaction
      entityManager.getTransaction().commit();
    } catch (Exception e) {
      // Rollback the transaction in case of errors
      if (entityManager.getTransaction().isActive()) {
        entityManager.getTransaction().rollback();
      }
      ctx.response().setStatusCode(500).end("Error: " + e.getMessage());
      return;
    } finally {
      // Close the entity manager
      entityManager.close();
    }

    // 新EntityManager
    entityManager = dbHelperInstance.getEntityManager();
    // 从数据库中查询所有服务
    List<Service> services = entityManager.createQuery("SELECT s FROM Service s", Service.class).getResultList();
    JsonObject response = new JsonObject()
      .put("status", "ok")
      .put("message", "Test ORM Success")
      .put("timestamp", System.currentTimeMillis())
      .put("services", services);
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(response.encode());
    entityManager.close();
  }
}




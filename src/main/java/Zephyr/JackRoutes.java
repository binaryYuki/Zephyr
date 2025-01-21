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
import java.util.*;

/**
 * @author Jingyu Wang
 * 待更新：关键词标记器
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
      .setMergeFormAttributes(true));

    router.get("/analyze/text/uploads").handler(ctx -> {
      // get method return html form for uploading text files
      ctx.response().putHeader("Content-Type", "text/html")
        .end("""
          <form action="/api/jack/analyze/text/uploads" method="post" enctype="multipart/form-data">
            <input type="file" name="file" accept=".txt">
            <input type="submit" value="Upload">
          </form>""");
    });

    router.post("/analyze/text/uploads").handler(ctx -> {
      List<FileUpload> uploads = ctx.fileUploads();
      for(FileUpload u:uploads){
        String fileName = u.fileName();
        String tail = fileName.substring(fileName.lastIndexOf("."));
        if ("text/plain".equals(u.contentType())
          &&".txt".equals(tail)
          &&"UTF-8".equals(u.charSet())
          &&u.size()<=50000)
          //校验通过，开始处置
        {
          handleFileUpload(ctx, u);
        } else{
          //校验不通过，强制删除
          uploads.remove(u);
          ctx.fail(400);

        }
      }
    });

    /**
     * 添加一个关键词
     * 前端传入Json对象，提取其input字段，写入数据库
     */
    router.post("/submit/keyword").handler( ctx-> {
      //获取请求体
      RequestBody body = ctx.body();
      //转化为JSON对象
      JsonObject object = body.asJsonObject();
      String keyword = object.getString("input");
      EntityManager entityManager = dbHelper.getEntityManagerFactory().createEntityManager();
      //提取所有已知的可疑关键词
      List<AcceptedSequences> acceptedSequences =
        entityManager
          .createQuery("SELECT a FROM AcceptedSequences a", AcceptedSequences.class)
          .getResultList();
      //如关键词已存在，则避免覆盖它
      for (AcceptedSequences acceptedSequences1: acceptedSequences){
        if(acceptedSequences1.getList().getFirst().equals(keyword)){
          ctx.fail(400);
        }
      }
      entityManager.close();
      //遍历完成仍不存在，存入新关键词
      entityManager = dbHelper.getEntityManagerFactory().createEntityManager();
      try {
        // Begin a transaction
        entityManager.getTransaction().begin();

          // 创建一个新AcceptedSequences对象
          AcceptedSequences newSequence = new AcceptedSequences();
          newSequence.setContent(keyword);
          newSequence.setTimeStampString(""+System.currentTimeMillis());
          // Persist the new sequence
          entityManager.persist(newSequence);

        // Commit the transaction
        entityManager.getTransaction().commit();
      } catch (Exception e) {
        // Rollback the transaction in case of errors
        if (entityManager.getTransaction().isActive()) {
          entityManager.getTransaction().rollback();
        }
        ctx.response().setStatusCode(500).end("Error: " + e.getMessage());
      } finally {
        // Close the entity manager
        entityManager.close();
      }
    });

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

  //关键词检测器 A naive approach of a text-based fraud detector.
  private void handleFileUpload(RoutingContext ctx, FileUpload u){
    Path path = Paths.get("Zephyr", "uploads", u.uploadedFileName());
    EntityManager entityManager = dbHelper.getEntityManagerFactory().createEntityManager();
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
    entityManager = dbHelper.getEntityManagerFactory().createEntityManager();
    //提取指定文件
    List<Uploads> uploads = entityManager.createQuery
        ("SELECT filePath FROM Uploads WHERE processed = FALSE", Uploads.class).getResultList();
    for(Uploads upload:uploads){
      //如果列表中有指定路径元素，说明提取正确
      if(upload.getFilePath().equals(path.toString())) {
        //发现可疑关键词
        if (processFile(upload.getFilePath())) {
          JsonObject response = new JsonObject()
            .put("status", "uploaded")
            .put("dir", path.toString())
            //返回alarmed状态
            .put("result", "alarmed")
            .put("timestamp", System.currentTimeMillis());

          ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
        } else {
          JsonObject response = new JsonObject()
            .put("status", "uploaded")
            .put("dir", path.toString())
            //检测通过
            .put("result", "unalarmed")
            .put("timestamp", System.currentTimeMillis());

          ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
        }
        upload.setProcessed(true);
        //处理结束, 文件在DB改为可覆盖状态，被BodyHandler从文件夹移除(Line:48)
      }
    }
    //遍历列表后仍未发现指定文件，处理失败
    ctx.fail(404);
  }

  private boolean processFile(String path) {
    /*施工中*/
    EntityManager entityManager = dbHelper.getEntityManagerFactory().createEntityManager();
    //提取所有已知的可疑关键词
    List<AcceptedSequences> acceptedSequences =
      entityManager
        .createQuery("SELECT a FROM AcceptedSequences a", AcceptedSequences.class)
        .getResultList();
    String line;

    try{
      FileReader reader = new FileReader(path);
      BufferedReader br = new BufferedReader(reader);
      while((line = br.readLine())!=null){
        for (AcceptedSequences sequence: acceptedSequences){
          if (line.contains(sequence.getList().getFirst())){
            //全文任何部分发现关键词，视为检测到关键词
            return true;
          }
        }
      }
      //文件结尾仍未发现关键词，视为未检测到关键词
      return false;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  // orm test
  private void testOrm(RoutingContext ctx) {
    // 假设要查找或更新 ID 为 1 的实体
    Long id = 1L;

    // Get the entity manager
    EntityManager entityManager = dbHelper.getEntityManagerFactory().createEntityManager();

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
    entityManager = dbHelper.getEntityManagerFactory().createEntityManager();

    // 从数据库中查询所有服务
    List<Service> services = entityManager.createQuery("SELECT s FROM Service s", Service.class).getResultList();
    JsonObject response = new JsonObject()
      .put("status", "ok")
      .put("message", "Test ORM Success")
      .put("timestamp", System.currentTimeMillis())
      .put("services", services);
    entityManager.close();
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(response.encode());
  }
}




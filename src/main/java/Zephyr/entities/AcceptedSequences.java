package Zephyr.entities;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "accepted_sequences")
public class AcceptedSequences {
  /*
   * 数据表
   * id： 主键，自动生成
   * String 词语本身
   * created_at：创建时间
   * updated_at：更新时间
   * 实现一个方法： 导出为 list
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY) // 主键自动生成
  private Long id;

  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Column(name = "last_updated_at", nullable = false)
  private String timeStampString;

  //权重，取决于用户举报次数
  @Column(name = "rate" , nullable = false)
  private int rate;

  public List<String> getList(){
    List<String> list = new ArrayList<>();
    list.add(content);
    list.add(timeStampString);
    return list;
  }

    public void setContent(String content) {
        this.content = content;
    }


    public void setTimeStampString(String timeStampString) {
        this.timeStampString = timeStampString;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id){
      this.id = id;
    }

  public int getRate() {
    return rate;
  }

  public void setRate(int rate) {
    this.rate = rate;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(String createdAt) {
    this.createdAt = createdAt;
  }
}

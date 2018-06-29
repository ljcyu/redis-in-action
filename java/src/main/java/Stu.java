public class Stu {
  long id;
  String username;
  double java,os,math;
  public Stu(){}

  public Stu(long id, String username, double java, double os, double math) {
    this.id = id;
    this.username = username;
    this.java = java;
    this.os = os;
    this.math = math;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public double getJava() {
    return java;
  }

  public void setJava(double java) {
    this.java = java;
  }

  public double getOs() {
    return os;
  }

  public void setOs(double os) {
    this.os = os;
  }

  public double getMath() {
    return math;
  }

  public void setMath(double math) {
    this.math = math;
  }

  @Override
  public String toString() {
    return "Stu{" +
        "id=" + id +
        ", username='" + username + '\'' +
        ", java=" + java +
        ", os=" + os +
        ", math=" + math +
        '}';
  }
}

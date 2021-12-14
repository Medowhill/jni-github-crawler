package info.hjaem.github

import java.net.{URL, URLEncoder}
import javax.net.ssl.HttpsURLConnection
import org.apache.commons.io.IOUtils
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import spray.json._
import scala.collection.mutable.Queue
import java.io._

object Main {

  val browser = JsoupBrowser()
  var isFirst = true
  val sleepSeconds = 10
  var saveCount = 0
  val saveFileName = "save.dump"

  def main(args: Array[String]): Unit = args match {
    case Array(start, end, lang) =>
      doA(start.toInt, end.toInt, lang)
    case Array("resume") =>
      val (pages, repos, results, lang) = load
      doResume(repos, pages, results, lang)
    case _ =>
      println("usage: run [pages] [lang]")
  }

  def doA(start: Int, end: Int, lang: String) {
    val repos = Queue[String]()
    val pages = (start.toInt to end.toInt).to(Queue)
    val results = Queue[String]()
    doResume(repos, pages, results, lang)
  }

  def trySave(x: (Queue[Int], Queue[String], Queue[String], String)) {
    saveCount += 1
    if (saveCount % 100 == 0) {
      val oos = new ObjectOutputStream(new FileOutputStream(saveFileName))
      oos.writeObject(x)
      oos.close
    }
  }

  def load: (Queue[Int], Queue[String], Queue[String], String) = {
    val ois = new ObjectInputStream(new FileInputStream(saveFileName))
    ois.readObject().asInstanceOf[(Queue[Int], Queue[String], Queue[String], String)]
  }

  def doResume(repos: Queue[String], pages: Queue[Int], results: Queue[String], lang: String) {
    while (!pages.isEmpty) {
      while (!repos.isEmpty) {
        trySave((pages, repos, results, lang))
        val repo = repos.dequeue()
        val r = RepositoryFactory(repo)
        if (r.jni) {
          println(r.toString)
          results += r.toString
        }
      }
      trySave((pages, repos, results, lang))
      val page = pages.dequeue()
      val url = s"https://api.github.com/search/repositories?order=desc&page=$page&q=language:$lang&sort=stars"
      val (res, rem, resetT) = get(url, RepositoryFactory.headers)
      if (rem <= 1) Thread.sleep(resetT * 1000 - System.currentTimeMillis() + 2000)
      repos ++=
        res.parseJson.asJsObject.fields("items").asInstanceOf[JsArray].elements.map(_.asJsObject.fields("url").asInstanceOf[JsString].value)
    }
  }


  def get(url: String, headers: Map[String, String] = Map()): (String, Int, Int) = {
    val con = (new URL(url)).openConnection.asInstanceOf[HttpsURLConnection]
    headers.map{ case (k, v) => con.setRequestProperty(k, v) }
    con.setUseCaches(false)
    con.setInstanceFollowRedirects(false)
    con.setDoInput(true)
    con.setDoOutput(false)
    con.setRequestMethod("GET")
    val in = con.getInputStream
    val content = IOUtils.toString(in, "UTF-8")
    in.close()
    val rem = con.getHeaderField("X-RateLimit-Remaining").toInt
    val resetT = con.getHeaderField("X-RateLimit-Reset").toInt
    //println((rem, resetT))
    con.disconnect()
    (content, rem, resetT)
  }
}

case class Repository(href: String, val java: Double, val c: Double, val cpp: Double) {
  val jni: Boolean = java > 0 && c > 0 && cpp < 0.00001
}

object RepositoryFactory {
  val headers = Map(
    "Authorization" -> s"token ${scala.io.Source.fromFile("token").mkString.trim}"
  )

  def apply(href: String): Repository = {
    val (res, rem, resetT) = Main.get(s"$href/languages", headers)
    if (rem <= 1) Thread.sleep(resetT * 1000 - System.currentTimeMillis() + 2000)

    val languages: Map[String, Int] =
        res.parseJson
        .asJsObject
        .fields
        .map{ case (k, v) => k.toLowerCase -> v.prettyPrint.toInt }
      val lines: Int = languages.values.sum

    val java: Double = languages.getOrElse("java", 0) * 1.0 / lines
    val c: Double = languages.getOrElse("c", 0) * 1.0 / lines
    val cpp: Double = languages.getOrElse("c++", 0) * 1.0 / lines
    Repository(href, java, c, cpp)
  }
}

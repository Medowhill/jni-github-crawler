package info.hjaem.github

import java.net.{URL, URLEncoder}
import javax.net.ssl.HttpsURLConnection
import org.apache.commons.io.IOUtils
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import spray.json._

object Main {

  val browser = JsoupBrowser()
  var isFirst = true
  val sleepSeconds = 10

  def main(args: Array[String]): Unit = args match {
    case Array(start, end, lang) =>
      val repos = (start.toInt to end.toInt).to(LazyList)
        .flatMap(getRepositories(_, lang))
      val jniRepos = repos.filter(_.jni)
      jniRepos foreach println
      println(repos.length)
      println(jniRepos.length)
    case _ =>
      println("usage: run [pages] [lang]")
  }

  def getRepositories(page: Int, lang: String): List[Repository] = {
    if (isFirst) isFirst = false else Thread.sleep(sleepSeconds * 1000)
    val url = s"https://api.github.com/search/repositories?order=desc&page=$page&q=language:$lang&sort=stars"
    get(url, Repository.headers).parseJson.asJsObject.fields("items").asInstanceOf[JsArray].elements.map(
      (x) => Repository(x.asJsObject.fields("url").asInstanceOf[JsString].value)).toList
  }

  def get(url: String, headers: Map[String, String] = Map()): String = {
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
    con.disconnect()
    content
  }
}

case class Repository(href: String) {
  import Repository._

  val languages: Map[String, Int] =
    Main.get(s"$href/languages", headers)
      .parseJson
      .asJsObject
      .fields
      .map{ case (k, v) => k.toLowerCase -> v.prettyPrint.toInt }

  val lines: Int = languages.values.sum

  val java: Double = languages.getOrElse("java", 0) * 1.0 / lines
  val c: Double = languages.getOrElse("c", 0) * 1.0 / lines
  val cpp: Double = languages.getOrElse("c++", 0) * 1.0 / lines

  val jni: Boolean = java > 0 && (c > 0 || cpp > 0)

  override def toString: String = s"$href java($java) c($c) c++($cpp)"
}

case object Repository extends (String => Repository) {
  val headers = Map(
    "Authorization" -> s"token ${scala.io.Source.fromFile("token").mkString.trim}"
  )
}

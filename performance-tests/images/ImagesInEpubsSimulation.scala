package com.blinkboxbooks.resourceserve

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.jdbc.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import akka.util.duration._
import bootstrap._
import java.io.File
import scala.util.Random

class ImagesInEpubsSimulation extends Simulation {

  // Set to where your resources are:
  val root = "/Users/jans/data/resources/"
  val paths = findPaths(root, "test-epubs", Set("epub")).random
  
  val outputSizes = Array(99, 150, 153, 167, 330, 362, 366, 731)
  val sizes = outputSizes.zip(Stream.continually("size")).map{ case (k, v) => Map(v -> k.toString) }.random
  
  val httpConf = httpConfig
    .baseURL("http://localhost:8080")
    .acceptCharsetHeader("utf-8")
    .acceptHeader("application/vnd.blinkboxbooks.data.v1+json")
    .acceptEncodingHeader("gzip, deflate")

  val scn = scenario("get pseudo-random sequence of images")
    .repeat(5000) {
      feed(paths)
        .feed(sizes)
        .exec(
          http("request_1")
            .get("/params;v=0;img:w=${size};img:m=scale/${path}/images/test.png.jpeg")
            .check(status.is(200)))
    }

  setUp(scn.users(4).ramp(4).protocolConfig(httpConf))

  // Helper functions.

  def findPaths(root: String, path: String, extensions: Set[String]): Array[Map[String, String]] =
    findFiles(new File(root + path))
      .filter(matchesExtension(_, extensions))
      .map(f => Map("path" -> f.getPath.drop(root.length)))
      .toArray

  def findFiles(f: File): Stream[File] =
    f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(findFiles) else Stream.empty)

  def matchesExtension(file: File, extensions: Set[String]) =
    extensions.exists(ext => file.getName.toLowerCase.contains("." + ext))

}
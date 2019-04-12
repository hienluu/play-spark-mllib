import models.UserRatingSummaryStat
import org.junit.Assert._
import org.junit.Test
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Reads._

@Test
class JsonTest {
  def main(args: Array[String]): Unit = {
    println("... hello there ...")
  }

  @Test
  def testParsing() = {
    val stats = Array("{\"summary\":\"count\",\"rating\":\"37\"}",
      "{\"summary\":\"mean\",\"rating\":\"4.135135135135135\"}",
      "{\"summary\":\"stddev\",\"rating\":\"0.9764494116202096\"}",
      "{\"summary\":\"min\",\"rating\":\"1.0\"}",
      "{\"summary\":\"max\",\"rating\":\"5.0\"}")

    var count:Int = 0
    var mean:Float = 0.0f
    var stddev:Float = 0.0f
    var min:Float = 0.0f
    var max:Float = 0.0f

    for (summary <- stats) {
      println(s"... $summary ...")
      val summaryJSON:JsValue = Json.parse(summary)
      val summaryType = (summaryJSON \ "summary").as[String]
      val ratingValue = (summaryJSON \ "rating").as[String]

      summaryType match {
        case "count" => count = ratingValue.toInt
        case "mean" => mean = ratingValue.toFloat
        case "stddev" => stddev = ratingValue.toFloat
        case "min" => min = ratingValue.toFloat
        case "max" => max = ratingValue.toFloat
      }
    }

    val ursStat = UserRatingSummaryStat(123, count, mean, stddev, min, max)
    println(ursStat)

  }


}

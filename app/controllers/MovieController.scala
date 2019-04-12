package controllers

import javax.inject.Inject
import models._
import bootstrap.Init
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, Controller}
import play.api.Logger
import play.api.libs.json.Reads._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._



/**
  * Created by hluu on 4/4/18.
  */
class MovieController @Inject()(implicit webJarAssets: WebJarAssets,
                                val messagesApi: MessagesApi) extends Controller {


  def index = Action {
    val popularMovies = Init.getTopKRatedStat("movieId", 5)
    val popularMovieStrings = decorateMovie(popularMovies).toJSON.collect()

    val popularMovieList = scala.collection.mutable.ListBuffer.empty[Movie]
    for (movieStr <- popularMovieStrings) {
      val movie = jsonToMovieWithCount(movieStr)
      Logger.info("count: " + movie.count)
      popularMovieList +=  movie
    }

    val popularRaters = Init.getTopKRatedStat("userId", 10)
    val popularRaterList = scala.collection.mutable.ListBuffer.empty[UserRatingStat]
    for (raterStr <- popularRaters.toJSON.collect()) {

      val raterJSON:JsValue = Json.parse(raterStr)
      popularRaterList += UserRatingStat((raterJSON \ "userId").as[Int],
        (raterJSON \ "count").as[Int])

    }

    val sortedPopMovies = popularMovieList.sortWith((m1, m2) => m2.count < m1.count)

    Ok(views.html.home(Init.stats, sortedPopMovies.toList, popularRaterList.toList))
  }

  def trainModel = Action {

    Init.trainModel
    //Redirect("/movies")
    Ok(views.html.modeltrained(Init.stats,   "Model training completed"))
  }

  def movies = Action  {

      val query1 =
        s"""
          SELECT * FROM movies limit 10
        """.stripMargin
      val sparkSession = Init.getSparkSessionInstance

      val smallSetMoviesDF: DataFrame = sparkSession.sql(query1)

      val movieStrings = decorateMovie(smallSetMoviesDF).toJSON.collect()

      val movieList = scala.collection.mutable.ListBuffer.empty[Movie]

      for (movieStr <- movieStrings) {

        val movieJSON:JsValue = Json.parse(movieStr)

        //Logger.warn("movie class:" + movieJSON.getClass)

        val title = (movieJSON \ "title").as[String]
        val movieId = (movieJSON \ "movieId").as[Int]

        val genres = (movieJSON \ "genres").get.toString()
        val tmdbId = (movieJSON \ "tmdbId").as[Int]

        //val posterPath = tmdb.getMovies().getMovie(tmdbId, "en").getPosterPath()

        val movieObj = Movie(movieId, title, genres, "")


        movieList +=  movieObj
      }


      Ok(views.html.movies(movieList.toList))

  }



  def randomMovies = Action {
    val randomMoviesDF = Init.getMoviesDF.sample(false, 0.5,System.currentTimeMillis).limit(10)

    val randomMoviesWithImgDF = randomMoviesDF.join(Init.movieWithImg, "movieId")

    val movieStrings = randomMoviesWithImgDF.limit(10).toJSON.collect()

    val movieList = scala.collection.mutable.ListBuffer.empty[Movie]
    for (movieStr <- movieStrings) {

      movieList +=  jsonToMovie(movieStr)
    }

    Ok(views.html.movies(movieList.toList))
  }

  private def jsonToMovieWithCount(movieStr:String) : Movie = {
    val movieJSON:JsValue = Json.parse(movieStr)

    val movieId = (movieJSON \ "movieId").as[Int]
    val title = (movieJSON \ "title").as[String]
    val genres = (movieJSON \ "genres").as[String]

    val imgUrl = (movieJSON \ "imgUrl").asOpt[String] match {
      case Some(url) => url
      case None => ""
    }

    val count = (movieJSON \ "count").as[Int]
    Movie(movieId, title, genres, imgUrl, count)

  }

  private def jsonToMovie(movieStr:String) : Movie = {
    val movieJSON:JsValue = Json.parse(movieStr)

    val movieId = (movieJSON \ "movieId").as[Int]
    val title = (movieJSON \ "title").as[String]
    val genres = (movieJSON \ "genres").as[String]

    val imgUrl = (movieJSON \ "imgUrl").asOpt[String] match {
      case Some(url) => url
      case None => ""
    }


    Movie(movieId, title, genres, imgUrl)

  }

  /**
    * For a given movie, find out recommended which users
    * @param movieId
    * @return
    */
  def recommendedUsers(movieId:Int) = Action {

    val movieDF = Init.getMovieById(movieId)
    val movieWithImgDF = movieDF.join(Init.getMovieWithImg, Seq("movieId"), "leftouter")


    // convert movie id to movie object so we can show in the UI
    val movieList = scala.collection.mutable.ListBuffer.empty[Movie]
    val movieStrings = movieWithImgDF.toJSON.collect()
    for (movieStr <- movieStrings) {
      movieList +=  jsonToMovie(movieStr)
    }

    val onlyMovie:Movie = movieList.toList(0)


    // now get recommended users for this movie

    val recommendedUsersDF = Init.getRecommendUserForMovie(movieId)

    val recommendedUserStrings = recommendedUsersDF.toJSON.collect()

    val userRatingList = scala.collection.mutable.ListBuffer.empty[Rating]
    for (recUserStr <- recommendedUserStrings) {
      val movieJSON: JsValue = Json.parse(recUserStr)

      val userId = (movieJSON \ "userId").as[Int]
      val rating = (movieJSON \ "rating").as[Float]

      userRatingList += Rating(userId, movieId, "", "", rating, "")
    }


    Ok(views.html.recommended_users(onlyMovie, userRatingList.toList))
  }

  def randomUsers = Action {

    val userRatingsDF = Init.getRatingsDF.groupBy("userId").count()

    val activeRatersDF = userRatingsDF.where("count > 500")
                            .sample(false, 0.5, System.currentTimeMillis).limit(5)

    val notActiveRatersDF = userRatingsDF.where("count < 100")
                             .sample(false, 0.5, System.currentTimeMillis).limit(5)

    val ratersDF = activeRatersDF.union(notActiveRatersDF);

    val ratingStrings = ratersDF.toJSON.collect()

    val userRatingsList = scala.collection.mutable.ListBuffer.empty[RatingSummary]
    for (ratingStr <- ratingStrings) {
      val ratingJSON:JsValue = Json.parse(ratingStr)

      val userId = (ratingJSON \ "userId").as[Int]
      val count = (ratingJSON \ "count").as[Int]


      userRatingsList +=  RatingSummary(userId, count, getTopGenreInfo(userId))
    }

    Ok(views.html.user_rating_summary(userRatingsList.toList))

  }



  private def getTopGenreInfo(userId:Int) : String = {
    val topGenreDF = Init.getTopGenresByUserId(userId)

    var genreInfoList = scala.collection.mutable.ListBuffer[String]()
    for (topGenreStr <- topGenreDF.toJSON.collect()) {
      val topGenreJSON:JsValue = Json.parse(topGenreStr)
      val genre = (topGenreJSON \ "genre").as[String]
      val count = (topGenreJSON \ "genre_count").as[Int]

      genreInfoList += s"$genre:$count";
    }

    genreInfoList.mkString(", ")
  }



  def compareActualVsPredicted(userId: Int) = Action {
    // find 5 highest rated movies for this users

    val userHighestRatingDF:DataFrame = Init.getRatingsForUser(userId).orderBy(col("rating").desc).limit(10)

    val userRatings:List[Rating] = convertToRatingObjects(userHighestRatingDF)

    // find 5 highest recommended movies for this user
    val recommendedMoviesDF:DataFrame = Init.getRecommendMoviesForUser(userId)
    val recommendMovies:List[Rating] = convertToRatingObjects(recommendedMoviesDF)

    val genreInfo = getTopGenreInfo(userId)

    val userRatingSummaryStat = gettUserRatingSummaryStats(userId)

    Ok(views.html.comparerating(userId, userRatings, recommendMovies, genreInfo, userRatingSummaryStat))
  }

  private def convertToRatingObjects(ratingDF:DataFrame) : List[Rating] = {

    val ratingStrings = decorateRating(ratingDF).toJSON.collect()


    val ratingList = scala.collection.mutable.ListBuffer.empty[Rating]
    for (ratingStr <- ratingStrings) {
      //Logger.info(ratingStr)

      val ratingJSON:JsValue = Json.parse(ratingStr)

      val userId = (ratingJSON \ "userId").as[Int]
      val movieId = (ratingJSON \ "movieId").as[Int]

      val rating = (ratingJSON \ "rating").as[Double]

      val title = (ratingJSON \ "title").as[String]
      val genres = (ratingJSON \ "genres").as[String]


      val imgUrl = (ratingJSON \ "imgUrl").asOpt[String] match {
        case Some(url) => url
        case None => ""
      }


      val movieObj = Rating(userId, movieId, title, genres, rating, imgUrl)

      ratingList +=  movieObj
    }

    ratingList.toList
  }

  def decorateMovie(moviesDF:DataFrame) : DataFrame = {
    val movieWithTmdbId:DataFrame = Init.getMovieWithImg

    val joinedDF = movieWithTmdbId.join(moviesDF, "movieId")


    if (joinedDF.schema.fields.map(f => f.name).contains("count")) {
      joinedDF.select("movieId", "title", "genres", "imgUrl", "count")
    } else {
      joinedDF.select("movieId", "title", "genres", "imgUrl")
    }
  }

  private def decorateRating(ratingsDF:DataFrame) : DataFrame = {
    val movieWithTmdbId:DataFrame = Init.getMovieWithImg

    Logger.info("**** movieWithTmdbId ****")
    movieWithTmdbId.printSchema()

    val joinedDF = movieWithTmdbId.join(ratingsDF, "movieId")

    //Logger.info("**** joinedDF ****")
    //joinedDF.printSchema()
    joinedDF.select("userId", "movieId","rating", "title", "genres", "imgUrl")
  }


  private def gettUserRatingSummaryStats(userId:Int) : UserRatingSummaryStat = {
    val summaryStat = Init.getUserRatingSummaryStats(userId)

    var count:Int = 0
    var mean:Float = 0.0f
    var stddev:Float = 0.0f
    var min:Float = 0.0f
    var max:Float = 0.0f

    for (summary <- summaryStat.toJSON.collect()) {

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

    UserRatingSummaryStat(userId, count, mean, stddev, min, max)
  }

}



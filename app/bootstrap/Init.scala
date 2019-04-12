package bootstrap

import models.Stats
import org.apache.spark.ml.recommendation.{ALS, ALSModel}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._


import play.api._


/**
  * Created by hluu on 4/4/18.
  */
object Init extends GlobalSettings {
  var sparkSession: SparkSession = _

  var linksDF:DataFrame = _
  var imgDF:DataFrame = _
  var moviesDF:DataFrame = _
  var ratingsDF:DataFrame = _
  var movieWithImg:DataFrame = _

  var top10MoviesPerUser:DataFrame = _
  var top10UsersPerMovie:DataFrame = _

  var topGenresPerUserDF:DataFrame = _

  var model:ALSModel = _
  var stats:Stats = _


  /**
    * On start load the json data from conf/data.json into in-memory Spark
    */
  override def onStart(app: Application) {
    sparkSession = SparkSession.builder
      .master("local")
      .appName("ApplicationController")
      .config("spark.sql.shuffle.partitions", "20")
      .getOrCreate()


    moviesDF = sparkSession.read.option("header", "true")
           .option("inferSchema", "true").csv("conf/movielens-movies.csv")

    moviesDF.cache()

    moviesDF.createOrReplaceTempView("movies")

    ratingsDF = sparkSession.read.option("header", "true")
      .option("inferSchema", "true").csv("conf/movielens-ratings.csv")

    ratingsDF.createOrReplaceTempView("ratings")

    ratingsDF.cache()

    // imgDF
    imgDF = sparkSession.read.option("header", "true")
      .option("inferSchema", "true").csv("conf/movielens-imgs.csv")


    movieWithImg = moviesDF.join(imgDF, Seq("movieId"), "leftouter").drop("imdbId")

    movieWithImg.printSchema();

    movieWithImg.cache()
    movieWithImg.count()

    // user rated movies
    val userRatedMoviesDF = ratingsDF.join(moviesDF, "movieId")
    val moviesGenreDF = userRatedMoviesDF.select(col("userId"),
      col("movieId"), explode(split(col("genres"),"\\|").as("genreArr")).as("genre"))
    val moviesGenreGroupDF = moviesGenreDF.select(col("userId"), col("genre"))
      .groupBy(col("userId"), col("genre")).agg(count("*") as "genre_count")


    val forRankingWindow = Window.partitionBy("userId").orderBy(desc("genre_count"))

    topGenresPerUserDF = moviesGenreGroupDF.withColumn("rank", rank().over(forRankingWindow))
      .where(col("rank").lt( 4))


    topGenresPerUserDF.cache()
    topGenresPerUserDF.count()


    computeStats()
  }

  private def computeStats(): Unit =  {
    val movieCount = moviesDF.count()
    val ratingCount = ratingsDF.count()
    val userCount = ratingsDF.select("userId").distinct().count

    stats = Stats(movieCount, userCount, ratingCount)
  }


  def getTopKRatedStat(column:String, k: Int) : DataFrame = {
    Logger.info(s"getTopKRatedStat: $column, k: $k")
    ratingsDF.groupBy(column).count().orderBy(col("count").desc).limit(k)
  }


  /**
    * On stop clear the sparksession
    */
  override def onStop(app: Application) {
    sparkSession.stop()
  }

  def getSparkSessionInstance : SparkSession = {
    sparkSession
  }

 /* def getTmdb  : TmdbApi = {
    tmdb
  }*/

  /*def getLinksDF : DataFrame = {
    linksDF
  }*/

  def getRatingsDF : DataFrame = {
    ratingsDF
  }

  def getMoviesDF : DataFrame = {
    moviesDF
  }

  def getMovieWithImg : DataFrame = {
    movieWithImg
  }

  def getUserRatingSummaryStats(userId:Int) : DataFrame = {
    ratingsDF.where(s"userId == $userId").select("rating").describe()
  }

  def getTopGenresPerUserDF: DataFrame = {
    topGenresPerUserDF
  }

  def getTopGenresByUserId(userId:Int) : DataFrame = {
    topGenresPerUserDF.where(s"userId == $userId")
  }

  def getMovieById(movieId: Int): DataFrame = {
    moviesDF.where(s"movieId == $movieId")
  }
  def getRatingsForUser(userId:Int) : DataFrame = {
    ratingsDF.where(s"userId == $userId")
  }

  def getRecommendMoviesForUser(userId:Int) : DataFrame = {
    Logger.info(s"getRecommendMoviesForUser($userId)")

    trainModel()
    val perUserRecs:DataFrame = top10MoviesPerUser.where(s"userId == $userId")

    perUserRecs.selectExpr("userId","explode(recommendations)")
      .selectExpr("userId", "col.movieId", "col.rating")
  }

  def getRecommendUserForMovie(movieId:Int) : DataFrame = {
    Logger.info(s"getRecommendUserForMovie($movieId)")
    trainModel()
    val perMovieRecs:DataFrame = top10UsersPerMovie.where(s"movieId == $movieId")

    perMovieRecs.selectExpr("movieId","explode(recommendations)")
      .selectExpr("movieId", "col.userId", "col.rating")
  }

  def getStats : Stats = {
    stats
  }

  def getRecommendForAllUsers(noOfMoview:Int) : DataFrame = {
    model.recommendForAllUsers(noOfMoview)
  }

  def trainModel() : ALSModel = {
    if (model == null) {
      Logger.info("Training model......")
      val Array(trainingData, testData) = Init.ratingsDF.randomSplit(Array(0.8, 0.2))

      val als = new ALS().setRank(12)
        .setMaxIter(5)
        .setRegParam(0.02)
        .setUserCol("userId")
        .setItemCol("movieId")
        .setRatingCol("rating")

      // create an instance of ALS, set model parameters & hyperparameters
      model = als.fit(trainingData)

      top10MoviesPerUser = model.recommendForAllUsers(10)
      top10UsersPerMovie = model.recommendForAllItems(10)

      Logger.info("*** top10MoviesPerUser schema ***")
      top10MoviesPerUser.printSchema()

      Logger.info("Training model completed......")
    } else {
      Logger.warn("!!!model already trained")
    }
    model
  }
}

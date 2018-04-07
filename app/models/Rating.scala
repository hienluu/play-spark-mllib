package models

/**
  * Created by hluu on 4/4/18.
  */
case class Rating(userId:Int, movieId:Int, title:String, genres:String,
                  rating:Double, posterPath:String)

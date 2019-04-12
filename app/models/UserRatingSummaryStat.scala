package models

case class UserRatingSummaryStat(userId:Int, count:Int,
                                 mean:Float, stddev:Float,
                                 min:Float, max:Float)

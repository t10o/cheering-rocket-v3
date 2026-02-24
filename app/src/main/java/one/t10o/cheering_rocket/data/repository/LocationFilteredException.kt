package one.t10o.cheering_rocket.data.repository

/**
 * 位置情報がノイズとして除外されたことを示す例外
 */
class LocationFilteredException(message: String) : Exception(message)

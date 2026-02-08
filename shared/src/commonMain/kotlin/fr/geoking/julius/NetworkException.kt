package fr.geoking.julius.shared

class NetworkException(val httpCode: Int?, message: String) : Exception(message)

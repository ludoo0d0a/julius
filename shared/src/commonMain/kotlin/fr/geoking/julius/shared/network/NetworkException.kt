package fr.geoking.julius.shared.network

class NetworkException(val httpCode: Int?, message: String) : Exception(message)

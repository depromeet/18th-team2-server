package com.team2.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ServerApplication

fun main(args: Array<String>) {
    runApplication<ServerApplication>(*args)
}

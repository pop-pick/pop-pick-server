package com.poppick.poppick

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PoppickApplication

fun main(args: Array<String>) {
    runApplication<PoppickApplication>(*args)
}

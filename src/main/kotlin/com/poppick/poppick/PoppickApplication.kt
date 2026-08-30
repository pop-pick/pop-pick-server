package com.poppick.poppick

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PoppickApplication

fun main(args: Array<String>) {
    runApplication<PoppickApplication>(*args)
}

package org.common.marvel.mafia

import org.common.marvel.mafia.service.ConnectorService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MafiaBackendApplication

fun main(args: Array<String>) {
    val applicationContext = runApplication<MafiaBackendApplication>(*args)
    applicationContext.getBean(ConnectorService::class.java).start()
}


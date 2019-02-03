package org.common.marvel.mafia.service

import org.common.marvel.mafia.component.Room
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.annotation.Resource

@RunWith(SpringRunner::class)
@SpringBootTest
class ConnectorManagerTest {

    @Resource
    private lateinit var connectorManager: ConnectorManager

    @Test
    fun checkTimeoutExecute() {
        val id = UUID.randomUUID().toString()

        connectorManager.idRoomMap[id] = Room(id = id, name = "test", members = ConcurrentLinkedQueue(), werewolfMembers = ConcurrentLinkedQueue(), gameWerewolfCount = 2, accountSessionMap = connectorManager.accountSessionMap, sessionAccountMap = connectorManager.sessionAccountMap)

        connectorManager.checkTimeoutExecute()
    }

}
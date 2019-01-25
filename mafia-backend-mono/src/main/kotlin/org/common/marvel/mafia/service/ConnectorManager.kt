package org.common.marvel.mafia.service

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.component.GameProtocol
import org.common.marvel.mafia.component.GameRoom
import org.common.marvel.mafia.component.Type
import org.common.marvel.mafia.config.Cmd
import org.common.marvel.mafia.util.JsonUtils
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

@Component
class ConnectorManager {

    val sessionIdClientMap = HashMap<String, SocketIOClient>()

    val sessionAccountMap = HashMap<SocketIOClient, String>()

    val accountSessionMap = HashMap<String, SocketIOClient>()

    val idGameRoomMap = HashMap<String, GameRoom>()

    val sessionQueue = ConcurrentLinkedQueue<SocketIOClient>()

    // TODO : variable
    private val roomCount = 8

    @Scheduled(cron = "*/10 * * * * *")
    fun matchRoom() {
        val size = sessionQueue.size
        val count = size / roomCount

        if (sessionQueue.size >= roomCount) {
            for (i in 1 until count + 1) {
                val choosenSessionQueue = ConcurrentLinkedQueue<SocketIOClient>()
                for (j in 1 until roomCount + 1) {
                    choosenSessionQueue.offer(sessionQueue.poll())
                }

                val id = UUID.randomUUID().toString()
                idGameRoomMap.put(id, GameRoom(id, choosenSessionQueue.toList(), accountSessionMap, sessionAccountMap))

                idGameRoomMap.get(id)!!.members.stream().forEach {
                    it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.StartGame.name)))
                }
            }
        }
    }

}
package org.common.marvel.mafia.service

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.component.Room
import org.springframework.stereotype.Component

@Component
class ConnectorManager {

    val onlineSession = mutableListOf<SocketIOClient>()

    val sessionAccountMap = mutableMapOf<SocketIOClient, String>()

    val accountSessionMap = mutableMapOf<String, SocketIOClient>()

    val idRoomMap = mutableMapOf<String, Room>()

}
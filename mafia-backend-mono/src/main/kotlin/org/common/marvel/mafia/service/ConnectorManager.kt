package org.common.marvel.mafia.service

import com.corundumstudio.socketio.SocketIOClient
import org.springframework.stereotype.Component

@Component
class ConnectorManager {

    val sessionIdClientMap = HashMap<String, SocketIOClient>()

    val sessionIdAccountMap = HashMap<String, String>()

    val accountSessionIdMap = HashMap<String, String>()

}
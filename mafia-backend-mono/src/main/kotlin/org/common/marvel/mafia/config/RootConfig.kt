package org.common.marvel.mafia.config

import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import org.common.marvel.mafia.component.GameProtocol
import org.common.marvel.mafia.component.Type
import org.common.marvel.mafia.service.ConnectorManager
import org.common.marvel.mafia.util.JsonUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import javax.annotation.Resource

data class LoginMsg(val account: String)
data class BroadcastMsg(val msg: String)

enum class Cmd {

    System,
    Login,
    Users,
    Game,
    Broadcast

}

@Configuration
@EnableScheduling
@ComponentScan(value = ["org.common.marvel.mafia"])
class WebSocketConfig {

    @Resource
    private lateinit var connectorManager: ConnectorManager

    @Bean
    fun socketioServer(): SocketIOServer {
        val config = com.corundumstudio.socketio.Configuration()
        config.port = 9092
        config.getSocketConfig().isReuseAddress = true

        val server = SocketIOServer(config)

        server.addConnectListener {
            connectorManager.sessionIdClientMap.put(it.sessionId.toString(), it)
            it.sendEvent(Cmd.System.name, """[SYSTEM]: Hello ! I'm server ! """)
            server.allClients.stream()
                    .forEach {
                        it.sendEvent(Cmd.Users.name, genUserList(server.allClients.toList()))
                    }
        }

        server.addDisconnectListener {
            connectorManager.sessionIdClientMap.remove(it.sessionId.toString())
            server.allClients.stream()
                    .forEach {
                        it.sendEvent(Cmd.Users.name, genUserList(server.allClients.toList()))
                    }
        }

        server.addEventListener(Cmd.Login.name, String::class.java) { client, data, ackSender ->
            val loginMsg = JsonUtils.readValue<LoginMsg>(data.toString())

            connectorManager.accountSessionMap.put(loginMsg.account, client)
            connectorManager.sessionAccountMap.put(client, loginMsg.account)
            client.sendEvent(Cmd.System.name, """[SYSTEM]: ${loginMsg.account} login success ! """)
            server.allClients.stream()
                    .forEach {
                        it.sendEvent(Cmd.Users.name, genUserList(server.allClients.toList()))
                    }
        }

        server.addEventListener(Cmd.Broadcast.name, String::class.java) { client, data, ackSender ->
            val broadcastMsg = JsonUtils.readValue<BroadcastMsg>(data.toString())

            server.allClients.stream()
                    .forEach {
                        it.sendEvent(Cmd.Broadcast.name, """[${connectorManager.sessionAccountMap.get(client)}]: ${broadcastMsg.msg}""")
                    }
        }

        server.addEventListener(Cmd.Game.name, String::class.java) { client, data, ackSender ->
            val gameProtocol = JsonUtils.readValue<GameProtocol>(data.toString())
            val gameRoom = connectorManager.idGameRoomMap.get(gameProtocol.roomId)
            when (gameProtocol.name) {
                Type.Join.name -> connectorManager.sessionQueue.offer(client)
                else -> gameRoom?.receiveCmd(client, gameProtocol)
            }
        }

        return server
    }

    private fun genUserList(clients: List<SocketIOClient>): String {
        val stringBuilder = StringBuilder()

        clients.stream()
                .map { v -> connectorManager.sessionAccountMap.get(v) }
                .filter { v -> v != null }
                .forEach { v -> stringBuilder.append("<li>$v</li>") }

        return stringBuilder.toString()
    }

}

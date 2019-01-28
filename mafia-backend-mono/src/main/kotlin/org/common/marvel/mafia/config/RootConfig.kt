package org.common.marvel.mafia.config

import com.corundumstudio.socketio.SocketIOServer
import org.common.marvel.mafia.component.Type
import org.common.marvel.mafia.service.ConnectorManager
import org.common.marvel.mafia.service.GameProtocol
import org.common.marvel.mafia.util.JsonUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import javax.annotation.Resource

data class LoginMsg(val account: String)

enum class Cmd {

    System,
    Login,
    Users,
    Room,
    Game,

}

@Configuration
@EnableScheduling
@ComponentScan(value = ["org.common.marvel.mafia"])
class RootConfig {

    @Resource
    private lateinit var connectorManager: ConnectorManager

    @Bean
    fun socketioServer(): SocketIOServer {
        val config = com.corundumstudio.socketio.Configuration()
        config.port = 9092
        config.socketConfig.isReuseAddress = true

        val server = SocketIOServer(config)

        server.addConnectListener {
            connectorManager.onlineSession.add(it)
            it.sendEvent(Cmd.System.name, """[SYSTEM]: Hello ! I'm server ! """)
        }

        server.addDisconnectListener {
            connectorManager.onlineSession.remove(it)
        }

        server.addEventListener(Cmd.Login.name, String::class.java) { client, data, ackSender ->
            val loginMsg = JsonUtils.readValue<LoginMsg>(data.toString())

            connectorManager.accountSessionMap[loginMsg.account] = client
            connectorManager.sessionAccountMap[client] = loginMsg.account
            client.sendEvent(Cmd.System.name, """[SYSTEM]: ${loginMsg.account} login success ! """)
        }

        server.addEventListener(Cmd.Game.name, String::class.java) { client, data, ackSender ->
            val gameProtocol = JsonUtils.readValue<GameProtocol>(data.toString())
            val gameRoom = connectorManager.idGameRoomMap[gameProtocol.roomId]
            when (gameProtocol.name) {
                Type.Join.name -> {
                    connectorManager.sessionQueue.offer(client)
                }
                else -> gameRoom?.receiveCmd(client, gameProtocol)
            }
        }

        return server
    }

}

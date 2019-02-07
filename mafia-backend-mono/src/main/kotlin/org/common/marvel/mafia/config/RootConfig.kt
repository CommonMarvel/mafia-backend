package org.common.marvel.mafia.config

import com.corundumstudio.socketio.SocketIOClient
import com.corundumstudio.socketio.SocketIOServer
import com.fasterxml.jackson.module.kotlin.readValue
import org.common.marvel.mafia.component.MemberInfo
import org.common.marvel.mafia.component.Protocol
import org.common.marvel.mafia.component.Room
import org.common.marvel.mafia.component.RoomIdName
import org.common.marvel.mafia.component.Type
import org.common.marvel.mafia.service.ConnectorManager
import org.common.marvel.mafia.util.JsonUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.annotation.Resource

data class BindCmd(val account: String)
data class ChatCmd(val from: String, val to: String, val content: String)

enum class Channel {

    Bind,
    Broadcast,
    Chat,
    Game,

}

@Configuration
@EnableScheduling
@ComponentScan(value = ["org.common.marvel.mafia"])
class RootConfig {

    @Resource
    private lateinit var connectorManager: ConnectorManager

    private val gameMemberCount = 8

    private val gameWerewolfCount = 2

    @Bean
    fun socketioServer(): SocketIOServer {
        val config = com.corundumstudio.socketio.Configuration()
        config.port = 9092
        config.socketConfig.isReuseAddress = true

        val server = SocketIOServer(config)

        server.addConnectListener {
            connectorManager.onlineSession.add(it)
        }

        server.addDisconnectListener {
            connectorManager.onlineSession.remove(it)
        }

        server.addEventListener(Channel.Bind.name, String::class.java) { client, data, ackSender ->
            ackSender.sendAckData("${Channel.Bind.name} Receive")

            val bindCmd = JsonUtils.jsonMapper.readValue<BindCmd>(data.toString())

            connectorManager.accountSessionMap[bindCmd.account] = client
            connectorManager.sessionAccountMap[client] = bindCmd.account
        }

        server.addEventListener(Channel.Broadcast.name, String::class.java) { client, data, ackSender ->
            ackSender.sendAckData("${Channel.Broadcast.name} Receive")

            server.allClients.forEach { it.sendEvent(Channel.Broadcast.name, data) }
        }

        server.addEventListener(Channel.Chat.name, String::class.java) { client, data, ackSender ->
            ackSender.sendAckData("${Channel.Chat.name} Receive")

            val chatCmd = JsonUtils.jsonMapper.readValue<ChatCmd>(data.toString())

            connectorManager.accountSessionMap[chatCmd.to]?.sendEvent(Channel.Chat.name, JsonUtils.jsonMapper.writeValueAsString(ChatCmd(from = connectorManager.sessionAccountMap[client]!!, to = chatCmd.to, content = chatCmd.content)))
        }

        server.addEventListener(Channel.Game.name, String::class.java) { client, data, ackSender ->
            val protocol = JsonUtils.jsonMapper.readValue<Protocol>(data.toString())

            ackSender.sendAckData("${protocol.type} Receive")

            when (protocol.type) {
                Type.CreateRoom.name -> {
                    val uuid = UUID.randomUUID().toString()
                    val members = ConcurrentLinkedQueue<SocketIOClient>()
                    members.offer(client)
                    connectorManager.idRoomMap[uuid] = Room(id = uuid, name = protocol.name!!, members = members, werewolfMembers = ConcurrentLinkedQueue(), gameWerewolfCount = gameWerewolfCount, accountSessionMap = connectorManager.accountSessionMap, sessionAccountMap = connectorManager.sessionAccountMap)

                    val membersInfo = members.map { MemberInfo(connectorManager.sessionAccountMap[it], "", "") }.toList()

                    client.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = uuid, name = protocol.name, type = Type.CreateRoom.name, membersInfo = membersInfo, isPublic = protocol.isPublic)))
                }
                Type.ListRooms.name -> {
                    // TODO : all entries is bad
                    val notFullRooms = connectorManager.idRoomMap.entries.filter { it.value.members.size < gameMemberCount }.map { RoomIdName(id = it.key, name = it.value.name) }.toList()
                    client.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(type = Type.ListRooms.name, rooms = notFullRooms)))
                }
                Type.Join.name -> {
                    if (connectorManager.idRoomMap[protocol.roomId!!]!!.members.size < gameMemberCount) {
                        connectorManager.idRoomMap[protocol.roomId!!]!!.members.offer(client)

                        val membersInfo = connectorManager.idRoomMap[protocol.roomId!!]!!.members.map { MemberInfo(connectorManager.sessionAccountMap[it], "", "") }.toList()

                        client.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = protocol.roomId, type = Type.Join.name, membersInfo = membersInfo, msg = "Success")))
                    } else {
                        client.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = protocol.roomId, type = Type.Join.name, msg = "Fail, Room is Full")))
                    }
                }
                Type.LeaveRoom.name -> {
                    connectorManager.idRoomMap[protocol.roomId]!!.members.remove(client)
                    connectorManager.idRoomMap[protocol.roomId]!!.werewolfMembers.remove(client)

                    client.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = protocol.roomId, type = Type.LeaveRoom.name, msg = "Success")))
                }
                else -> connectorManager.idRoomMap[protocol.roomId]?.receive(client, protocol)
            }
        }

        return server
    }

}

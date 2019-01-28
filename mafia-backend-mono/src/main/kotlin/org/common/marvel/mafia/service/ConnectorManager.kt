package org.common.marvel.mafia.service

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.component.GameRoom
import org.common.marvel.mafia.component.Type
import org.common.marvel.mafia.config.Cmd
import org.common.marvel.mafia.util.JsonUtils
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.streams.toList

class MemberInfo(var account: String? = null,
                 var character: String? = null,
                 var status: String? = null)

class GameProtocol(var roomId: String? = null,
                   var name: String? = null,
                   var membersInfo: List<MemberInfo>? = null,
                   var killWho: String? = null,
                   var beatWho: String? = null,
                   var msg: String? = null)

enum class Character {

    Werewolf,
    Human

}

@Component
class ConnectorManager {

    val onlineSession = ArrayList<SocketIOClient>()

    val sessionAccountMap = HashMap<SocketIOClient, String>()

    val accountSessionMap = HashMap<String, SocketIOClient>()

    val idGameRoomMap = HashMap<String, GameRoom>()

    val sessionQueue = ConcurrentLinkedQueue<SocketIOClient>()

    val random = Random(Date().time)

    private val roomMemberCount = 4
    private val werewolfMemberCount = 2

    @Scheduled(cron = "*/10 * * * * *")
    fun matchRoom() {
        val size = sessionQueue.size
        val count = size / roomMemberCount
        if (sessionQueue.size >= roomMemberCount) {
            for (i in 1 until count + 1) {
                /* take element you need */
                val choosenSessionQueue = ConcurrentLinkedQueue<SocketIOClient>()
                for (j in 1 until roomMemberCount + 1) {
                    choosenSessionQueue.offer(sessionQueue.poll())
                }

                /* create session character map */
                val sessionCharacterMap = HashMap<SocketIOClient, String>()
                choosenSessionQueue.stream().forEach {
                    sessionCharacterMap[it] = Character.Human.name
                }
                val chooseSet = HashSet<Int>()
                chooseSet.add(random.nextInt(choosenSessionQueue.size))
                while (chooseSet.size < werewolfMemberCount) {
                    chooseSet.add(random.nextInt(choosenSessionQueue.size))
                }
                chooseSet.stream().forEach {
                    sessionCharacterMap[choosenSessionQueue.toList()[it]] = Character.Werewolf.name
                }

                /* create GameRoom and member infos */
                val id = UUID.randomUUID().toString()
                val memberInfos = choosenSessionQueue.stream()
                        .map { v -> MemberInfo(sessionAccountMap[v], sessionCharacterMap.get(v), "Alive") }
                        .toList()
                idGameRoomMap[id] = GameRoom(id, choosenSessionQueue.toList(), accountSessionMap, sessionAccountMap, memberInfos)
                idGameRoomMap[id]!!.werewolfsCount = werewolfMemberCount

                /* server send StartGame Cmd */
                choosenSessionQueue.stream().forEach {
                    it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.StartGame.name, memberInfos)))
                }
            }
        }
    }

    // not enough
//    @Scheduled(cron = "*/10 * * * * *")
//    fun disconnectCheck() {
//        idGameRoomMap.keys.stream()
//                .forEach { v ->
//                    idGameRoomMap.get(v)!!.membersInfo.forEach {
//                        if (!onlineSession.contains(accountSessionMap.get(it.account))) {
//                            it.status = "Disconnect"
//                        }
//                    }
//
//                    idGameRoomMap.get(v)!!.members.stream().forEach {
//                        it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(v, Type.MembersInfo.name, idGameRoomMap.get(v)!!.membersInfo)))
//                    }
//                }
//    }

}
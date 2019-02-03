package org.common.marvel.mafia.component

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.config.Channel
import org.common.marvel.mafia.util.JsonUtils
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList

data class RoomIdName(val id: String, val name: String)

class MemberInfo(var account: String? = null,
                 var character: String? = null,
                 var status: String? = null)

class Protocol(var roomId: String? = null,
               var name: String? = null,
               var type: String? = null,
               var rooms: List<RoomIdName>? = null,
               var membersInfo: List<MemberInfo>? = null,
               var killWho: String? = null,
               var beatWho: String? = null,
               var from: String? = null,
               var msg: String? = null,
               var isPublic: Boolean? = false)

enum class Type {

    RoomChat,
    WooChat,
    CreateRoom,
    ListRooms,
    LeaveRoom,
    Join,
    RequestStartGame,
    RequestSwitchDay,
    RequestSwitchNight,
    RequestKill,
    RequestBeat,
    StartGame,
    RequestDay,
    RequestNight,
    Kill,
    Beat,
    HumanWin,
    WerewolfWin,
    None,

}

enum class Character {

    Werewolf,
    Human,

}

enum class MemberStatus {

    Alive,
    Dead,

}

class Room(val id: String,
           val name: String,
           val members: ConcurrentLinkedQueue<SocketIOClient>,
           val werewolfMembers: ConcurrentLinkedQueue<SocketIOClient>,
           val gameWerewolfCount: Int,
           val accountSessionMap: MutableMap<String, SocketIOClient>,
           val sessionAccountMap: MutableMap<SocketIOClient, String>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val random = Random(Date().time)

    private var aliveMembersCount = members.size
    private var aliveWerewolfsCount = werewolfMembers.size

    private val membersInfo = mutableListOf<MemberInfo>()

    private val requestStartGameCount = AtomicInteger()
    private val requestSwitchDayCount = AtomicInteger()
    private val requestSwitchNightCount = AtomicInteger()
    private val requestKillCount = AtomicInteger()
    private val requestBeatCount = AtomicInteger()

    private var lastAction: Pair<String, LocalDateTime> = Pair(Type.None.name, LocalDateTime.now())

    private val deadSessionList = ArrayList<SocketIOClient>()

    private val killVoteList = ArrayList<String>()
    private val beatVoteList = ArrayList<String>()

    fun receive(client: SocketIOClient, protocol: Protocol) {
        when (protocol.type) {
            Type.RoomChat.name -> handleRoomChat(client, protocol)
            Type.WooChat.name -> handleWooChat(client, protocol)
            Type.RequestStartGame.name -> addCheckBroadcast(requestStartGameCount, protocol, aliveMembersCount)
            Type.RequestSwitchDay.name -> addCheckBroadcast(requestSwitchDayCount, protocol, aliveMembersCount)
            Type.RequestSwitchNight.name -> addCheckBroadcast(requestSwitchNightCount, protocol, aliveMembersCount)
            Type.RequestKill.name -> addCheckBroadcast(requestKillCount, protocol, aliveMembersCount)
            Type.RequestBeat.name -> addCheckBroadcast(requestBeatCount, protocol, aliveWerewolfsCount)
        }
    }

    suspend fun timeoutExecute() {
        if (lastAction.first != Type.None.name && LocalDateTime.now().isAfter(lastAction.second)) {
            when (lastAction.first) {
                Type.RequestStartGame.name -> processRequestStartGameCmd()
                Type.RequestSwitchDay.name -> processRequestSwitchDayCmd()
                Type.RequestSwitchNight.name -> processRequestSwitchNightCmd()
                Type.RequestKill.name -> processRequestKillCmd(members)
                Type.RequestBeat.name -> processRequestBeatCmd(members)
            }

            lastAction = Pair(Type.None.name, LocalDateTime.now())
        }
    }

    private fun handleRoomChat(client: SocketIOClient, protocol: Protocol) {
        members.forEach {
            it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = protocol.roomId, type = Type.RoomChat.name, from = sessionAccountMap[client], msg = protocol.msg)))
        }
    }

    private fun handleWooChat(client: SocketIOClient, protocol: Protocol) {
        werewolfMembers.forEach {
            it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = protocol.roomId, type = Type.WooChat.name, from = sessionAccountMap[client], msg = protocol.msg)))
        }
    }

    private fun addCheckBroadcast(cmdCount: AtomicInteger, protocol: Protocol, membersCount: Int) {
        val addAndGet = cmdCount.addAndGet(1)

        when (protocol.type) {
            Type.RequestKill.name -> killVoteList.add(protocol.killWho!!)
            Type.RequestBeat.name -> beatVoteList.add(protocol.beatWho!!)
        }

        if (addAndGet == 1) {
            when (protocol.type) {
                Type.RequestSwitchDay.name -> lastAction = Pair(Type.RequestDay.name, LocalDateTime.now().plusSeconds(30))
                Type.RequestSwitchNight.name -> lastAction = Pair(Type.RequestNight.name, LocalDateTime.now().plusSeconds(30))
                Type.RequestKill.name -> lastAction = Pair(Type.RequestKill.name, LocalDateTime.now().plusMinutes(3))
                Type.RequestBeat.name -> lastAction = Pair(Type.RequestBeat.name, LocalDateTime.now().plusMinutes(3))
            }
        }

        if (addAndGet == membersCount) {
            cmdCount.addAndGet(-1 * membersCount)

            when (protocol.type) {
                Type.RequestStartGame.name -> processRequestStartGameCmd()
                Type.RequestSwitchDay.name -> processRequestSwitchDayCmd()
                Type.RequestSwitchNight.name -> processRequestSwitchNightCmd()
                Type.RequestKill.name -> processRequestKillCmd(members)
                Type.RequestBeat.name -> processRequestBeatCmd(members)
            }
        }
    }

    private fun processRequestStartGameCmd() {
        members.forEach { membersInfo.add(MemberInfo(sessionAccountMap[it], Character.Human.name, MemberStatus.Alive.name)) }
        val chooseSet = HashSet<Int>()
        chooseSet.add(random.nextInt(members.size))
        while (chooseSet.size < gameWerewolfCount) {
            chooseSet.add(random.nextInt(members.size))
        }
        chooseSet.forEach {
            membersInfo[it].character = Character.Werewolf.name
        }

        members.forEach {
            it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.StartGame.name, membersInfo = membersInfo)))
        }
    }

    private fun processRequestSwitchDayCmd() {
        members.forEach {
            if (!deadSessionList.contains(it)) {
                it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.RequestDay.name, membersInfo = membersInfo)))
            }
        }
    }

    private fun processRequestSwitchNightCmd() {
        members.forEach {
            if (!deadSessionList.contains(it)) {
                it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.RequestNight.name, membersInfo = membersInfo)))
            }
        }
    }

    private fun processRequestKillCmd(members: ConcurrentLinkedQueue<SocketIOClient>) {
        /* sum the vote result  */
        val killSumPairList = killVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()
        if (killSumPairList.size == 2 && killSumPairList[0].second == 1 && killSumPairList[1].second == 1) {
            log.info("break even, skippppp ...")

            members.forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.Kill.name, membersInfo = membersInfo, killWho = "Nobody")))
            }
        } else {
            val sessionOfDead = accountSessionMap[killSumPairList[0].first]!!

            deadSessionList.add(sessionOfDead)
            aliveMembersCount -= 1
            if (werewolfMembers.contains(sessionOfDead)) {
                aliveWerewolfsCount -= 1
            }

            membersInfo.forEach {
                if (accountSessionMap[it.account]!! == sessionOfDead) {
                    it.status = MemberStatus.Dead.name
                }
            }

            members.forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.Kill.name, membersInfo = membersInfo, killWho = killSumPairList[0].first)))
            }

            killVoteList.clear()

            val aliveHumanSize = membersInfo.filter { it.character == Character.Human.name && it.status == MemberStatus.Alive.name }.size

            if (aliveMembersCount == aliveHumanSize) {
                members.forEach {
                    it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.HumanWin.name, membersInfo = membersInfo)))
                }
            }
            val aliveWerewolfSize = membersInfo.filter { it.character == Character.Werewolf.name && it.status == MemberStatus.Alive.name }.size
            if (aliveMembersCount == aliveWerewolfsCount && aliveMembersCount == aliveWerewolfSize) {
                members.forEach {
                    it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.HumanWin.name, membersInfo = membersInfo)))
                }
            }
        }
    }

    private fun processRequestBeatCmd(members: ConcurrentLinkedQueue<SocketIOClient>) {
        /* sum the vote result  */
        val beatSumPairList = beatVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()

        val sessionOfDead = accountSessionMap[beatSumPairList[0].first]!!

        deadSessionList.add(sessionOfDead)
        aliveMembersCount -= 1

        membersInfo.forEach {
            if (accountSessionMap[it.account]!! == sessionOfDead) {
                it.status = MemberStatus.Dead.name
            }
        }

        members.forEach {
            it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.Beat.name, membersInfo = membersInfo, beatWho = beatSumPairList[0].first)))
        }

        beatVoteList.clear()

        val aliveHumanSize = membersInfo.filter { it.character == Character.Human.name && it.status == MemberStatus.Alive.name }.size
        if (aliveMembersCount == aliveHumanSize) {
            members.forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.HumanWin.name, membersInfo = membersInfo)))
            }
        }
        val aliveWerewolfSize = membersInfo.filter { it.character == Character.Werewolf.name && it.status == MemberStatus.Alive.name }.size
        if (aliveMembersCount == aliveWerewolfsCount && aliveMembersCount == aliveWerewolfSize) {
            members.forEach {
                it.sendEvent(Channel.Game.name, JsonUtils.jsonMapper.writeValueAsString(Protocol(roomId = id, type = Type.HumanWin.name, membersInfo = membersInfo)))
            }
        }
    }

}

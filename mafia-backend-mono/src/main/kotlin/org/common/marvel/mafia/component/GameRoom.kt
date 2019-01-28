package org.common.marvel.mafia.component

import com.corundumstudio.socketio.SocketIOClient
import org.common.marvel.mafia.config.Cmd
import org.common.marvel.mafia.service.Character
import org.common.marvel.mafia.service.GameProtocol
import org.common.marvel.mafia.service.MemberInfo
import org.common.marvel.mafia.util.JsonUtils
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.streams.toList

enum class Type {

    RoomChat,
    WooChat,
    MembersInfo,
    Join,
    RequestSwitchDay,
    RequestSwitchNight,
    RequestKill,
    RequestBeat,
    StartGame,
    RequestDay,
    RequestNight,
    Kill,
    Beat,
    HuamnWin,
    WerewolfWin

}

class GameRoom(val id: String,
               val members: List<SocketIOClient>,
               val accountSessionMap: HashMap<String, SocketIOClient>,
               val sessionAccountMap: HashMap<SocketIOClient, String>,
               val membersInfo: List<MemberInfo>) {

    private val log = LoggerFactory.getLogger(javaClass)

    private var membersCount = members.size
    var werewolfsCount = 0

    private val requestSwitchDayCount = AtomicInteger()
    private val requestSwitchNightCount = AtomicInteger()
    private val requestKillCount = AtomicInteger()
    private val requestBeatCount = AtomicInteger()

    private val deadSessionList = ArrayList<SocketIOClient>()

    private val killVoteList = ArrayList<String>()
    private val beatVoteList = ArrayList<String>()

    fun receiveCmd(client: SocketIOClient, protocol: GameProtocol) {
        when (protocol.name) {
            Type.RoomChat.name -> handleRoomChat(client, protocol)
            Type.WooChat.name -> handleWooChat(client, protocol)
            Type.RequestSwitchDay.name -> addCheckBroadcast(requestSwitchDayCount, protocol)
            Type.RequestSwitchNight.name -> addCheckBroadcast(requestSwitchNightCount, protocol)
            Type.RequestKill.name -> addCheckBroadcast(requestKillCount, protocol)
            Type.RequestBeat.name -> addCheckBeat(requestBeatCount, protocol)
            // TODO else
        }
    }

    private fun handleRoomChat(client: SocketIOClient, protocol: GameProtocol) {
        members.stream().forEach {
            it.sendEvent(Cmd.Room.name, """[${sessionAccountMap[client]}]: ${protocol.msg}""")
        }
    }

    private fun handleWooChat(client: SocketIOClient, protocol: GameProtocol) {
        membersInfo.filter { it.character!!.equals(Character.Werewolf.name) }
                .map { accountSessionMap[it.account] }
                .forEach {
                    it!!.sendEvent(Cmd.Room.name, """[${sessionAccountMap[client]}]: ${protocol.msg}""")
                }
    }

    private fun addCheckBroadcast(cmdCount: AtomicInteger, protocol: GameProtocol) {
        val addAndGet = cmdCount.addAndGet(1)

        when (protocol.name) {
            Type.RequestKill.name -> killVoteList.add(protocol.killWho!!)
        }

        if (addAndGet == membersCount) {
            cmdCount.addAndGet(-1 * membersCount)

            when (protocol.name) {
                Type.RequestSwitchDay.name -> processRequestSwitchDayCmd(members)
                Type.RequestSwitchNight.name -> processRequestSwitchNightCmd(members)
                Type.RequestKill.name -> processRequestKillCmd(members)
            }
        }
    }

    private fun addCheckBeat(cmdCount: AtomicInteger, protocol: GameProtocol) {
        val addAndGet = cmdCount.addAndGet(1)

        beatVoteList.add(protocol.beatWho!!)

        if (addAndGet == werewolfsCount) {
            cmdCount.addAndGet(-1 * werewolfsCount)

            processRequestBeatCmd(members)
        }
    }

    private fun processRequestSwitchDayCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            if (!deadSessionList.contains(it)) {
                it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestDay.name, membersInfo)))
            }
        }
    }

    private fun processRequestSwitchNightCmd(members: List<SocketIOClient>) {
        members.stream().forEach {
            if (!deadSessionList.contains(it)) {
                it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.RequestNight.name, membersInfo)))
            }
        }
    }

    private fun processRequestKillCmd(members: List<SocketIOClient>) {
        /* sum the vote result  */
        val killSumPairList = killVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()
        if (killSumPairList.size == 2 && killSumPairList[0].second == 1 && killSumPairList[1].second == 1) {
            log.info("skippppp ...")

            members.stream().forEach {
                it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Kill.name, membersInfo, "Nobody")))
            }
        } else {
            val sessionOfDead = accountSessionMap[killSumPairList[0].first]!!

            val werewolfSessionMembers = membersInfo.filter { it.character!!.equals(Character.Werewolf.name) }
                    .map { accountSessionMap[it.account] }
                    .toList()

            deadSessionList.add(sessionOfDead)
            membersCount -= 1
            if (werewolfSessionMembers.contains(sessionOfDead)) {
                werewolfsCount -= 1
            }

            membersInfo.forEach {
                if (accountSessionMap[it.account]!! == sessionOfDead) {
                    it.status = "Dead"
                }
            }

            members.stream().forEach {
                it.sendEvent(Cmd.Users.name, genUserList(membersInfo))
                it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Kill.name, membersInfo, killSumPairList[0].first)))
            }

            killVoteList.clear()

            val aliveHumanSize = membersInfo.filter { it.character == Character.Human.name && it.status == "Alive" }.size
            if (membersCount == aliveHumanSize) {
                members.stream().forEach {
                    it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.HuamnWin.name, membersInfo)))
                }
            }
            val aliveWerewolfSize = membersInfo.filter { it.character == Character.Werewolf.name && it.status == "Alive" }.size
            if (membersCount == werewolfsCount && membersCount == aliveWerewolfSize) {
                members.stream().forEach {
                    it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.WerewolfWin.name, membersInfo)))
                }
            }
        }
    }

    private fun processRequestBeatCmd(members: List<SocketIOClient>) {
        /* sum the vote result  */
        val beatSumPairList = beatVoteList.groupBy { v -> v }.entries.stream().map { v -> Pair(v.key, v.value.size) }.sorted { o1, o2 -> o2.second - o1.second }.toList()

        val sessionOfDead = accountSessionMap[beatSumPairList[0].first]!!

        deadSessionList.add(sessionOfDead)
        membersCount -= 1

        membersInfo.forEach {
            if (accountSessionMap[it.account]!! == sessionOfDead) {
                it.status = "Dead"
            }
        }

        members.stream().forEach {
            it.sendEvent(Cmd.Users.name, genUserList(membersInfo))
            it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.Beat.name, membersInfo, "", beatSumPairList[0].first)))
        }

        beatVoteList.clear()

        val aliveHumanSize = membersInfo.filter { it.character == Character.Human.name && it.status == "Alive" }.size
        if (membersCount == aliveHumanSize) {
            members.stream().forEach {
                it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.HuamnWin.name, membersInfo)))
            }
        }
        val aliveWerewolfSize = membersInfo.filter { it.character == Character.Werewolf.name && it.status == "Alive" }.size
        if (membersCount == werewolfsCount && membersCount == aliveWerewolfSize) {
            members.stream().forEach {
                it.sendEvent(Cmd.Game.name, JsonUtils.writeValueAsString(GameProtocol(id, Type.WerewolfWin.name, membersInfo)))
            }
        }
    }

    private fun genUserList(membersInfo: List<MemberInfo>): String {
        val stringBuilder = StringBuilder()

        membersInfo.stream()
                .map { v ->
                    if (v.status == "Alive") {
                        v.account
                    } else {
                        v.account + " -> " + v.character
                    }
                }
                .forEach { v -> stringBuilder.append("<li>$v</li>") }

        return stringBuilder.toString()
    }

}

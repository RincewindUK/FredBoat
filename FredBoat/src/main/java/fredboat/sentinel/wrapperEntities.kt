package fredboat.sentinel

import com.fredboat.sentinel.entities.IMessage
import com.fredboat.sentinel.entities.MessageReceivedEvent
import com.fredboat.sentinel.entities.SendMessageResponse
import reactor.core.publisher.Mono
import java.util.regex.Pattern

typealias RawGuild = com.fredboat.sentinel.entities.Guild
typealias RawMember = com.fredboat.sentinel.entities.Member
typealias RawUser = com.fredboat.sentinel.entities.User
typealias RawTextChannel = com.fredboat.sentinel.entities.TextChannel
typealias RawVoiceChannel = com.fredboat.sentinel.entities.VoiceChannel
typealias RawRole = com.fredboat.sentinel.entities.Role
typealias RawMesssage = com.fredboat.sentinel.entities.Message

private val MENTION_PATTERN = Pattern.compile("<@!?([0-9]+)>", Pattern.DOTALL)

// TODO: These classes are rather inefficient. We should cache more things, and we should avoid duplication of Guild entities

class Guild(
        val id: Long
) {
    val raw: RawGuild
        get() = Sentinel.INSTANCE.getGuild(id)
    val name: String
        get() = raw.name
    val owner: Member?
        get() {
            if (raw.owner != null) return Member(raw.owner!!)
            return null
        }

    // TODO: Make these lazy so we don't have to recompute them
    val textChannels: List<TextChannel>
        get() = raw.textChannels.map { TextChannel(it, id) }
    val voiceChannels: List<VoiceChannel>
        get() = raw.voiceChannels.map { VoiceChannel(it, id) }
    val selfMember: Member
        get() = membersMap[Sentinel.INSTANCE.getApplicationInfo().botId.toString()]!!
    val members: List<Member>
        get() = raw.members.map { Member(it.value) }
    val membersMap: Map<String, Member>
        get() {
            val list = mutableMapOf<String, Member>()
            raw.members.forEach { (k, v) -> list[k] = Member(v) }
            return list
        }
    val roles: List<Role>
        get() = raw.roles.map { Role(it, id) }

    fun getVoiceChannel(id: Long): VoiceChannel? {
        voiceChannels.forEach { if (it.id == id) return it }
        return null
    }

    fun getRole(id: Long): Role? {
        roles.forEach { if(it.id == id) return it }
        return null
    }

    override fun equals(other: Any?): Boolean {
        return other is Guild && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class Member(val raw: RawMember) {
    val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val nickname: String?
        get() = raw.nickname
    val effectiveName: String
        get() = if (raw.nickname != null) raw.nickname!! else raw.name
    val discrim: Short
        get() = raw.discrim
    val guild: Guild
        get() = Guild(raw.guildId)
    val guildId: Long
        get() = raw.guildId
    val bot: Boolean
        get() = raw.bot
    val voiceChannel: VoiceChannel?
        get() {
            if (raw.voiceChannel != null) return guild.getVoiceChannel(raw.voiceChannel!!)
            return null
        }
    val roles: List<Role>
        get() {
            val list = mutableListOf<Role>()
            val guildRoles = guild.roles
            guildRoles.forEach { if (raw.roles.contains(it.id)) list.add(it) }
            return list.toList()
        }

    fun asMention() = "<@$id>"
    fun asUser(): User {
        return User(RawUser(
                id,
                name,
                discrim,
                bot
        ))
    }

    fun hasPermission(permissions: IPermissionSet): Mono<Boolean> =
            Sentinel.INSTANCE.checkPermissions(this, permissions)
                    .map { it.passed }

    override fun equals(other: Any?): Boolean {
        return other is Member && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class User(val raw: RawUser) {
    val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val discrim: Short
        get() = raw.discrim
    val bot: Boolean
        get() = raw.bot

    fun sendPrivate(message: String)
            = Sentinel.INSTANCE.sendPrivateMessage(this, RawMesssage(message))
    fun sendPrivate(message: IMessage)
            = Sentinel.INSTANCE.sendPrivateMessage(this, message)

    override fun equals(other: Any?): Boolean {
        return other is User && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

interface Channel {
    val id: Long
    val name: String
    val guild: Guild
    val ourEffectivePermissions: Long
}

class TextChannel(val raw: RawTextChannel, val guildId: Long) : Channel {
    override val id: Long
        get() = raw.id
    override val name: String
        get() = raw.name
    override val guild: Guild
        get() = Guild(guildId)
    override val ourEffectivePermissions: Long
        get() = raw.ourEffectivePermissions

    fun checkOurPermissions(permissions: IPermissionSet): Boolean =
            raw.ourEffectivePermissions and permissions.raw == permissions.raw

    fun send(str: String): Mono<SendMessageResponse> {
        return Sentinel.INSTANCE.sendMessage(raw, RawMesssage(str))
    }

    fun send(message: IMessage): Mono<SendMessageResponse> {
        return Sentinel.INSTANCE.sendMessage(raw, message)
    }

    fun sendTyping() {
        Sentinel.INSTANCE.sendTyping(raw)
    }

    override fun equals(other: Any?): Boolean {
        return other is TextChannel && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class VoiceChannel(val raw: RawVoiceChannel, val guildId: Long) : Channel {
    // TODO: List of members

    override val id: Long
        get() = raw.id
    override val name: String
        get() = raw.name
    override val guild: Guild
        get() = Guild(guildId)
    override val ourEffectivePermissions: Long
        get() = raw.ourEffectivePermissions

    override fun equals(other: Any?): Boolean {
        return other is VoiceChannel && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class Role(val raw: RawRole, val guildId: Long) {
    val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val permissions: PermissionSet
        get() = PermissionSet(raw.permissions)
    val guild: Guild
        get() = Guild(guildId)
    val publicRole: Boolean // The @everyone role shares the ID of the guild
        get() = id == guildId

    override fun equals(other: Any?): Boolean {
        return other is Role && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class Message(val raw: MessageReceivedEvent) {
    val id: Long
        get() = raw.id
    val content: String
        get() = raw.content
    val member: Member
        get() = Member(raw.author)
    val guild: Guild
        get() = Guild(raw.guildId)
    val channel: TextChannel
        get() = TextChannel(raw.channel, raw.guildId)
    val mentionedMembers: List<Member>
        get() {
            // Technically one could mention someone who isn't a member of the guild,
            // but we don't really care for that

            val matcher = MENTION_PATTERN.matcher(content)
            val list =  mutableListOf<Member>()
            val members = guild.membersMap
            while (matcher.find()) {
                members[matcher.group(1)]?.let { list.add(it) }
            }

            return list
        }
}
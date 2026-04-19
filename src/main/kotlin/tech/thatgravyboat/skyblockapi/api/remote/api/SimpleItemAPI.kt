package tech.thatgravyboat.skyblockapi.api.remote.api

import me.owdding.ktmodules.Module
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import tech.thatgravyboat.repolib.api.RepoAPI
import tech.thatgravyboat.repolib.api.RepoStatus
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockRarity
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnRepoStatus
import tech.thatgravyboat.skyblockapi.api.events.misc.RepoStatusEvent
import tech.thatgravyboat.skyblockapi.api.repo.LazyItemStack
import tech.thatgravyboat.skyblockapi.api.remote.PetQuery
import tech.thatgravyboat.skyblockapi.api.repo.RepoItemCache
import tech.thatgravyboat.skyblockapi.api.remote.RepoItemsAPI
import tech.thatgravyboat.skyblockapi.api.remote.RepoPetsAPI
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.UNKNOWN
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.attribute
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.enchantment
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.item
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.pet
import tech.thatgravyboat.skyblockapi.api.remote.api.SkyBlockId.Companion.rune
import tech.thatgravyboat.skyblockapi.utils.builders.ItemBuilder
import tech.thatgravyboat.skyblockapi.utils.extentions.*
import tech.thatgravyboat.skyblockapi.utils.json.getPath

@Module
object SimpleItemAPI {

    internal val unobtainableIds = SkyBlockAPI.getRepo("skyblockid/unobtainable_ids", SkyBlockId.CODEC.listOf())
    private val cache: RepoItemCache<SkyBlockId> = RepoItemCache { id ->
        val clean = id.cleanId.uppercase().takeUnless { it == UNKNOWN } ?: return@RepoItemCache null

        when {
            id.isPet -> {
                if (clean.contains(":")) {
                    val (petId, rarity) = clean.split(":")
                    val sbRarity = SkyBlockRarity.fromNameOrNull(rarity)
                    val pet = sbRarity?.let { RepoPetsAPI.getLazyItemStack(PetQuery(petId, it, 1)) }
                    if (pet != null) {
                        return@RepoItemCache pet
                    }
                }

                SkyBlockRarity.entries.reversed().firstNotNullOfOrNull { skyBlockRarity ->
                    runCatching {
                        RepoPetsAPI.getLazyItemStack(PetQuery(clean, skyBlockRarity, 1))
                    }.getOrNull()
                }
            }
            id.isRune -> RepoEnchantmentAPI.getLazyItemStack(if (clean.contains(":")) clean else "$clean:null")
            id.isEnchantment -> RepoEnchantmentAPI.getLazyItemStack(if (clean.contains(":")) clean else "$clean:null")
            id.isAttribute -> RepoAttributeAPI.getLazyItemStack(clean)
            id.isItem -> clean.let(RepoItemsAPI::getLazyItemStack)
            id.isUnsafe -> clean.let(RepoItemsAPI::getLazyItemStack)
            else -> null
        }
    }
    private val nameCache: MutableMap<String, SkyBlockId> = mutableMapOf()
    private val allIds: MutableList<SkyBlockId> = mutableListOf()

    init {
        if (RepoAPI.isInitialized()) setupCache()
    }

    fun findIdByName(name: String) = nameCache[name.lowercase().stripColor()]

    fun getItemByIdOrNull(id: SkyBlockId): ItemStack? = cache[id.trySafe(::item)]?.create()
    fun getItemById(id: SkyBlockId): ItemStack = getItemByIdOrNull(id) ?: ItemBuilder(Items.BARRIER) { name("Unknown item: $id") }

    fun getPetByIdOrNull(id: SkyBlockId): ItemStack? = cache[id.trySafe(::pet)]?.create()
    fun getPetById(id: SkyBlockId): ItemStack = getPetByIdOrNull(id) ?: ItemBuilder(Items.BARRIER) { name("Unknown pet: $id") }

    fun getRuneByIdOrNull(id: SkyBlockId): ItemStack? = cache[id.trySafe(::rune)]?.create()
    fun getRuneById(id: SkyBlockId) = getRuneByIdOrNull(id) ?: ItemBuilder(Items.BARRIER) { name("Unknown rune: $id") }

    fun getEnchantmentLazyItemStackByIdOrNull(id: SkyBlockId): LazyItemStack? = cache[id.trySafe(::enchantment)]
    fun getEnchantmentByIdOrNull(id: SkyBlockId): ItemStack? = getEnchantmentLazyItemStackByIdOrNull(id)?.create()
    fun getEnchantmentById(id: SkyBlockId): ItemStack = getEnchantmentByIdOrNull(id) ?: ItemBuilder(Items.BARRIER) { name("Unknown enchantment: $id") }

    fun getAttributeByIdOrNull(id: SkyBlockId): ItemStack? = cache[id.trySafe(::attribute)]?.create()
    fun getAttributeById(id: SkyBlockId): ItemStack = getAttributeByIdOrNull(id) ?: ItemBuilder(Items.BARRIER) { name("Unknown attribute: $id") }

    fun getAllIds(): List<SkyBlockId> = allIds
    fun getAllNames(): Set<String> = nameCache.keys

    @Subscription(RepoStatusEvent::class)
    @OnRepoStatus(RepoStatus.SUCCESS)
    fun onRepoStatus() {
        setupCache()
    }

    private fun List<Pair<String, SkyBlockId>>.applyFiltered() = this
        .apply { allIds.addAll(this.map { (_, id) -> id }) }
        .filter { (_, id) -> id !in unobtainableIds }
        .toMap()
        .let(nameCache::putAll)

    private fun setupCache() {
        val start = currentInstant()
        cache.clear()
        RepoAPI.pets().pets().entries.map { (id, data) -> data.name() to pet(id) }.applyFiltered()

        RepoAPI.runes().runes().entries.flatMap { (id, data) ->
            data.mapNotNull { rune ->
                rune.name().stripColor() to rune("$id", rune.tier())
            }
        }.applyFiltered()


        RepoAPI.enchantments().enchantments().flatMap { (id, enchantments) ->
            enchantments.levels().map { (_, enchantment) ->
                "${enchantments.name()} ${enchantment.literalLevel()}" to enchantment("$id:${enchantment.level()}")
            }
        }.applyFiltered()

        RepoAPI.attributes().attributes().flatMap { (_, attribute) ->
            listOf(
                attribute.name() to attribute(attribute.attributeId()),
                attribute.shardName() to attribute(attribute.attributeId()),
                attribute.shardName().removeSuffix("Shard").trim() to attribute(attribute.attributeId()),
            )
        }.applyFiltered()

        RepoAPI.items().items().entries.mapNotNull { (id, element) ->
            val components = element.getPath("['components'].['minecraft:custom_name'].['text']") ?: return@mapNotNull null
            components.asString.stripColor() to item(id)
        }.applyFiltered()

        val newCache = nameCache.flatMap { (key, value) ->
            val key = key.lowercase().stripColor()
            listOf(
                key to value,
                key.sanitizeForCommandInput() to value,
            )
        }.distinct().toMap()
        nameCache.clear()
        nameCache.putAll(newCache)
        SkyBlockAPI.trace("[SimpleItemAPI] Cached ${nameCache.size} item names and ${allIds.size} ids in ${start.since().toReadableTime(allowMs = true)}")
    }
}

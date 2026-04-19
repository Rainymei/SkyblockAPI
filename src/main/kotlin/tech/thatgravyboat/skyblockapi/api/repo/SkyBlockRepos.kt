package tech.thatgravyboat.skyblockapi.api.repo

import com.google.gson.JsonObject
import com.mojang.authlib.properties.Property
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import tech.thatgravyboat.repolib.api.EnchantsAPI
import tech.thatgravyboat.repolib.api.PetsAPI
import tech.thatgravyboat.repolib.api.ReforgeStonesAPI.ReforgeData
import tech.thatgravyboat.repolib.api.RepoAPI
import tech.thatgravyboat.repolib.api.RunesAPI.Rune
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockRarity
import tech.thatgravyboat.skyblockapi.platform.ResolvableProfile
import tech.thatgravyboat.skyblockapi.utils.extentions.*
import tech.thatgravyboat.skyblockapi.utils.json.getPath
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.Text.asComponent
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextProperties.stripped
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.italic

object SkyBlockItemsRepo : RepoItemCache<String>("Items") {

    private val repo get() = RepoAPI.items().items()
    private val names by lazy {
        this.repo.mapNotNull { entry ->
            val json = entry.value.getPath("['components'].['minecraft:custom_name'].['text']") ?: return@mapNotNull null
            val text = Text.of(json.asString("")).stripped.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // neu doesn't store them with : like hypixel does, we however use the hypixel format for eas of use
            text.lowercase() to entry.key.uppercase().replace("-", ":")
        }.toMap()
    }

    override fun create(key: String): LazyItemStack? {
        val id = key.uppercase().replace(":", "-").takeUnless { it == "MUSHROOM_COLLECTION" } ?: "RED_MUSHROOM"
        return RepoAPI.items().getItem(id)?.let(::LazyItemStack)
    }

    fun get(id: String): JsonObject? = ifInitialized { this.repo[id] }

    fun getIdByName(name: String): String? = ifInitialized {
        val lowercase = name.lowercase()
        names[lowercase]?.let { return it }
        val noStars = lowercase.removeTrailingChar('✪').trim()
        names[noStars]?.let { return it }
        val firstWhitespace = noStars.indexOf(' ')
        if (firstWhitespace == -1) return null
        val withoutFirstWord = noStars.substring(firstWhitespace).trim() // In case the item has a reforge
        return names[withoutFirstWord]
    }
}

object SkyBlockRunesRepo : RepoItemCacheAsQuery<SkyBlockRunesRepo.Query>("Runes", ::Query) {

    private val repo get() = RepoAPI.runes()

    override fun create(key: Query): LazyItemStack? {
        val rune = (if (key.tier == null) this.get(key.id)?.maxByOrNull(Rune::tier) else this.getTier(key.id, key.tier!!)) ?: return null

        return LazyItemStack(Items.PLAYER_HEAD) {
            this[DataComponents.PROFILE] = ResolvableProfile { put("textures", Property("textures", rune.texture())) }
            this[DataComponents.CUSTOM_NAME] = Text.of(rune.name())
            this[DataComponents.LORE] = ItemLore(rune.lore().map(Text::of))
        }
    }

    fun get(id: String): List<Rune>? = ifInitialized { this.repo.getRunes(id) }
    fun getTier(id: String, tier: Int): Rune? = get(id)?.find { it.tier() == tier }

    data class Query(
        var id: String = "",
        var tier: Int? = null
    )
}

object SkyBlockReforgeStonesRepo : RepoItemCache<String>("Reforge Stones") {

    private val repo get() = RepoAPI.reforgeStones()

    override fun create(key: String): LazyItemStack? {
        if (!this.repo.reforgeStones().containsKey(key)) return null
        return RepoAPI.items().getItem(key)?.let(::LazyItemStack)
    }

    fun get(id: String): ReforgeData? = ifInitialized { this.repo.getReforgeStone(id) }

    fun getIdByName(name: String): String? = ifInitialized { this.repo.reforgeStones().entries.find { it.value.name().equals(name, true) }?.key }
    fun getByName(name: String): Pair<String, ReforgeData>? = getIdByName(name)?.let { id -> get(id)?.let { id to it } }
}

object SkyBlockPetsRepo : RepoItemCacheAsQuery<SkyBlockPetsRepo.Query>("Pets", ::Query) {

    private val repo get() = RepoAPI.pets()

    override fun create(key: Query): LazyItemStack? {
        val data = RepoAPI.pets().getPet(key.id) ?: return null
        val pet = data.tiers()[key.rarity.name] ?: return null
        val skin = key.skin?.let { SkyBlockItemsRepo.getLazyItemStack("PET_SKIN_$it") }
        // TODO skin rarity can not be obtained via the lazy item stack api
        val name = Text.join(
            Text.of("[Lvl ${key.level}] ", TextColor.GRAY),
            Text.of(data.name(), key.rarity.color),
            if (key.skin != null) Text.of(" ✦", TextColor.LIGHT_PURPLE) else null,
        ) {
            this.italic = false
        }
        val lore = ItemLore(pet.getFormattedLore(key.level, key.heldItem).map(Text::of))

        return skin?.withComponents {
            this[DataComponents.CUSTOM_NAME] = name
            this[DataComponents.LORE] = lore
        } ?: LazyItemStack(Items.PLAYER_HEAD) {
            this[DataComponents.PROFILE] = ResolvableProfile { put("textures", Property("textures", pet.texture())) }
            this[DataComponents.CUSTOM_NAME] = name
            this[DataComponents.LORE] = lore
        }
    }

    fun get(id: String): PetsAPI.Data? = ifInitialized { this.repo.getPet(id) }

    data class Query(
        var id: String = "",
        var rarity: SkyBlockRarity = SkyBlockRarity.COMMON,
        var level: Int = 100,
        var skin: String? = null,
        var heldItem: String? = null
    )
}

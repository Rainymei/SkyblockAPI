package tech.thatgravyboat.skyblockapi.api.remote.api

import com.mojang.authlib.properties.Property
import me.owdding.ktmodules.Module
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import tech.thatgravyboat.repolib.api.AttributesAPI
import tech.thatgravyboat.repolib.api.RepoAPI
import tech.thatgravyboat.repolib.api.RepoStatus
import tech.thatgravyboat.skyblockapi.api.data.SkyBlockRarity
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnRepoStatus
import tech.thatgravyboat.skyblockapi.api.events.misc.RepoStatusEvent
import tech.thatgravyboat.skyblockapi.api.repo.LazyItemStack
import tech.thatgravyboat.skyblockapi.api.repo.RepoItemCache
import tech.thatgravyboat.skyblockapi.api.repo.RepoItemStackGetter
import tech.thatgravyboat.skyblockapi.platform.Identifiers
import tech.thatgravyboat.skyblockapi.platform.ResolvableProfile
import tech.thatgravyboat.skyblockapi.utils.extentions.compoundTag
import tech.thatgravyboat.skyblockapi.utils.extentions.putCompound
import tech.thatgravyboat.skyblockapi.utils.extentions.toData
import tech.thatgravyboat.skyblockapi.utils.text.Text
import tech.thatgravyboat.skyblockapi.utils.text.Text.asComponent
import tech.thatgravyboat.skyblockapi.utils.text.TextColor
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.color
import tech.thatgravyboat.skyblockapi.utils.text.TextStyle.italic

@Module
object RepoAttributeAPI : RepoItemStackGetter<String> {

    private val attributeIdMap: MutableMap<String, AttributesAPI.Attribute> = mutableMapOf()
    override val cache: RepoItemCache<String> = RepoItemCache { id ->
        val attribute = attributeIdMap[id.lowercase()] ?: RepoAPI.attributes().getAttribute(id)
        if (attribute == null) return@RepoItemCache null

        val item = Identifiers.parse(attribute.item().lowercase())
            ?.let(BuiltInRegistries.ITEM::getValue)
            ?.takeUnless { it == Items.AIR }
            ?: Items.BARRIER

        LazyItemStack(item.takeIf { attribute.texture() != null } ?: Items.PLAYER_HEAD) {
            if (attribute.texture() != null) {
                this[DataComponents.PROFILE] = ResolvableProfile { put("textures", Property("textures", attribute.texture())) }
            }
            this[DataComponents.ITEM_NAME] = attribute.shardName().asComponent()
            this[DataComponents.CUSTOM_NAME] = Text.of(attribute.shardName()) {
                this.italic = false
                runCatching {
                    this.color = SkyBlockRarity.valueOf(attribute.rarity()).color
                }
            }

            val rawLore = attribute.lore()
            val lore = rawLore.map { it.asComponent() }.toMutableList()
                .also { it.addFirst(Text.of(attribute.name()) { this.color = TextColor.GOLD }) }.toList()

            this[DataComponents.LORE] = ItemLore(lore, lore)
            this[DataComponents.CUSTOM_DATA] = compoundTag {
                putString("id", "ATTRIBUTE_SHARD")
                putCompound("attributes") {
                    putInt(attribute.id(), 1)
                }
            }.toData()
        }
    }

    @Subscription(RepoStatusEvent::class)
    @OnRepoStatus(RepoStatus.SUCCESS)
    fun onRepoReady() {
        attributeIdMap.putAll(RepoAPI.attributes().attributes().values.associateBy { it.attributeId().lowercase() })
    }

    fun getAttributeDataById(id: String): AttributesAPI.Attribute? {
        if (!RepoAPI.isInitialized()) return null
        return attributeIdMap[id.lowercase()] ?: RepoAPI.attributes().getAttribute(id)
    }

    @Deprecated("Replaced by this.getItemStack and this.getLazyItemStack", replaceWith = ReplaceWith("this.getItemStack(id)"))
    fun getAttributeByIdOrNull(id: String): ItemStack? = this.getItemStack(id)

}

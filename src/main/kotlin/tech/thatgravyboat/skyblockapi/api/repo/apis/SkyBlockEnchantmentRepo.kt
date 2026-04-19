package tech.thatgravyboat.skyblockapi.api.repo.apis

object SkyBlockEnchantmentRepo : RepoItemCacheAsQuery<SkyBlockEnchantmentRepo.Query>("Enchantments", ::Query) {

    private val repo get() = RepoAPI.enchantments()

    override fun create(key: Query): LazyItemStack? {
        val enchantment = get(key.id) ?: return null
        val enchantmentLevel = enchantment.levels().values.sortedBy(EnchantsAPI.EnchantLevel::level).firstOrElseLast { it.level() == key.level } ?: return null
        val lore = enchantmentLevel.lore().map { it.asComponent() }

        return LazyItemStack(Items.ENCHANTED_BOOK) {
            this[DataComponents.ITEM_NAME] = Text.of("${enchantment.name()} ${enchantmentLevel.literalLevel()}")
            this[DataComponents.LORE] = ItemLore(lore, lore)
            this[DataComponents.CUSTOM_DATA] = compoundTag {
                putString("id", "ENCHANTED_BOOK")
                putCompound("enchantments") {
                    putInt(enchantment.id(), enchantmentLevel.level())
                }
            }.toData()
        }
    }

    fun get(id: String): EnchantsAPI.Enchant? = ifInitialized { this.repo.getEnchantment(id) }

    data class Query(
        var id: String = "",
        var level: Int? = null
    )
}

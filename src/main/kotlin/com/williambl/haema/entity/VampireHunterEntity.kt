package com.williambl.haema.entity

import com.williambl.haema.Vampirable
import net.minecraft.block.entity.BannerPattern
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.*
import net.minecraft.entity.ai.goal.*
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.PatrolEntity
import net.minecraft.entity.passive.AbstractTraderEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.text.TranslatableText
import net.minecraft.util.DyeColor
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.world.LocalDifficulty
import net.minecraft.world.World
import net.minecraft.world.WorldAccess

class VampireHunterEntity(entityType: EntityType<out VampireHunterEntity>?, world: World?) : PatrolEntity(entityType, world), CrossbowUser {
    val inventory = SimpleInventory(5)

    override fun initialize(world: WorldAccess?, difficulty: LocalDifficulty?, spawnReason: SpawnReason?, entityData: EntityData?, entityTag: CompoundTag?): EntityData? {
        val result =  super.initialize(world, difficulty, spawnReason, entityData, entityTag)
        initEquipment(difficulty)
        return result
    }

    override fun initGoals() {
        super.initGoals()
        goalSelector.add(0, SwimGoal(this))
        goalSelector.add(2, VampireHunterCrossbowAttackGoal(this, 1.0, 8.0f))
        goalSelector.add(3, VampireHunterMeleeAttackGoal(this, 1.0, true))
        goalSelector.add(8, WanderAroundGoal(this, 0.6))
        goalSelector.add(9, LookAtEntityGoal(this, PlayerEntity::class.java, 15.0f, 1.0f))
        goalSelector.add(10, LookAtEntityGoal(this, MobEntity::class.java, 15.0f))

        targetSelector.add(1, RevengeGoal(this, AbstractTraderEntity::class.java).setGroupRevenge())
        targetSelector.add(2, FollowTargetGoal(this, PlayerEntity::class.java, 10, true, false) { it is Vampirable && it.isVampire })
    }

    override fun initDataTracker() {
        super.initDataTracker()
        dataTracker.startTracking(CHARGING, false)
    }

    override fun initEquipment(difficulty: LocalDifficulty?) {
        val crossbow = ItemStack(Items.CROSSBOW)

        val crossbowEnchants = mutableMapOf(Pair(Enchantments.QUICK_CHARGE, 3))
        if (random.nextInt(300) == 0) {
            crossbowEnchants[Enchantments.PIERCING] = 1
        }
        EnchantmentHelper.set(crossbowEnchants, crossbow)
        equip(300, crossbow)

        val sword = ItemStack(Items.WOODEN_SWORD)
        sword.addEnchantment(Enchantments.SMITE, 2)
        equip(301, sword)

        if (isPatrolLeader) {
            val banner = ItemStack(Items.WHITE_BANNER)
            val compoundTag: CompoundTag = banner.getOrCreateSubTag("BlockEntityTag")
            val listTag = BannerPattern.Patterns()
                .add(BannerPattern.RHOMBUS_MIDDLE, DyeColor.RED)
                .add(BannerPattern.HALF_HORIZONTAL, DyeColor.LIGHT_BLUE)
                .add(BannerPattern.CIRCLE_MIDDLE, DyeColor.RED)
                .toTag()
            compoundTag.put("Patterns", listTag)
            @Suppress("UsePropertyAccessSyntax")
            banner.getOrCreateTag().putInt("HideFlags", 32)
            banner.setCustomName(TranslatableText("block.haema.righteous_banner").formatted(Formatting.GOLD))
            equipStack(EquipmentSlot.HEAD, banner)
        }
    }

    override fun equip(slot: Int, item: ItemStack): Boolean {
        return if (super.equip(slot, item)) {
            true
        } else {
            val i = slot - 300
            if (i >= 0 && i < inventory.size()) {
                inventory.setStack(i, item)
                true
            } else {
                false
            }
        }
    }

    fun takeItem(item: Item): ItemStack {
        for (i in 0 until inventory.size()) {
            val stack = inventory.getStack(i)
            if (stack.item == item) {
                inventory.removeStack(i)
                return stack
            }
        }
        return ItemStack.EMPTY
    }

    override fun writeCustomDataToTag(tag: CompoundTag) {
        super.writeCustomDataToTag(tag)
        val listTag = ListTag()
        for (i in 0 until inventory.size()) {
            val itemStack = inventory.getStack(i)
            if (!itemStack.isEmpty) {
                listTag.add(itemStack.toTag(CompoundTag()))
            }
        }
        tag.put("Inventory", listTag)
    }

    override fun readCustomDataFromTag(tag: CompoundTag) {
        super.readCustomDataFromTag(tag)
        val listTag = tag.getList("Inventory", 10)
        for (i in listTag.indices) {
            val itemStack = ItemStack.fromTag(listTag.getCompound(i))
            if (!itemStack.isEmpty) {
                inventory.addStack(itemStack)
            }
        }
        setCanPickUpLoot(true)
    }

    override fun attack(target: LivingEntity?, pullProgress: Float) {
        shoot(this, 1.6f)
    }

    override fun postShoot() {}

    override fun setCharging(charging: Boolean) {
        dataTracker[CHARGING] = true
    }

    fun isCharging(): Boolean = dataTracker[CHARGING]

    override fun shoot(target: LivingEntity?, crossbow: ItemStack?, projectile: ProjectileEntity?, multiShotSpray: Float) {
        shoot(this, target, projectile, multiShotSpray, 1.6f)
    }

    override fun isTeammate(other: Entity?): Boolean {
        return if (super.isTeammate(other)) {
            true
        } else if (other is VampireHunterEntity || other is AbstractTraderEntity) {
            this.scoreboardTeam == null && other.scoreboardTeam == null
        } else {
            false
        }
    }

    companion object {
        val CHARGING: TrackedData<Boolean> = DataTracker.registerData(VampireHunterEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
    }
}

class VampireHunterCrossbowAttackGoal(private val actor: VampireHunterEntity, speed: Double, range: Float) : CrossbowAttackGoal<VampireHunterEntity>(actor, speed, range) {
    override fun canStart(): Boolean = hasValidTarget() && actorHasCrossbow()

    override fun shouldContinue(): Boolean =
        hasValidTarget() && (canStart() || !this.actor.navigation.isIdle) && actorHasCrossbow()

    private fun hasValidTarget(): Boolean = actor.target != null && actor.target!!.isAlive && actor.squaredDistanceTo(actor.target!!) > 16 && actor.target!!.health > 4

    private fun actorHasCrossbow(): Boolean =
        actor.isHolding(Items.CROSSBOW) || actor.inventory.containsAny(setOf(Items.CROSSBOW))

    override fun start() {
        super.start()
        if (!actor.isHolding(Items.CROSSBOW)) {
            val equipped = actor.getStackInHand(Hand.MAIN_HAND)
            actor.equipStack(EquipmentSlot.MAINHAND, actor.takeItem(Items.CROSSBOW))
            actor.inventory.addStack(equipped)
        }
    }

    override fun stop() {
        super.stop()
        if (actor.isHolding(Items.CROSSBOW)) {
            val equipped = actor.getStackInHand(Hand.MAIN_HAND)
            if (actor.inventory.canInsert(equipped)) {
                actor.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
                actor.inventory.addStack(equipped)
            }
        }
    }
}

class VampireHunterMeleeAttackGoal(private val actor: VampireHunterEntity, speed: Double, pauseWhenMobIdle: Boolean) : MeleeAttackGoal(actor, speed, pauseWhenMobIdle) {
    override fun canStart(): Boolean = super.canStart() && hasValidTarget() && actorHasSword()

    override fun shouldContinue(): Boolean = super.shouldContinue() && hasValidTarget() && actorHasSword()

    private fun hasValidTarget(): Boolean = actor.target != null && actor.target!!.isAlive

    private fun actorHasSword(): Boolean =
        actor.isHolding(Items.WOODEN_SWORD) || actor.inventory.containsAny(setOf(Items.WOODEN_SWORD))

    override fun start() {
        super.start()
        if (!actor.isHolding(Items.WOODEN_SWORD)) {
            val equipped = actor.getStackInHand(Hand.MAIN_HAND)
            actor.equipStack(EquipmentSlot.MAINHAND, actor.takeItem(Items.WOODEN_SWORD))
            actor.inventory.addStack(equipped)
        }
    }

    override fun tick() {
        if (actor.target == null) return
        super.tick()
    }

    override fun stop() {
        super.stop()
        if (actor.isHolding(Items.WOODEN_SWORD)) {
            val equipped = actor.getStackInHand(Hand.MAIN_HAND)
            if (actor.inventory.canInsert(equipped)) {
                actor.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY)
                actor.inventory.addStack(equipped)
            }
        }
    }
}

package com.williambl.haema.plugin.origins

import io.github.apace100.origins.power.Power
import io.github.apace100.origins.power.PowerType
import io.github.apace100.origins.registry.ModRegistries
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import java.lang.reflect.Constructor
import java.util.function.BiFunction


lateinit var powerTypeCtor: Constructor<PowerType<*>>

@Suppress("UNCHECKED_CAST")
fun <T: Power> createPowerType(factory: BiFunction<PowerType<*>, PlayerEntity, T>): PowerType<T> =
    powerTypeCtor.newInstance(factory) as PowerType<T>

fun registerPowerTypes() {
    //Reflection because an accessor mixin would require the mod to be loaded, but we don't want a hard dep
    @Suppress("UNCHECKED_CAST")
    powerTypeCtor = Class.forName("io.github.apace100.origins.power.PowerType").declaredConstructors.first {
        it.parameterTypes.first() == BiFunction::class.java
    } as Constructor<PowerType<*>>
    powerTypeCtor.isAccessible = true

    Registry.register(
        ModRegistries.POWER_TYPE,
        Identifier("haema:vampire"),
        createPowerType(BiFunction<PowerType<*>, PlayerEntity, VampirePower> { type, player -> VampirePower(type, player) })
    )
    registerDummyPowerType("haema:immortality")
    registerDummyPowerType("haema:night_vision")
    registerDummyPowerType("haema:vampiric_strength")
    registerDummyPowerType("haema:dash")
}

fun registerDummyPowerType(id: String) {
    Registry.register(
        ModRegistries.POWER_TYPE,
        Identifier(id),
        createPowerType(BiFunction { type, player -> Power(type, player) })
    )
}
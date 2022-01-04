package xyz.atrius.waystones.data.config

import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.*
import kotlin.reflect.KClass

interface Parser<T> {

    /**
     * Parses the given data into the specified output type
     * this will return null if there is a problem with parsing
     * the data.
     *
     * @param input The input value to parse.
     * @property T The parser return type. Null if error occurs.
     * @return The parsed data, or null if an error occurred.
     */
    fun parse(input: Any?): T?

    fun toString(value: T): String =
        value.toString()
}

object StringParser : Parser<String> {
    override fun parse(input: Any?) = input.toString()
}

sealed class IntParser : Parser<Int> {
    override fun parse(input: Any?): Int? = input?.toString()?.toIntOrNull()

    companion object : IntParser()
}

object PositiveValueParser : IntParser() {

    override fun parse(input: Any?): Int? {
        val num = super.parse(input) ?: return null
        return if (isValid(num)) num else null
    }

    private fun isValid(value: Int): Boolean = value >= 0
}

class RangeParser(private val range: IntRange) : IntParser() {

    override fun parse(input: Any?): Int? {
        val value = super.parse(input)
        return if (value in range) value else null
    }
}

object DoubleParser : Parser<Double> {
    override fun parse(input: Any?): Double? = input?.toString()?.toDoubleOrNull()
}

object PercentageParser : Parser<Double> {
    private val regex = "^[0-9]+(.[0-9]+)?%$".toRegex()

    override fun parse(input: Any?): Double? {
        val str = input?.toString()
        return if (str?.matches(regex) == true)
            str.dropLast(1).toDouble() / 100 else null
    }

    override fun toString(value: Double): String =
        "${value * 100}%"
}

object BooleanParser : Parser<Boolean> {
    override fun parse(input: Any?): Boolean? {
        val str = input?.toString()
        return when (str?.lowercase()) {
            "1", "t", "true"  -> true
            "0", "f", "false" -> false
            else              -> null
        }
    }
}

object LocaleParser : Parser<Locale> {
    override fun parse(input: Any?): Locale? = input?.let { Locale.forLanguageTag(input.toString()) }

    override fun toString(value: Locale): String = value.toLanguageTag()
}

object LocationParser : Parser<Location> {
    private val regex = "^(\\w+)@([0-9A-F]{8})([0-9A-F]{8})([0-9A-F]{8})$"
        .toRegex(RegexOption.IGNORE_CASE)

    override fun parse(input: Any?): Location? {
        val (_, world, x, y, z) =
            regex.find(input.toString())?.groupValues ?: return null
        return Location(
            Bukkit.getWorld(world),
            x.toDouble(),
            y.toDouble(),
            z.toDouble()
        )
    }

    override fun toString(value: Location): String =
        "${value.world}@{${value.blockX.toHex()}${value.blockY.toHex()}${value.blockZ.toHex()}"

    private fun Int.toHex() =
        toString(16).padStart(8, '0')
}

class EnumParser<E : Enum<E>>(private val enum: KClass<E>) : Parser<E> {
    override fun parse(input: Any?): E? {
        input ?: return null
        val values = enum.java.enumConstants
        return values.firstOrNull { it.name.equals(input.toString(), true) }
    }
}

class ListParser<T>(private val parser: Parser<T>) : Parser<List<T>> {
    @Suppress("UNCHECKED_CAST")
    override fun parse(input: Any?): List<T>? {
        if (input is List<*>)
            return input as List<T>?
        val str = input?.toString()
        // Parse data in either list or vararg form
        val data = str?.removeSurrounding("[", "]")
            ?.split("[ ,] *".toRegex()) ?: return null
        val arr = mutableListOf<Any>()
        // Populate the array
        for (item in data)
            arr += (parser.parse(item) ?: return null)
        return arr as List<T>?
    }
}
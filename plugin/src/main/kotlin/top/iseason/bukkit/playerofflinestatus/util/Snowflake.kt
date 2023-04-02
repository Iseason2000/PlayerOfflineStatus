package top.iseason.bukkit.playerofflinestatus.util

import java.net.NetworkInterface
import kotlin.math.abs

class Snowflake(private val workerId: Long) {
    private val datacenterId = abs(getMachineId() % 32)
    private val twEpoch = 1634393012000L
    private val datacenterIdBits = 5
    private val workerIdBits = 5
    private val maxDatacenterId = -1L xor (-1L shl datacenterIdBits)
    private val maxWorkerId = -1L xor (-1L shl workerIdBits)
    private val sequenceBits = 12
    private val workerIdShift = sequenceBits
    private val datacenterIdShift = sequenceBits + workerIdBits
    private val timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits
    private val sequenceMask = -1L xor (-1L shl sequenceBits)

    private var sequence = 0L
    private var lastTimestamp = -1L

    init {
        require(datacenterId in 0..maxDatacenterId) { "Datacenter ID must be between 0 and $maxDatacenterId" }
        require(workerId in 0..maxWorkerId) { "Worker ID must be between 0 and $maxWorkerId" }
    }

    fun nextId(): Long {
        var timestamp = timeGen()

        if (timestamp < lastTimestamp) {
            throw RuntimeException("Clock moved backwards. Refusing to generate id for ${lastTimestamp - timestamp} milliseconds.")
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) and sequenceMask
            if (sequence == 0L) {
                timestamp = tilNextMillis(lastTimestamp)
            }
        } else {
            sequence = 0L
        }

        lastTimestamp = timestamp

        return (timestamp - twEpoch shl timestampLeftShift) or (datacenterId shl datacenterIdShift) or (workerId shl workerIdShift) or sequence
    }

    private fun tilNextMillis(lastTimestamp: Long): Long {
        var timestamp = timeGen()
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen()
        }
        return timestamp
    }

    private fun timeGen(): Long {
        return System.currentTimeMillis()
    }

    private fun getMachineId(): Long {
        // 获取所有网络接口
        val interfaces = NetworkInterface.getNetworkInterfaces()

        // 遍历网络接口，找到第一个非虚拟网卡的 MAC 地址
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isVirtual) {
                continue
            }
            val macAddress = networkInterface.hardwareAddress
            if (macAddress != null && macAddress.isNotEmpty()) {
                // 将 MAC 地址转换为字符串
                val hexString = StringBuilder()
                for (b in macAddress) {
                    hexString.append(String.format("%02X", b))
                }
                // 将字符串解析为 Long 类型的值
                return java.lang.Long.parseLong(hexString.toString(), 16)
            }
        }

        throw RuntimeException("Could not determine machine ID")
    }

}
package crazydude.com.telemetry.protocol

import android.util.Log
import crazydude.com.telemetry.protocol.crc.CRCMAVLink
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import crazydude.com.telemetry.protocol.decoder.MAVLinkDataDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MAVLink2Protocol : Protocol {

    constructor(dataListener: DataDecoder.Listener) : super(MAVLinkDataDecoder(dataListener))
    constructor(dataDecoder: DataDecoder) : super(dataDecoder)

    private val crc = CRCMAVLink()

    private var state = State.IDLE
    private var buffer: IntArray = IntArray(0)
    private var payloadIndex = 0
    private var packetLength = 0
    private var packetIncompatibility = 0
    private var packetCompatibility = 0
    private var packetIndex = 0
    private var systemId = 0
    private var componentId = 0
    private var messageId = 0
    private var messageIdBuffer = ByteArray(4)
    private var messageIdIndex = 0
    private var crcLow: Int? = null
    private var crcHigh: Int? = null
    private var unique = HashSet<Int>()
    private var gotRadioStatus = false;  //preffer RADIO_STATUS messages over RC_CHANNELS_RAW

    companion object {
        enum class State {
            IDLE, LENGTH, INCOMPATIBILITY, COMPATIBILITY, INDEX, SYSTEM_ID, COMPONENT_ID, MESSAGE_ID, PAYLOAD, CRC
        }

        private const val PACKET_MARKER = 0xFD

        private const val MAV_PACKET_HEARTBEAT_ID = 0
        private const val MAV_PACKET_STATUS_ID = 1
        private const val MAV_PACKET_ATTITUDE_ID = 30
        private const val MAV_PACKET_RC_CHANNELS_RAW_ID = 35
        private const val MAV_PACKET_RC_CHANNELS_ID = 65
        private const val MAV_PACKET_VFR_HUD_ID = 74
        private const val MAV_PACKET_GPS_RAW_ID = 24
        private const val MAV_PACKET_RADIO_STATUS_ID = 109
        private const val MAV_PACKET_GPS_ORIGIN_ID = 49
        private const val MAV_PACKET_HOME_POSITION_ID = 242
        private const val MAV_PACKET_STATUSTEXT_ID = 253

        private const val MAV_PACKET_STATUS_LENGTH = 31
        private const val MAV_PACKET_HEARTBEAT_LENGTH = 9
        private const val MAV_PACKET_RC_CHANNEL_LENGTH = 22
        private const val MAV_PACKET_ATTITUDE_LENGTH = 28
        private const val MAV_PACKET_VFR_HUD_LENGTH = 20
        private const val MAV_PACKET_GPS_RAW_LENGTH = 30
        private const val MAV_PACKET_RADIO_STATUS_LENGTH = 9
        private const val MAV_PACKET_HOME_POSITION_LENGTH = 52
        private const val MAV_PACKET_STATUSTEXT_LEN = 54
        private const val MAV_PACKET_TRANSMISSION_HANDSHAKE = 130
        private const val MAV_PACKET_ENCAPSULATED_DATA      = 131
    }

    override fun process(data: Int) {
        when (state) {
            State.IDLE -> {
                if (data == PACKET_MARKER) {
                    state = State.LENGTH
                }
            }
            State.LENGTH -> {
                packetLength = data
                state = State.INCOMPATIBILITY
            }
            State.INCOMPATIBILITY -> {
                packetIncompatibility = data
                state = State.COMPATIBILITY
            }
            State.COMPATIBILITY -> {
                packetCompatibility = data
                state = State.INDEX
            }
            State.INDEX -> {
                packetIndex = data
                state = State.SYSTEM_ID
            }
            State.SYSTEM_ID -> {
                systemId = data
                state = State.COMPONENT_ID
            }
            State.COMPONENT_ID -> {
                componentId = data
                state = State.MESSAGE_ID
                messageIdIndex = 0
            }
            State.MESSAGE_ID -> {
                messageIdBuffer[messageIdIndex++] = data.toByte()
                if (messageIdIndex >= 3) {
                    messageId = ByteBuffer.wrap(messageIdBuffer).order(ByteOrder.LITTLE_ENDIAN).int
                    state = State.PAYLOAD
                    payloadIndex = 0
                    buffer = IntArray(packetLength)
                }
            }
            State.PAYLOAD -> {
                if (payloadIndex < packetLength) {
                    buffer[payloadIndex] = data
                    payloadIndex++
                } else {
                    state = State.CRC
                    crcLow = data
                    crcHigh = null
                }
            }
            State.CRC -> {
                crcHigh = data
                if (checkCrc()) {
                    processPacket()
                } else {
                    Log.d("MAVLink2Protocol", "Bad CRC for $messageId")
                }
                state = State.IDLE
            }
        }
    }

    private fun processPacket() {
        val byteBuffer = ByteBuffer.wrap(buffer.copyOf(255).map { it.toByte() }.toByteArray())
            .order(ByteOrder.LITTLE_ENDIAN)
        if (messageId == MAV_PACKET_STATUS_ID) {
            val sensors = byteBuffer.int
            val enabledSensors = byteBuffer.int
            val healthSensors = byteBuffer.int
            val load = byteBuffer.short
            val voltage = byteBuffer.short
            val current = byteBuffer.short
            val dropRate = byteBuffer.short
            val errors = byteBuffer.short
            val errorsCount1 = byteBuffer.short
            val errorsCount2 = byteBuffer.short
            val errorsCount3 = byteBuffer.short
            val errorsCount4 = byteBuffer.short
            val fuel = byteBuffer.get()

            dataDecoder.decodeData(
                Protocol.Companion.TelemetryData(
                    VBAT,
                    voltage.toInt()
                )
            )
            dataDecoder.decodeData(
                Protocol.Companion.TelemetryData(
                    Protocol.CURRENT,
                    current.toInt()
                )
            )
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(Protocol.FUEL, fuel.toInt()))
        } else if (messageId == MAV_PACKET_STATUSTEXT_ID) {
            val severity = byteBuffer.get()
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(STATUSTEXT, severity.toInt(), byteBuffer.array()))
        } else if (messageId == MAV_PACKET_HEARTBEAT_ID) {
            val customMode = byteBuffer.int
            val aircraftType = byteBuffer.get()
            val autopilotClass = byteBuffer.get()
            val mode = byteBuffer.get()
            val state = byteBuffer.get()
            val version = byteBuffer.get()
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(FLYMODE, mode.toInt(), byteBuffer.array()))

            val rawMode = mode.toInt();
            val armed = (rawMode and MAVLinkDataDecoder.MAV_MODE_FLAG_SAFETY_ARMED) == MAVLinkDataDecoder.MAV_MODE_FLAG_SAFETY_ARMED;
            this.processArmed(armed);
        } else if (messageId == MAV_PACKET_RC_CHANNELS_RAW_ID) {
            //Channels RC
            //mavlink_rc_channels_raw_t
            //https://github.com/iNavFlight/inav/blob/master/lib/main/MAVLink/common/mavlink_msg_rc_channels_raw.h
            val time = byteBuffer.int
            val channel0 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_0,channel0.toInt()))
            val channel1 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_1,channel1.toInt()))
            val channel2 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_2,channel2.toInt()))
            val channel3 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_3,channel3.toInt()))
            val channel4 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_4,channel4.toInt()))
            val channel5 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_5,channel5.toInt()))
            val channel6 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_6,channel6.toInt()))
            val channel7 = byteBuffer.short
            dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_7,channel7.toInt()))
            val port = byteBuffer.get()
            val rssi = byteBuffer.get().toInt() and 0xff
            if ( !gotRadioStatus)
                dataDecoder.decodeData( Protocol.Companion.TelemetryData(RSSI,rssi.toInt()))
        } else if (messageId == MAV_PACKET_RC_CHANNELS_ID) {
            //Channels RC
            //mavlink_rc_channels_t
            //https://github.com/iNavFlight/inav/blob/master/lib/main/MAVLink/common/mavlink_msg_rc_channels.h
            val time = byteBuffer.int
            val channel0 = byteBuffer.short
            val channel1 = byteBuffer.short
            val channel2 = byteBuffer.short
            val channel3 = byteBuffer.short
            val channel4 = byteBuffer.short
            val channel5 = byteBuffer.short
            val channel6 = byteBuffer.short
            val channel7 = byteBuffer.short
            val channel8 = byteBuffer.short
            val channel9 = byteBuffer.short
            val channel10 = byteBuffer.short
            val channel11 = byteBuffer.short
            val channel12 = byteBuffer.short
            val channel13 = byteBuffer.short
            val channel14 = byteBuffer.short
            val channel15 = byteBuffer.short
            val channel16 = byteBuffer.short
            val channel17 = byteBuffer.short
            val channelsCount = byteBuffer.get()

            if ( channelsCount > 0 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_0,channel0.toInt()))
            if ( channelsCount > 1 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_1,channel1.toInt()))
            if ( channelsCount > 2 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_2,channel2.toInt()))
            if ( channelsCount > 3 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_3,channel3.toInt()))
            if ( channelsCount > 4 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_4,channel4.toInt()))
            if ( channelsCount > 5 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_5,channel5.toInt()))
            if ( channelsCount > 6 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_6,channel6.toInt()))
            if ( channelsCount > 7 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_7,channel7.toInt()))
            if ( channelsCount > 8 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_8,channel8.toInt()))
            if ( channelsCount > 9 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_9,channel9.toInt()))
            if ( channelsCount > 10 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_10,channel10.toInt()))
            if ( channelsCount > 11 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_11,channel11.toInt()))
            if ( channelsCount > 12 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_12,channel12.toInt()))
            if ( channelsCount > 13 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_13,channel13.toInt()))
            if ( channelsCount > 14 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_14,channel14.toInt()))
            if ( channelsCount > 15 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_15,channel15.toInt()))
            if ( channelsCount > 16 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_16,channel16.toInt()))
            if ( channelsCount > 17 ) dataDecoder.decodeData( Protocol.Companion.TelemetryData(RC_CHANNEL_17,channel17.toInt()))

            val rssi = byteBuffer.get().toInt() and 0xff
            if ( !gotRadioStatus)
                dataDecoder.decodeData( Protocol.Companion.TelemetryData(RSSI,rssi.toInt()))
        } else if (messageId == MAV_PACKET_ATTITUDE_ID) {
            dataDecoder.decodeData(
                Protocol.Companion.TelemetryData(
                    ATTITUDE,
                    0,
                    byteBuffer.array()
                )
            )
        } else if (messageId == MAV_PACKET_VFR_HUD_ID) {
            val airSpeed = byteBuffer.float
            val groundSpeed = byteBuffer.float
            val alt = byteBuffer.float
            val vspeed = byteBuffer.float
            val heading = byteBuffer.short
            val throttle = byteBuffer.short

            dataDecoder.decodeData( Protocol.Companion.TelemetryData( GSPEED, (groundSpeed * 100).toInt()))
            dataDecoder.decodeData( Protocol.Companion.TelemetryData( ASPEED, (airSpeed * 100).toInt()))
            dataDecoder.decodeData( Protocol.Companion.TelemetryData( VSPEED, (vspeed * 100).toInt()))
            dataDecoder.decodeData( Protocol.Companion.TelemetryData( THROTTLE, throttle.toInt()))
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(ALTITUDE, (alt * 100).toInt()))

        } else if (messageId == MAV_PACKET_RADIO_STATUS_ID) {
            val rxErrors = byteBuffer.short
            val fixed = byteBuffer.short
            val rssi = byteBuffer.get().toInt() and 0xff
            val remRssi = byteBuffer.get()
            val txbuf = byteBuffer.get()
            val noise = byteBuffer.get()
            val remnoise = byteBuffer.get()
            gotRadioStatus = true;
            dataDecoder.decodeData( Protocol.Companion.TelemetryData( RSSI, rssi.toInt()))
        } else if (messageId == MAV_PACKET_GPS_RAW_ID) {
            val time = byteBuffer.long
            val lat = byteBuffer.int 
            val lon = byteBuffer.int
            val altitude = byteBuffer.int
            val eph = byteBuffer.short
            val epv = byteBuffer.short
            val vel = byteBuffer.short
            val cog = byteBuffer.short
            val fixType = byteBuffer.get()
            val satellites = byteBuffer.get()

            dataDecoder.decodeData( Protocol.Companion.TelemetryData( Protocol.GPS_STATE, fixType.toInt()))
            dataDecoder.decodeData( Protocol.Companion.TelemetryData( Protocol.GPS_SATELLITES, satellites.toInt()))
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(Protocol.GPS_ALTITUDE, altitude))
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(Protocol.GPS_LATITUDE, lat))
            this.processLatitude(lat / 10000000.toDouble());
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(Protocol.GPS_LONGITUDE, lon))
            this.processLongitude(lon / 10000000.toDouble());

            if (cog.toInt() != -1)
                dataDecoder.decodeData( Protocol.Companion.TelemetryData( Protocol.HEADING, cog.toInt()))
        } else if (messageId == MAV_PACKET_GPS_ORIGIN_ID) {
            val lat = byteBuffer.int
            val lon = byteBuffer.int

            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS_ORIGIN_LATITUDE, lat))
            this.processOriginLatitude(lat / 10000000.toDouble())
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS_ORIGIN_LONGITUDE, lon))
            this.processOriginLongitude(lon / 10000000.toDouble())
        } else if (messageId == MAV_PACKET_HOME_POSITION_ID && packetLength == MAV_PACKET_HOME_POSITION_LENGTH) {
            val lat = byteBuffer.int
            val lon = byteBuffer.int

            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS_HOME_LATITUDE, lat))
            this.processHomeLatitude(lat / 10000000.toDouble())
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(GPS_HOME_LONGITUDE, lon))
            this.processHomeLongitude(lon / 10000000.toDouble())
        } else if (messageId == MAV_PACKET_TRANSMISSION_HANDSHAKE) {
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(IMAGE_HANDSHAKE, 0, byteBuffer.array()))
        } else if (messageId == MAV_PACKET_ENCAPSULATED_DATA) {
            dataDecoder.decodeData(Protocol.Companion.TelemetryData(IMAGE_DATA, 0, byteBuffer.array()))
        } else {
            unique.add(messageId)
        }
    }

    private fun checkCrc(): Boolean {
        crc.start_checksum()
        crc.update_checksum(packetLength)
        crc.update_checksum(packetIncompatibility)
        crc.update_checksum(packetCompatibility)
        crc.update_checksum(packetIndex)
        crc.update_checksum(systemId)
        crc.update_checksum(componentId)
        messageIdBuffer.copyOfRange(0, 3).forEach { crc.update_checksum(it.toInt()) }
        buffer.forEach { crc.update_checksum(it) }
        crc.finish_checksum(messageId)
        return crcHigh == crc.msb && crcLow == crc.lsb
    }
}
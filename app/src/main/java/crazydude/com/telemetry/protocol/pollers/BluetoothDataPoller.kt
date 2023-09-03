package crazydude.com.telemetry.protocol.pollers

import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import crazydude.com.telemetry.protocol.*
import crazydude.com.telemetry.protocol.decoder.DataDecoder
import java.io.FileOutputStream
import java.io.IOException

class BluetoothDataPoller(
    private val bluetoothSocket: BluetoothSocket,
    private val listener: DataDecoder.Listener,
    outputStream: FileOutputStream?
) : DataPoller {

    private var selectedProtocol: Protocol? = null
    private lateinit var thread: Thread

    private var connectedOnce : Boolean = false;

    init {
        connectedOnce = false;
        thread = Thread(Runnable {
            try {
                bluetoothSocket.connect()
                if (bluetoothSocket.isConnected) {
                    connectedOnce = true
                    runOnMainThread(Runnable {
                        listener.onConnected()
                    })
                }
                val protocolDetector =
                    ProtocolDetector(object :
                        ProtocolDetector.Callback {
                        override fun onProtocolDetected(protocol: Protocol?) {
                            when (protocol) {
                                is FrSkySportProtocol -> {
                                    selectedProtocol =
                                        FrSkySportProtocol(
                                            listener
                                        )
                                    listener?.onProtocolDetected("FrSky")
                                }

                                is CrsfProtocol -> {
                                    selectedProtocol =
                                        CrsfProtocol(
                                            listener
                                        )
                                    listener?.onProtocolDetected("CRSF")
                                }

                                is LTMProtocol -> {
                                    selectedProtocol =
                                        LTMProtocol(
                                            listener
                                        )
                                    listener?.onProtocolDetected("LTM")
                                }

                                is MAVLinkProtocol -> {
                                    selectedProtocol =
                                        MAVLinkProtocol(
                                            listener
                                        )
                                    listener?.onProtocolDetected("Mavlink v1")
                                }

                                is MAVLink2Protocol -> {
                                    selectedProtocol = MAVLink2Protocol(
                                        listener
                                    )
                                    listener?.onProtocolDetected("Mavlink v2")
                                }

                                else -> {
                                    thread.interrupt()
                                }
                            }
                        }
                    })
                val buffer = ByteArray(1024)
                while (!thread.isInterrupted && bluetoothSocket.isConnected) {
                    val size = bluetoothSocket.inputStream.read(buffer)
                    outputStream?.write(buffer, 0, size)
                    for (i in 0 until size) {
                        if (selectedProtocol == null) {
                            listener?.onTelemetryByte();
                            protocolDetector.feedData(buffer[i].toUByte().toInt())
                        } else {
                            listener?.onTelemetryByte();
                            selectedProtocol?.process(buffer[i].toUByte().toInt())
                        }
                    }
                }
            } catch (e: IOException) {
                try {
                    outputStream?.close()
                } catch (e: IOException) {
                    // ignore
                }

                try {
                    bluetoothSocket.close()
                } catch (e: IOException) {
                    // ignore
                }

                runOnMainThread(Runnable {
                    if (connectedOnce) {
                        listener.onDisconnected()
                    }
                    else {
                        listener.onConnectionFailed()
                    }
                })
                return@Runnable
            }
        })

        thread.start()
    }


    private fun runOnMainThread(runnable: Runnable) {
        Handler(Looper.getMainLooper())
            .post {
                runnable.run()
            }
    }

    override fun disconnect() {
        thread.interrupt()
        try{
            this.bluetoothSocket.close();
        } catch (e: IOException) {
        // ignore
        }
    }
}
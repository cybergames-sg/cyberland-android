package com.bcm.messenger.common.server

import com.bcm.messenger.common.AccountContext
import com.bcm.messenger.common.bcmhttp.WebSocketHttp
import com.bcm.messenger.common.core.BcmHttpApiHelper
import com.bcm.messenger.common.utils.log.ACLog
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import com.google.protobuf.InvalidProtocolBufferException
import com.orhanobut.logger.Logger
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.whispersystems.libsignal.util.Pair
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos
import java.io.IOException
import java.util.concurrent.TimeUnit

class ServerConnection(private val accountContext: AccountContext, private val scheduler: Scheduler, private val userAgent: String) {

    private val RESPONSE_REFUSE = 403
    private val RESPONSE_GONE = 410

    private val TAG = "ServerConnection"

    private val outgoingRequests = mutableMapOf<Long, SettableFuture<Pair<Int, String>>>()

    private var webSocketHttp: WebSocketHttp = WebSocketHttp(accountContext)
    private var client: WebSocket? = null
    private var protoDataEvent: IServerProtoDataEvent? = null
    private var websocketEvent = WebsocketConnectionListener(false, true, scheduler)

    private var connectState = ConnectState.INIT

    private val wsUri = BcmHttpApiHelper.getApi("/v1/websocket/?login=%s&password=%s").replace("https://", "wss://")

    private var connectToken = 0

    private var connectingTime = 0L



    fun setConnectionListener(listener: IServerProtoDataEvent?) {
        protoDataEvent = listener
    }


    fun isDisconnect(): Boolean {
        return connectState == ConnectState.INIT || connectState == ConnectState.DISCONNECTED
    }

    fun isTimeout(): Boolean {
        if (connectingTime > 0L) {
            return (System.currentTimeMillis() - connectingTime) >= 10_000L
        }
        return false
    }

    fun isConnected(): Boolean {
        return connectState == ConnectState.CONNECTED
    }

    fun connect(token: Int = 0): Boolean {
        ACLog.i(accountContext, TAG,"WSC connect()...")

        return connectImpl(false, token)
    }

    private fun connectImpl(forRetry: Boolean, token: Int): Boolean {
        if (isDisconnect()) {
            connectingTime = 0

            connectToken = token
            updateConnectState(ConnectState.CONNECTING)

            val url = String.format(wsUri, accountContext.uid, accountContext.password)
            ACLog.d(accountContext, TAG, "WebSocketConnection filledUri: $url")
            ACLog.i(accountContext, TAG, "WSC connecting...")

            connectingTime = System.currentTimeMillis()
            websocketEvent.destroyed = true
            websocketEvent = WebsocketConnectionListener(forRetry, false, scheduler)
            this.client = webSocketHttp.connect(url, userAgent, websocketEvent)
        }
        return true
    }

    fun disconnect() {
        ACLog.i(accountContext, TAG, "WSC disconnect()...")

        websocketEvent.destroyed = true
        clearConnection(1000, "OK")
        updateConnectState(connectState)
    }

    fun state(): ConnectState {
        return connectState
    }

    private fun updateConnectState(state: ConnectState) {
        this.connectState = state
        protoDataEvent?.onServiceConnected(accountContext, connectToken, connectState)
    }

    fun sendRequest(request: WebSocketProtos.WebSocketRequestMessage, callback: SettableFuture<Pair<Int, String>>) {
        val client = this.client
        if (null == client || !isConnected()) {
            callback.setException(IOException("No connection!"))
            return
        }

        val message = WebSocketProtos.WebSocketMessage.newBuilder()
                .setType(WebSocketProtos.WebSocketMessage.Type.REQUEST)
                .setRequest(request)
                .build()

        outgoingRequests[request.id] = callback

        if (!client.send(ByteString.of(*message.toByteArray()))) {
            outgoingRequests.remove(request.id)
            callback.setException(IOException("send request failed"))
        }
    }

    fun sendResponse(response: WebSocketProtos.WebSocketResponseMessage, callback: SettableFuture<Boolean>) {
        val client = this.client
        if (null == client || !isConnected()) {
            callback.setException(IOException("No connection!"))
            return
        }

        val message = WebSocketProtos.WebSocketMessage.newBuilder()
                .setType(WebSocketProtos.WebSocketMessage.Type.RESPONSE)
                .setResponse(response)
                .build()

        if (!client.send(ByteString.of(*message.toByteArray()))) {
            callback.setException(IOException("send response failed!"))
        } else {
            callback.set(true)
        }
    }

    fun sendKeepAlive(): Boolean {
        val client = this.client
        ACLog.i(accountContext, TAG, "sending keep alive")
        if (client != null && isConnected()) {
            val message = WebSocketProtos.WebSocketMessage.newBuilder()
                    .setType(WebSocketProtos.WebSocketMessage.Type.REQUEST)
                    .setRequest(WebSocketProtos.WebSocketRequestMessage.newBuilder()
                            .setId(System.currentTimeMillis())
                            .setPath("/v1/keepalive")
                            .setVerb("GET")
                            .build()).build()
                    .toByteArray()

            if (client.send(ByteString.of(*message))) {
                ACLog.i(accountContext, TAG, "keep alive succeed")
                return true
            }
        }

        return false
    }


    private fun clearConnection(code: Int, reason: String?) {
        if (isConnected()) {
            try {
                val iterator = outgoingRequests.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    entry.value.setException(IOException("Closed $code reason:$reason"))
                    iterator.remove()
                }

                val client = this.client
                this.client = null
                client?.close(1000, "OK")

            } catch (e: Throwable) {
                ACLog.e(accountContext, TAG, "disconnect", e)
            }
        }

        connectingTime = 0

        connectState = ConnectState.DISCONNECTED
    }

    inner class WebsocketConnectionListener(private val forRetry: Boolean, destroy: Boolean = false, private val scheduler: Scheduler) : WebSocketListener() {
        private var connected = false
        private var retryDisposable:Disposable? = null
        var destroyed = false
            set(value) {
                field = value
                if (destroyed) {
                    retryDisposable?.dispose()
                    retryDisposable = null
                }
            }

        init {
            this.destroyed = destroy
        }

        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            if (destroyed) {
                ACLog.i(accountContext, TAG, "ignore onOpen")
                return
            }

            ACLog.i(accountContext, TAG, "onConnected()")
            connectingTime = 0

            updateConnectState(ConnectState.CONNECTED)

            connected = true
            connectToken = 0
        }

        override fun onMessage(webSocket: WebSocket?, payload: ByteString?) {
            if (destroyed) {
                ACLog.i(accountContext, TAG, "ignore onMessage")
                return
            }

            ACLog.i(accountContext, TAG, "WSC onMessage()")
            try {
                val message = WebSocketProtos.WebSocketMessage.parseFrom(payload!!.toByteArray())

                ACLog.i(accountContext, TAG, " Message Type: " + message.getType().getNumber())

                if (message.type.number == WebSocketProtos.WebSocketMessage.Type.REQUEST_VALUE) {
                    protoDataEvent?.onMessageArrive(accountContext, message.request)
                } else if (message.type.number == WebSocketProtos.WebSocketMessage.Type.RESPONSE_VALUE) {
                    val listener = outgoingRequests[message.response.id]
                    listener?.set(Pair(message.response.status,
                            String(message.response.body.toByteArray())))
                }
            } catch (e: InvalidProtocolBufferException) {
                ACLog.e(accountContext, TAG, "onMessage", e)
            }
        }

        override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
            if (destroyed) {
                ACLog.i(accountContext, TAG, "ignore onClosed")
                return
            }

            ACLog.i(accountContext, TAG, "onClose()...")
            clearConnection(code, reason)
        }

        override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) {
            if (destroyed) {
                ACLog.i(accountContext, TAG, "ignore onFailure")
                return
            }
            ACLog.e(accountContext, TAG, "onFailure()", t)

            val connected = this.connected
            if (response != null) {
                val code = response.code()
                ACLog.e(accountContext, TAG, "onFailure() code: $code")

                if (code == RESPONSE_REFUSE) {
                    val info = response.header("X-Online-Device")
                    //???
                    protoDataEvent?.onClientForceLogout(accountContext, info, KickEvent.OTHER_LOGIN)

                } else if (code == RESPONSE_GONE) {
                    protoDataEvent?.onClientForceLogout(accountContext, null, KickEvent.ACCOUNT_GONE)
                }
            }

            if (!isDisconnect()) {
                clearConnection(1000, "OK")
            }

            if (connected || !forRetry ) {
                retryDisposable = Observable.create<Boolean> {
                    if (accountContext.isLogin && !destroyed && !forRetry) {
                        connectImpl(true, connectToken)
                    }
                }.delaySubscription(1000, TimeUnit.MILLISECONDS)
                        .subscribeOn(scheduler)
                        .observeOn(scheduler)
                        .subscribe ({},{})
            }
        }

        override fun onMessage(webSocket: WebSocket?, text: String?) {
            if (destroyed) {
                ACLog.i(accountContext, TAG, "ignore onMessage")
                return
            }
            ACLog.i(accountContext, TAG, "onMessage(text)! $text")
        }

        override fun onClosing(webSocket: WebSocket?, code: Int, reason: String?) {
            if (destroyed) {
                ACLog.i(accountContext, TAG, "ignore onClosing")
                return
            }
            ACLog.i(accountContext, TAG, "onClosing()!...")
            if (!isDisconnect()) {
                webSocket?.close(1000, "OK")
            }
        }
    }

    interface IServerProtoDataEvent {
        fun onServiceConnected(accountContext: AccountContext, connectToken: Int, state: ConnectState)
        fun onMessageArrive(accountContext: AccountContext, message: WebSocketProtos.WebSocketRequestMessage): Boolean
        fun onClientForceLogout(accountContext: AccountContext, info: String?, type: KickEvent)
    }

}
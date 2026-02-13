package com.mrboombastic.buwudzik.device

import android.content.res.Resources
import com.mrboombastic.buwudzik.R

/**
 * Reason for BLE disconnection
 */
sealed class DisconnectionReason {
    abstract fun getMessage(context: Resources): String
    open fun getHint(context: Resources): String? = null

    data object DeviceTerminated : DisconnectionReason() {
        override fun getMessage(context: Resources) =
            context.getString(R.string.disconnect_reason_device_terminated)

        override fun getHint(context: Resources) =
            context.getString(R.string.auth_hint)
    }

    data object ConnectionTimeout : DisconnectionReason() {
        override fun getMessage(context: Resources) =
            context.getString(R.string.disconnect_reason_timeout)

        override fun getHint(context: Resources) =
            context.getString(R.string.hint_check_nearby)
    }

    data object LinkLost : DisconnectionReason() {
        override fun getMessage(context: Resources) =
            context.getString(R.string.disconnect_reason_link_lost)
    }

    data object UserRequested : DisconnectionReason() {
        override fun getMessage(context: Resources) =
            context.getString(R.string.disconnect_reason_user_requested)
    }

    data class Unknown(val status: Int, val hintResId: Int? = null) : DisconnectionReason() {
        override fun getMessage(context: Resources) =
            context.getString(R.string.disconnect_reason_unknown, status)

        override fun getHint(context: Resources) = hintResId?.let { context.getString(it) }
    }

    companion object {
        fun fromGattStatus(status: Int): DisconnectionReason = when (status) {
            0 -> UserRequested // GATT_SUCCESS - normal disconnect
            8 -> ConnectionTimeout // GATT_CONN_TIMEOUT
            19 -> DeviceTerminated // GATT_CONN_TERMINATE_PEER_USER
            22 -> LinkLost // GATT_CONN_TERMINATE_LOCAL_HOST
            133 -> Unknown(
                status,
                R.string.hint_restart_bluetooth
            ) // GATT_ERROR
            else -> Unknown(status)
        }
    }
}

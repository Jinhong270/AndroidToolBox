package com.jh270.toolbox.data

import android.content.Context

object AppSettings {
    private const val PREFS = "toolbox_prefs"
    private const val AGREEMENT = "agreement_accepted"
    private const val TERMINAL_BG = "terminal_background"
    private const val TERMINAL_SELECTION = "terminal_selection"

    const val DEFAULT_TERMINAL_BG = 0xFF0A0E14.toInt()
    const val DEFAULT_TERMINAL_SELECTION = 0xCC2563EB.toInt()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun agreementAccepted(context: Context): Boolean = prefs(context).getBoolean(AGREEMENT, false)

    fun setAgreementAccepted(context: Context) {
        prefs(context).edit().putBoolean(AGREEMENT, true).apply()
    }

    fun terminalBackground(context: Context): Int = prefs(context).getInt(TERMINAL_BG, DEFAULT_TERMINAL_BG)

    fun setTerminalBackground(context: Context, color: Int) {
        prefs(context).edit().putInt(TERMINAL_BG, color).apply()
    }

    fun terminalSelection(context: Context): Int = prefs(context).getInt(TERMINAL_SELECTION, DEFAULT_TERMINAL_SELECTION)

    fun setTerminalSelection(context: Context, color: Int) {
        prefs(context).edit().putInt(TERMINAL_SELECTION, color).apply()
    }
}

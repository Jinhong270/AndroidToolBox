package com.jh270.toolbox.ui

import android.content.Context
import com.jh270.toolbox.R
import com.jh270.toolbox.domain.ContactInfo
import com.jh270.toolbox.domain.DomainDates
import com.jh270.toolbox.domain.DomainRecord
import com.jh270.toolbox.domain.DomainStatus
import com.jh270.toolbox.domain.ExpiryHint
import com.jh270.toolbox.domain.ExpiryPhase
import com.jh270.toolbox.domain.sourceLabel

private val statusIds = mapOf(
    "ok" to R.string.status_ok,
    "active" to R.string.status_ok,
    "inactive" to R.string.status_inactive,
    "associated" to R.string.status_associated,
    "locked" to R.string.status_locked,
    "client delete prohibited" to R.string.status_client_delete,
    "client transfer prohibited" to R.string.status_client_transfer,
    "client update prohibited" to R.string.status_client_update,
    "client renew prohibited" to R.string.status_client_renew,
    "client hold" to R.string.status_client_hold,
    "server delete prohibited" to R.string.status_server_delete,
    "server transfer prohibited" to R.string.status_server_transfer,
    "server update prohibited" to R.string.status_server_update,
    "server renew prohibited" to R.string.status_server_renew,
    "server hold" to R.string.status_server_hold,
    "pending delete" to R.string.status_pending_delete,
    "pending transfer" to R.string.status_pending_transfer,
    "pending update" to R.string.status_pending_update,
    "pending create" to R.string.status_pending_create,
    "pending renew" to R.string.status_pending_renew,
    "pending restore" to R.string.status_pending_restore,
    "redemption period" to R.string.status_redemption,
    "add period" to R.string.status_add_period,
    "auto renew period" to R.string.status_auto_renew,
    "renew period" to R.string.status_renew_period,
    "transfer period" to R.string.status_transfer_period
)

fun Context.statusText(status: DomainStatus): String {
    val id = statusIds[status.code] ?: return status.label
    return getString(id)
}

fun Context.roleText(role: String): String {
    val id = when (role) {
        "registrant" -> R.string.role_registrant
        "administrative" -> R.string.role_admin
        "technical" -> R.string.role_tech
        "billing" -> R.string.role_billing
        else -> return role
    }
    return getString(id)
}

fun Context.dnssecText(value: String): String {
    val id = when (value) {
        "signed" -> R.string.dnssec_signed
        "unsigned" -> R.string.dnssec_unsigned
        else -> return value
    }
    return getString(id)
}

fun Context.expiryText(hint: ExpiryHint): String {
    val count = hint.days.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return when (hint.phase) {
        ExpiryPhase.DUE_TODAY -> getString(R.string.domain_due_today)
        ExpiryPhase.EXPIRED_TODAY -> getString(R.string.domain_expired_today)
        ExpiryPhase.REMAINING -> resources.getQuantityString(R.plurals.domain_expires_in_days, count, count)
        ExpiryPhase.EXPIRED -> resources.getQuantityString(R.plurals.domain_expired_days, count, count)
    }
}

fun Context.contactSummary(contact: ContactInfo): String {
    if (!contact.visible) return getString(R.string.contact_hidden)
    return listOfNotNull(
        contact.name,
        contact.organization,
        contact.email,
        contact.phone,
        contact.country?.let { getString(R.string.contact_country, it) }
    ).joinToString(", ")
}

fun Context.domainSummary(record: DomainRecord, now: Long = System.currentTimeMillis()): String {
    val lines = mutableListOf<String>()
    lines += getString(R.string.summary_domain, record.unicodeName)
    if (!record.asciiName.equals(record.unicodeName, ignoreCase = true)) {
        lines += getString(R.string.summary_punycode, record.asciiName)
    }
    if (record.statuses.isNotEmpty()) {
        val joined = record.statuses.joinToString(getString(R.string.status_join)) { statusText(it) }
        lines += getString(R.string.summary_status, joined)
    }
    record.registrar?.name?.let { lines += getString(R.string.summary_registrar, it) }
    record.registrar?.ianaId?.let { lines += getString(R.string.summary_registrar_id, it) }
    record.registrar?.url?.let { lines += getString(R.string.summary_registrar_url, it) }
    record.registrar?.abuseEmail?.let { lines += getString(R.string.summary_abuse_email, it) }
    record.registrar?.abusePhone?.let { lines += getString(R.string.summary_abuse_phone, it) }
    record.handle?.let { lines += getString(R.string.summary_handle, it) }
    record.registeredAt?.let { lines += getString(R.string.summary_registered, DomainDates.format(it)) }
    record.expiresAt?.let { rawDate ->
        val hint = DomainDates.expiryHint(rawDate, now)
        lines += if (hint == null) {
            getString(R.string.summary_expires, DomainDates.format(rawDate))
        } else {
            getString(R.string.summary_expires_hint, DomainDates.format(rawDate), expiryText(hint))
        }
    }
    record.changedAt?.let { lines += getString(R.string.summary_changed, DomainDates.format(it)) }
    record.databaseUpdatedAt?.let { lines += getString(R.string.summary_updated, DomainDates.format(it)) }
    record.dnssec?.let { lines += getString(R.string.summary_dnssec, dnssecText(it)) }
    if (record.nameservers.isEmpty()) {
        lines += getString(R.string.summary_ns_missing)
    } else {
        lines += getString(R.string.summary_ns)
        record.nameservers.forEach { server ->
            val addresses = if (server.addresses.isEmpty()) "" else "  ${server.addresses.joinToString(" ")}"
            lines += getString(R.string.summary_ns_line, server.name, addresses)
        }
    }
    if (record.contacts.isEmpty()) {
        lines += getString(R.string.summary_contacts_private)
    } else {
        record.contacts.forEach { contact ->
            lines += getString(R.string.summary_contact, roleText(contact.role), contactSummary(contact))
        }
    }
    lines += getString(R.string.summary_source, record.sourceLabel())
    return lines.joinToString("\n")
}

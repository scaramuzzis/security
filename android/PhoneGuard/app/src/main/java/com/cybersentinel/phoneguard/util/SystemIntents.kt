package com.cybersentinel.phoneguard.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Deep-link di sistema riusati in tutta l'app (scheda app, permessi,
 * Play Store). Centralizzati qui per evitare di ripetere lo stesso
 * `Intent` + `runCatching` in ogni pagina.
 */
object SystemIntents {

    fun openAppDetails(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                ).addNewTaskFlagIfNeeded(context)
            )
        }
    }

    fun openUsageAccessSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addNewTaskFlagIfNeeded(context))
        }
    }

    /** Permesso "Accesso a tutti i file" (Android 11+), con fallback sulle versioni precedenti. */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            runCatching { context.startActivity(intent.addNewTaskFlagIfNeeded(context)) }.onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addNewTaskFlagIfNeeded(context)
                    )
                }
            }
        }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent.addNewTaskFlagIfNeeded(context)) }
    }

    fun uninstallApp(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addNewTaskFlagIfNeeded(context)
            )
        }
    }

    /** Apre la scheda dell'app sul Play Store, dove parte l'aggiornamento vero. */
    fun openPlayStoreListing(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        runCatching { context.startActivity(market.addNewTaskFlagIfNeeded(context)) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    ).addNewTaskFlagIfNeeded(context)
                )
            }
        }
    }

    /** Apre il tastierino con un codice USSD di sistema pre-digitato (non chiama: l'utente conferma). */
    fun dialUssd(context: Context, code: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(code)}")).addNewTaskFlagIfNeeded(context)
            )
        }
    }

    /** FLAG_ACTIVITY_NEW_TASK è necessario solo se il contesto non è già un'Activity. */
    private fun Intent.addNewTaskFlagIfNeeded(context: Context): Intent {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return this
    }
}

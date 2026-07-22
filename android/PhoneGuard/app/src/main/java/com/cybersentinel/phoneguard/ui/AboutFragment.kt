package com.cybersentinel.phoneguard.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import com.cybersentinel.phoneguard.BuildConfig
import com.cybersentinel.phoneguard.R

/**
 * Pagina "Informazioni app": versione, data di rilascio (mese/anno reali
 * di build, non una stringa da aggiornare a mano) e cosa fa l'app.
 *
 * Versione e codice build sono letti a runtime da [android.content.pm.PackageInfo]
 * — unica fonte di verità è `versionName`/`versionCode` in `build.gradle.kts`,
 * niente da tenere sincronizzato a mano in due posti.
 */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

        view.findViewById<TextView>(R.id.aboutVersion).text = getString(
            R.string.about_version,
            packageInfo.versionName, versionCode, BuildConfig.BUILD_DATE
        )
    }
}

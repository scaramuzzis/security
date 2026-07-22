package com.cybersentinel.phoneguard.ui.base

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.cybersentinel.phoneguard.R

/**
 * Fragment con pull-to-refresh: ogni pagina che carica dati mostra la
 * rotella di caricamento standard di Material sia al primo caricamento
 * sia trascinando la pagina verso il basso col dito. Il layout deve avere
 * una `SwipeRefreshLayout` con id `swipeRefresh` come radice.
 *
 * Ogni sottoclasse resta responsabile di impostare `swipeRefresh.isRefreshing`
 * a `true` quando avvia il proprio caricamento e a `false` (tramite
 * [endRefresh]) quando i dati sono pronti: le pagine hanno logiche di
 * caricamento troppo diverse (liste, testo, grafici) per un unico metodo
 * `refresh()` comune.
 */
abstract class RefreshableFragment(@LayoutRes layoutId: Int) : Fragment(layoutId) {

    protected lateinit var swipeRefresh: SwipeRefreshLayout
        private set

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        swipeRefresh.setColorSchemeResources(R.color.primary)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.cyber_surface_variant)
    }

    /** Da chiamare a fine caricamento, per far sparire la rotella. */
    protected fun endRefresh() {
        if (isAdded) swipeRefresh.isRefreshing = false
    }
}

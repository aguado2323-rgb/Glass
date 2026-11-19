package com.glass.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glass.tv.GlassApp
import com.glass.tv.Nav
import com.glass.tv.Route
import com.glass.tv.data.SeriesItem
import com.glass.tv.data.displayTitle
import com.glass.tv.data.VodStream
import com.glass.tv.ui.Focusable
import com.glass.tv.ui.LocalPalette
import com.glass.tv.ui.Poster
import com.glass.tv.ui.Symbols
import com.glass.tv.ui.T
import com.glass.tv.ui.Type
import com.glass.tv.ui.glass

// Películas y Series al estilo de la app Apple TV: por defecto, una fila por carpeta que se recorre en horizontal
// ("Explorar"); la vista "Carpetas" mantiene la lista de carpetas con la cuadrícula, para ordenar, ocultar o
// bloquear carpetas y para ver una entera.

/** Control segmentado de tvOS bajo el título de la sección. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.glass(RoundedCornerShape(50)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            Focusable(onClick = { onSelect(i) }, onFocused = { onSelect(i) }, shape = RoundedCornerShape(50)) { focused ->
                Box(
                    Modifier
                        .background(if (focused) p.focusBg else if (i == selected) p.text.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    T(label, Type.label.copy(fontWeight = FontWeight.SemiBold), if (focused) p.focusText else if (i == selected) p.text else p.muted)
                }
            }
        }
    }
}

/** Películas o Series: "Explorar" (filas) o "Carpetas" (la vista de siempre). */
@Composable
internal fun CatalogSwitch(app: GlassApp, nav: Nav, type: String, folders: @Composable () -> Unit) {
    var mode by rememberSaveable(type) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Segmented(listOf("Explorar", "Carpetas"), mode) { mode = it }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp)) {
            if (mode == 0) CatalogShelves(app, nav, type) else folders()
        }
    }
}

/** Una fila por carpeta: Novedades, Mi lista y las carpetas del proveedor en el orden elegido. */
@Composable
private fun CatalogShelves(app: GlassApp, nav: Nav, type: String) {
    val vod = type == "vod"
    val cats = load(app.repo.version) { if (vod) app.repo.vodCategories() else app.repo.seriesCategories() }
    LoadView(cats) { list ->
        val folders = arrangedFolders(app, type, list).filterNot { app.folders.isBlocked(type, it.id) }.distinctBy { it.id }
        val favCount = app.favorites.of(type).size
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "new") { Shelf(app, nav, type, "Novedades", NEW_FOLDER) }
            if (favCount > 0) item(key = "fav") { Shelf(app, nav, type, "Mi lista", FAV_FOLDER) }
            items(folders, key = { it.id }) { c -> Shelf(app, nav, type, c.name, c.id) }
        }
    }
}

@Composable
private fun Shelf(app: GlassApp, nav: Nav, type: String, title: String, id: String) {
    val p = LocalPalette.current
    val vod = type == "vod"
    val items = load(id, app.repo.version, app.favorites.items.size) {
        when {
            vod && id == NEW_FOLDER -> app.repo.latestVod(30)
            vod && id == FAV_FOLDER -> app.favorites.of("vod").map { it.toVod() }
            vod -> app.repo.vodStreams(id).sortedByDescending { it.added }.take(30)
            id == NEW_FOLDER -> app.repo.latestSeries(30)
            id == FAV_FOLDER -> app.favorites.of("series").map { it.toSeries() }
            else -> app.repo.series(id).take(30)
        }
    }
    // Algunos proveedores repiten el mismo título en una carpeta: una clave repetida en la fila cierra la app
    val list = items.orNull()?.distinctBy { if (it is VodStream) "v" + it.id else "s" + (it as SeriesItem).id }
    if (list != null && list.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(title, Type.h2.copy(fontSize = 17.sp), p.text)
            Icon(Symbols.Chevron, contentDescription = null, tint = p.muted, modifier = Modifier.padding(start = 2.dp).size(18.dp))
        }
        if (list == null) {
            T(if (items is Load.Err) "No se pudo cargar" else "Cargando…", Type.small, p.muted, Modifier.padding(top = 10.dp).height(40.dp))
        } else LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 10.dp, end = 48.dp, start = 4.dp),
        ) {
            items(list, key = { (if (it is VodStream) "v" + it.id else "s" + (it as SeriesItem).id) }) { it ->
                when (it) {
                    is VodStream -> {
                        val k = "shelf:$id:vod:${it.id}"
                        Poster(
                            url = it.icon, title = displayTitle(it.name), width = 112.dp,
                            subtitle = listOfNotNull(it.year.takeIf { y -> y > 0 }?.toString(), rating(it.rating)).joinToString("  "),
                            onClick = { nav.open(Route.Movie(it), k) },
                            onMenu = { app.favorites.toggle(it.toFav()) },
                            focusRequester = rememberReturnFocus(nav, k),
                        )
                    }
                    is SeriesItem -> {
                        val k = "shelf:$id:series:${it.id}"
                        Poster(
                            url = it.cover, title = displayTitle(it.name), width = 112.dp,
                            subtitle = listOfNotNull(it.year.takeIf { y -> y > 0 }?.toString(), rating(it.rating)).joinToString("  "),
                            onClick = { nav.open(Route.Show(it), k) },
                            onMenu = { app.favorites.toggle(it.toFav()) },
                            focusRequester = rememberReturnFocus(nav, k),
                        )
                    }
                }
            }
        }
    }
}

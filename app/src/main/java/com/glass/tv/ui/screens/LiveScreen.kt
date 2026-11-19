package com.glass.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import com.glass.tv.ui.glass
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.glass.tv.GlassApp
import com.glass.tv.data.Category
import com.glass.tv.data.EpgEntry
import com.glass.tv.data.FavItem
import com.glass.tv.data.LiveStream
import com.glass.tv.data.PlayItem
import com.glass.tv.play
import com.glass.tv.ui.Empty
import com.glass.tv.ui.Img
import com.glass.tv.ui.ListRow
import com.glass.tv.ui.LocalPalette
import com.glass.tv.ui.T
import com.glass.tv.ui.Type
import kotlinx.coroutines.delay

fun LiveStream.toPlay() = PlayItem(kind = "live", id = id, title = name, subtitle = if (num > 0) num.toString() else "", icon = icon, epgId = epgId)
fun LiveStream.toFav() = FavItem(type = "live", id = id, name = name, icon = icon, epgId = epgId, num = num)
fun FavItem.toLive() = LiveStream(id = id, num = num, name = name, icon = icon, categoryId = "", epgId = epgId)

@Composable
fun LiveScreen(app: GlassApp) {
    var mode by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Segmented(listOf("Canales", "Carpetas"), mode) { mode = it }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).padding(top = 14.dp)) {
            if (mode == 0) LiveChannels(app) else LiveFolders(app)
        }
    }
}

@Composable
private fun LiveFolders(app: GlassApp) {
    val cats = load(app.repo.version) { app.repo.liveCategories() }
    var selected by rememberSaveable { mutableStateOf(FAV_FOLDER) }
    var menuFor by remember { mutableStateOf<LiveStream?>(null) }
    var timerFor by remember { mutableStateOf<LiveStream?>(null) }

    val entry = remember { FolderEntry() }
    Row(Modifier.fillMaxSize().enterAtFolders(entry), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        LoadView(cats) { list ->
            FolderColumn(fixedFolders(withNew = false) + arrangedFolders(app, "live", list), selected, { selected = it }, entry = entry, manage = FolderManage("live", list))
        }
        Column(Modifier.weight(1f)) {
            if (selected == SEARCH_FOLDER) {
                SearchPanel("Buscar canal", { val ex = app.folders.excludes("live"); app.repo.allLive().filterNot { ex(it.categoryId) } }, { it.name }) { found ->
                    ChannelList(app, found, onMenu = { menuFor = it })
                }
            } else if (isBlockedFolder(app, "live", selected)) {
                LockedPanel(app, "live", selected)
            } else {
                val streams = load(selected, app.favorites.items.size, app.repo.version, reset = true) {
                    kotlinx.coroutines.delay(FOLDER_DEBOUNCE_MS) // al recorrer carpetas deprisa no se pide cada una por el camino
                    if (selected == FAV_FOLDER) app.favorites.of("live").map { it.toLive() } else app.repo.liveStreams(selected)
                }
                T("OK para ver · ≡ Menú para favoritos y grabar", Type.small, LocalPalette.current.muted, Modifier.padding(start = 12.dp, bottom = 6.dp))
                LoadView(streams) { list ->
                    if (list.isEmpty()) {
                        Empty(if (selected == FAV_FOLDER) "Aún no tienes canales favoritos. Pulsa ≡ sobre un canal para añadirlo." else "Esta carpeta está vacía.")
                    } else {
                        ChannelList(app, list, onMenu = { menuFor = it })
                    }
                }
            }
        }
    }

    menuFor?.let { s ->
        ChannelActions(app, s, onTimer = { timerFor = s }, onDismiss = { menuFor = null })
    }
    timerFor?.let { s ->
        NewTimerDialog(app, onDismiss = { timerFor = null }, presetChannel = s)
    }
}

@Composable
fun ChannelList(app: GlassApp, list: List<LiveStream>, onMenu: (LiveStream) -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            val now by produceState<EpgEntry?>(null, s.id) {
                delay(350) // al desplazarse rápido no se piden guías que no se van a ver
                value = app.repo.shortEpg(s.id, 3).firstOrNull { it.isNow() }
            }
            val rec = app.recordings.activeFor(s.id) != null
            ListRow(
                title = (if (s.num > 0) "${s.num}  " else "") + s.name,
                subtitle = now?.let { "${it.title} · hasta ${hm(it.end)}" },
                trailing = if (app.favorites.contains("live", s.id)) "★" else null,
                dot = if (rec) LocalPalette.current.rec else null,
                leading = { Img(s.icon, Modifier.size(width = 44.dp, height = 30.dp).clip(RoundedCornerShape(6.dp)), crop = false) },
                onClick = { play(ctx, list.map { it.toPlay() }, i) },
                onMenu = { onMenu(s) },
            )
        }
    }
}

@Composable
fun ChannelActions(app: GlassApp, s: LiveStream, onTimer: () -> Unit, onDismiss: () -> Unit) {
    val isFav = app.favorites.contains("live", s.id)
    val active = app.recordings.activeFor(s.id)
    val actions = buildList<Pair<String, () -> Unit>> {
        add((if (isFav) "Quitar de favoritos" else "Añadir a favoritos") to { app.favorites.toggle(s.toFav()); Unit })
        if (active == null) {
            add("Grabar ahora (2 horas)" to { startManualRecording(app, s.id, s.name, null) })
        } else {
            add("Detener grabación" to { app.recordings.cancel(active.id) })
        }
        add("Programar grabación…" to onTimer)
    }
    ActionsDialog(s.name, actions, onDismiss)
}

/** Grabación inmediata. Si hay EPG, termina con el programa actual (más el margen). */
fun startManualRecording(app: GlassApp, streamId: Int, channel: String, epgNow: EpgEntry?) {
    val now = System.currentTimeMillis()
    val end = epgNow?.end?.plus(app.settings.marginAfter * 60_000L) ?: (now + 2 * 60 * 60_000L)
    app.recordings.schedule(
        com.glass.tv.record.Recording(
            streamId = streamId, channel = channel, title = epgNow?.title ?: channel,
            start = now, end = end, source = "manual",
        )
    )
}


// ---------------- Canales al estilo de la app Apple TV ----------------

/**
 * Carpetas en botones arriba; debajo, a la izquierda, los canales con lo que se emite y cuánto lleva;
 * a la derecha, la ficha del canal enfocado (ahora y después).
 */
@Composable
private fun LiveChannels(app: GlassApp) {
    val p = LocalPalette.current
    val cats = load(app.repo.version) { app.repo.liveCategories() }
    var selected by rememberSaveable { mutableStateOf(FAV_FOLDER) }
    var focused by remember { mutableStateOf<LiveStream?>(null) }
    var menuFor by remember { mutableStateOf<LiveStream?>(null) }
    var timerFor by remember { mutableStateOf<LiveStream?>(null) }
    Column(Modifier.fillMaxSize()) {
        LoadView(cats) { list ->
            val chips = listOf(Category(FAV_FOLDER, "Favoritos"), Category(SEARCH_FOLDER, "Buscar")) + arrangedFolders(app, "live", list)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 48.dp, bottom = 12.dp),
            ) {
                items(chips.size, key = { chips[it].id }) { i ->
                    val c = chips[i]
                    Chip(if (c.id == FAV_FOLDER || c.id == SEARCH_FOLDER) c.name else folderLabel(app, "live", c), c.id == selected) { selected = c.id }
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1.3f)) {
                when {
                    selected == SEARCH_FOLDER -> SearchPanel("Buscar canal", { val ex = app.folders.excludes("live"); app.repo.allLive().filterNot { ex(it.categoryId) } }, { it.name }) { found ->
                        ChannelRows(app, found, onFocus = { focused = it }, onMenu = { menuFor = it })
                    }
                    isBlockedFolder(app, "live", selected) -> LockedPanel(app, "live", selected)
                    else -> {
                        val streams = load(selected, app.favorites.items.size, app.repo.version, reset = true) {
                            kotlinx.coroutines.delay(FOLDER_DEBOUNCE_MS)
                            if (selected == FAV_FOLDER) app.favorites.of("live").map { it.toLive() } else app.repo.liveStreams(selected)
                        }
                        LoadView(streams) { list ->
                            if (list.isEmpty()) Empty(if (selected == FAV_FOLDER) "Aún no tienes canales favoritos. Elige una carpeta y pulsa ≡ sobre un canal para añadirlo." else "Esta carpeta está vacía.")
                            else ChannelRows(app, list, onFocus = { focused = it }, onMenu = { menuFor = it })
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) { focused?.let { ChannelInfo(app, it) } }
        }
    }
    menuFor?.let { s -> ChannelActions(app, s, onTimer = { timerFor = s }, onDismiss = { menuFor = null }) }
    timerFor?.let { s -> NewTimerDialog(app, onDismiss = { timerFor = null }, presetChannel = s) }
}

/** Botón de carpeta (cápsula): enfocado en blanco, elegido en cristal más claro. */
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    com.glass.tv.ui.Focusable(onClick = onClick, shape = RoundedCornerShape(50), liftOnFocus = 1.06f) { f ->
        androidx.compose.foundation.layout.Box(
            Modifier
                .then(if (f) Modifier.background(p.focusBg) else Modifier.background(p.text.copy(alpha = if (selected) 0.22f else 0.08f)))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            T(text, Type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), if (f) p.focusText else if (selected) p.text else p.text.copy(alpha = 0.82f))
        }
    }
}

@Composable
private fun ChannelRows(app: GlassApp, list: List<LiveStream>, onFocus: (LiveStream) -> Unit, onMenu: (LiveStream) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            val now by produceState<EpgEntry?>(null, s.id) {
                delay(350) // al desplazarse rápido no se piden guías que no se van a ver
                value = app.repo.shortEpg(s.id, 3).firstOrNull { it.isNow() }
            }
            val rec = app.recordings.activeFor(s.id) != null
            com.glass.tv.ui.Focusable(
                onClick = { play(ctx, list.map { it.toPlay() }, i) },
                onMenu = { onMenu(s) },
                onFocused = { onFocus(s) },
                shape = RoundedCornerShape(14.dp),
                liftOnFocus = 1.02f,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) { f ->
                val fg = if (f) p.focusText else p.text
                val sub = if (f) p.focusText.copy(alpha = 0.6f) else p.muted
                Row(
                    Modifier.fillMaxWidth().then(if (f) Modifier.background(p.focusBg) else Modifier).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(width = 84.dp, height = 48.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { Img(s.icon, Modifier.size(width = 70.dp, height = 38.dp), crop = false) }
                    T(if (s.num > 0) s.num.toString() else "", Type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), sub, Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        T(now?.title?.ifBlank { null } ?: s.name, Type.label.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), fg)
                        T(listOfNotNull(s.name.takeIf { now != null }, now?.let { "${hm(it.start)} – ${hm(it.end)}" }).joinToString(" · ").ifBlank { " " }, Type.small, sub, Modifier.padding(top = 2.dp))
                        now?.let { e ->
                            val frac = ((System.currentTimeMillis() - e.start).toFloat() / (e.end - e.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                            androidx.compose.foundation.layout.Box(Modifier.padding(top = 6.dp).width(160.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = 0.18f))) {
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(frac).height(3.dp).background(fg))
                            }
                        }
                    }
                    if (rec) androidx.compose.foundation.layout.Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.rec))
                    if (app.favorites.contains("live", s.id)) T("★", Type.small, sub)
                }
            }
        }
    }
}

/** Ficha del canal enfocado: logotipo, lo que se emite ahora y lo siguiente. */
@Composable
private fun ChannelInfo(app: GlassApp, s: LiveStream) {
    val p = LocalPalette.current
    val epg by produceState<List<EpgEntry>>(emptyList(), s.id) {
        delay(250)
        value = try { app.repo.shortEpg(s.id, 4) } catch (e: Exception) { emptyList() }
    }
    val now = epg.firstOrNull { it.isNow() }
    val next = epg.firstOrNull { now != null && it.start >= now.end }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).glass(RoundedCornerShape(16.dp)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Img(s.icon, Modifier.fillMaxSize(0.5f), crop = false) }
    T("${s.name.uppercase()} · AHORA", Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 1.5.sp), p.muted, Modifier.padding(top = 16.dp))
    T(now?.title ?: "Sin guía para este canal", Type.heading, p.text, Modifier.padding(top = 4.dp), maxLines = 2)
    now?.desc?.takeIf { it.isNotBlank() }?.let { T(it, Type.body, p.text.copy(alpha = 0.8f), Modifier.padding(top = 6.dp), maxLines = 4) }
    next?.let { T("Después · ${hm(it.start)} ${it.title}", Type.small, p.muted, Modifier.padding(top = 12.dp)) }
    T("OK para ver · ≡ favoritos y grabar", Type.small, p.muted, Modifier.padding(top = 12.dp))
}

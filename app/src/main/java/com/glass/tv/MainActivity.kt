package com.glass.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import com.glass.tv.ui.glassPanel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.horizontalScroll
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import com.glass.tv.data.PlayItem
import com.glass.tv.data.SeriesItem
import com.glass.tv.data.VodStream
import com.glass.tv.player.PlayerActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.glass.tv.player.MiniPlayer
import com.glass.tv.player.MiniPlayerWindow
import com.glass.tv.ui.TvButton
import com.glass.tv.ui.glass
import com.glass.tv.ui.Focusable
import com.glass.tv.ui.LocalPalette
import com.glass.tv.ui.T
import com.glass.tv.ui.Type
import com.glass.tv.ui.GlassTheme
import com.glass.tv.ui.screens.FavoritesScreen
import com.glass.tv.ui.screens.GuideScreen
import com.glass.tv.ui.screens.HomeScreen
import com.glass.tv.ui.screens.LiveScreen
import com.glass.tv.ui.screens.LoginScreen
import com.glass.tv.ui.screens.MovieDetail
import com.glass.tv.ui.screens.MoviesScreen
import com.glass.tv.ui.screens.PinPad
import com.glass.tv.ui.screens.RecordingsScreen
import com.glass.tv.ui.screens.SeriesDetail
import com.glass.tv.ui.screens.SeriesScreen
import com.glass.tv.ui.screens.SettingsScreen
import com.glass.tv.ui.screens.UpdateDialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab(val label: String) {
    HOME("Inicio"), DISCOVER("Descubrir"), GUIDE("Guía"), LIVE("TV en directo"), MOVIES("Películas"), SERIES("Series"),
    FAVS("Mi lista"), REC("Grabaciones"), SETTINGS("Ajustes");

    /** Símbolo del menú lateral. */
    val icon: androidx.compose.ui.graphics.vector.ImageVector get() = when (this) {
        HOME -> com.glass.tv.ui.Symbols.Home
        DISCOVER -> com.glass.tv.ui.Symbols.Explore
        GUIDE -> com.glass.tv.ui.Symbols.Guide
        LIVE -> com.glass.tv.ui.Symbols.Live
        MOVIES -> com.glass.tv.ui.Symbols.Movie
        SERIES -> com.glass.tv.ui.Symbols.Tv
        FAVS -> com.glass.tv.ui.Symbols.Bookmark
        REC -> com.glass.tv.ui.Symbols.Record
        SETTINGS -> com.glass.tv.ui.Symbols.Gear
    }
}

sealed interface Route {
    data object Tabs : Route
    data class Movie(val vod: VodStream) : Route
    data class Show(val series: SeriesItem) : Route
    /** Ficha de un título de Descubrir (addons). */
    data class Addon(val meta: com.glass.tv.addons.MetaPreview) : Route
    /** Addons, cuentas debrid y preferencias de fuentes. */
    data object DiscoverSetup : Route
}

/** Navegación mínima: una pila de pantallas y la pestaña activa. */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Tabs)
    var tab by mutableStateOf(Tab.HOME)
    var settingsUnlocked by mutableStateOf(false)
    /** Elemento desde el que se abrió la ficha que se acaba de cerrar; al volver, recupera el foco. */
    var returnKey by mutableStateOf<String?>(null)
    /** Una clave por pantalla abierta, para volver al elemento exacto aunque se encadenen fichas. */
    private val keys = mutableListOf<String?>()
    fun push(r: Route) { keys.add(null); returnKey = null; stack.add(r) }
    fun open(r: Route, fromKey: String) { keys.add(fromKey); returnKey = null; stack.add(r) }
    fun pop(): Boolean = if (stack.size > 1) {
        returnKey = keys.removeLastOrNull()
        stack.removeAt(stack.lastIndex); true
    } else false
}

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as GlassApp).internalNav = false
    }

    override fun onStop() {
        super.onStop()
        // Al salir de la app (botón Inicio) o al abrir otro vídeo, la ventana se cierra
        MiniPlayer.stop()
        // Al salir de la app (no al abrir el reproductor), las carpetas bloqueadas se vuelven a cerrar
        val app = application as GlassApp
        if (!app.internalNav && !isChangingConfigurations) app.folders.relockAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val app = application as GlassApp
        setContent {
            GlassTheme(app.settings.theme) {
                Root(app) { finish() }
            }
        }
    }
}

fun play(ctx: Context, list: List<PlayItem>, index: Int) {
    if (list.isEmpty()) return
    GlassApp.instance.queue.set(list, index)
    GlassApp.instance.internalNav = true
    ctx.startActivity(Intent(ctx, PlayerActivity::class.java))
}

@Composable
private fun Root(app: GlassApp, onExit: () -> Unit) {
    val p = LocalPalette.current
    val nav = remember { Nav() }
    // Guarda el estado de las pestañas (carpeta elegida, posición en la cuadrícula…) mientras se ve una ficha
    val tabsState = rememberSaveableStateHolder()
    var unlocked by remember { mutableStateOf(!(app.settings.pinOnStart && app.vault.hasPin())) }

    // Al abrir la app, mira si hay una versión nueva publicada en GitHub
    LaunchedEffect(app.repo.account) { if (app.repo.account != null) app.updater.check() }

    // Actualización automática de la lista: al abrir la app y luego cada hora comprueba si toca.
    LaunchedEffect(app.repo.account, app.settings.refreshEvery) {
        if (app.repo.account == null) return@LaunchedEffect
        while (true) {
            val every = app.settings.refreshEvery.hours * 3_600_000L
            if (every > 0 && System.currentTimeMillis() - app.repo.lastRefresh >= every) app.repo.refreshAll()
            delay(60 * 60_000L)
        }
    }

    // Descubrir: los catálogos de los addons se empiezan a pedir al abrir la app, para que la pestaña salga ya llena
    LaunchedEffect(app.repo.account) {
        if (app.repo.account == null) return@LaunchedEffect
        delay(2_000)
        app.discover.prefetch()
    }

    // Guía completa (XMLTV): se descarga al abrir la app si tiene más de 12 horas, y luego cada hora se comprueba
    LaunchedEffect(app.repo.account) {
        if (app.repo.account == null) return@LaunchedEffect
        delay(5_000) // deja arrancar la interfaz primero
        while (true) {
            app.epg.refreshIfOld(app.repo, 12)
            delay(60 * 60_000L)
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            // Con un canal en ventana, Play/Pausa del mando lo vuelve a poner a pantalla completa
            .onPreviewKeyEvent { e ->
                if (MiniPlayer.active && e.type == KeyEventType.KeyUp &&
                    (e.key == Key.MediaPlayPause || e.key == Key.MediaPlay)
                ) { MiniPlayer.expand(ctx); true } else false
            }
    ) {
        // Fondo ambiental de tvOS: da color y profundidad al cristal de encima
        com.glass.tv.ui.AmbientBackground(Modifier.fillMaxSize())
        when {
            app.repo.account == null -> LoginScreen(app)
            !unlocked -> PinPad(title = "Introduce el PIN", onDone = { pin ->
                if (app.vault.checkPin(pin)) { unlocked = true; true } else false
            })
            else -> {
                BackHandler {
                    if (!nav.pop()) {
                        if (nav.tab != Tab.HOME) nav.tab = Tab.HOME else onExit()
                    }
                }
                when (val r = nav.stack.last()) {
                    Route.Tabs -> tabsState.SaveableStateProvider("tabs") { TabsScaffold(app, nav) }
                    // key: cada ficha empieza de cero aunque se abra una desde otra
                    is Route.Movie -> androidx.compose.runtime.key(r, nav.stack.size) { MovieDetail(app, nav, r.vod) }
                    is Route.Show -> androidx.compose.runtime.key(r, nav.stack.size) { SeriesDetail(app, r.series) }
                    is Route.Addon -> androidx.compose.runtime.key(r, nav.stack.size) { com.glass.tv.ui.screens.AddonDetail(app, nav, r.meta) }
                    Route.DiscoverSetup -> com.glass.tv.ui.screens.DiscoverSetupScreen(app, nav)
                }
                if (app.updater.prompt) UpdateDialog(app)
                MiniPlayerWindow(Modifier.align(Alignment.BottomEnd).padding(24.dp))
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun TabsScaffold(app: GlassApp, nav: Nav) {
    val p = LocalPalette.current
    // Menú lateral de tvOS 18: oculto mientras ves contenido; se abre con ← desde el borde izquierdo o con Atrás.
    var menuOpen by remember { mutableStateOf(false) }
    val activeItem = remember { FocusRequester() }
    val content = remember { FocusRequester() }
    val now by produceState(initialValue = clock()) {
        while (true) { delay(20_000); value = clock() }
    }
    // La sección enfocada en el menú se abre sola tras un instante (al recorrerlo deprisa no se carga cada una)
    var focusedTab by remember { mutableStateOf<Tab?>(null) }
    LaunchedEffect(focusedTab) {
        val t = focusedTab ?: return@LaunchedEffect
        if (t != nav.tab) { delay(TAB_SWITCH_DELAY_MS); nav.tab = t }
    }
    fun closeMenu() {
        focusedTab?.let { nav.tab = it }
        menuOpen = false
    }
    // Al cerrar el menú (o al volver de una ficha) el foco entra en el contenido
    LaunchedEffect(menuOpen) {
        if (menuOpen) { delay(30); try { activeItem.requestFocus() } catch (_: Exception) { } }
        else if (nav.returnKey == null) {
            delay(80)
            try { content.requestFocus() } catch (_: Exception) { }
        }
    }
    LaunchedEffect(Unit) {
        if (nav.returnKey != null) {
            delay(1500) // si el elemento ya no está (lista actualizada…), el foco va al contenido
            if (nav.returnKey == null) return@LaunchedEffect
            nav.returnKey = null
            try { content.requestFocus() } catch (_: Exception) { }
        }
    }
    // Atrás desde el contenido abre el menú; con el menú abierto, el Atrás general (Inicio y luego salir)
    BackHandler(enabled = !menuOpen) { menuOpen = true }
    BackHandler(enabled = menuOpen && nav.tab != Tab.HOME) { focusedTab = Tab.HOME; nav.tab = Tab.HOME }

    val home = nav.tab == Tab.HOME
    val guide = nav.tab == Tab.GUIDE
    val discover = nav.tab == Tab.DISCOVER
    // Inicio, Guía y Descubrir: pantalla completa y siempre oscuras
    val fullBleed = home || guide || discover
    val homeFocus = remember { com.glass.tv.ui.screens.HomeFocus(activeItem) }
    val shift by androidx.compose.animation.core.animateDpAsState(if (menuOpen) MENU_W - 24.dp else 0.dp, label = "menu")
    val dim by androidx.compose.animation.core.animateFloatAsState(if (menuOpen) 0.5f else 0f, label = "oscurecer")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .offset(x = shift)
                .focusRequester(content)
                .focusRestorer()
                .focusGroup()
        ) {
            Box(Modifier.fillMaxSize().then(when { guide -> Modifier.padding(top = 28.dp); fullBleed -> Modifier; else -> Modifier.padding(start = 64.dp, end = 48.dp, top = 28.dp) })) {
                if (!fullBleed) Column(Modifier.fillMaxSize()) {
                    T(nav.tab.label, Type.title.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = (-0.8).sp), p.text)
                    T(tabHint(nav.tab), Type.small, p.muted, Modifier.padding(top = 4.dp, bottom = 16.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) { TabContent(app, nav, homeFocus) }
                } else TabContent(app, nav, homeFocus)
            }
            if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }

        // Borde izquierdo invisible (fuera del contenido, para que al volver no se restaure aquí el foco):
        // al pulsar ← en lo primero de una fila, el foco llega aquí y abre el menú
        if (!menuOpen) Box(
            Modifier.align(Alignment.CenterStart).width(2.dp).fillMaxHeight()
                .onFocusChanged { if (it.isFocused) menuOpen = true }
                .focusable()
        )

        // Ventana de un canal: controles arriba a la derecha
        MiniPlayer.item?.let { m ->
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 32.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(m.title, onClick = { MiniPlayer.expand(ctx) }, icon = androidx.compose.material.icons.Icons.Filled.PlayArrow)
                TvButton("", onClick = { MiniPlayer.stop() }, icon = androidx.compose.material.icons.Icons.Filled.Close, description = "Cerrar la ventana")
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = menuOpen,
            enter = androidx.compose.animation.slideInHorizontally { -it / 3 } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally { -it / 3 } + androidx.compose.animation.fadeOut(),
        ) {
            CompositionLocalProvider(LocalPalette provides com.glass.tv.ui.OledPalette) {
                SideMenu(
                    nav = nav, now = now, activeItem = activeItem,
                    onFocusTab = { focusedTab = it },
                    onPick = { t -> focusedTab = t; nav.tab = t; menuOpen = false },
                    onClose = { closeMenu() },
                )
            }
        }
    }
}

/** Panel de cristal del menú lateral, con las secciones en el orden de siempre y Ajustes abajo. */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun SideMenu(nav: Nav, now: String, activeItem: FocusRequester, onFocusTab: (Tab) -> Unit, onPick: (Tab) -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .padding(16.dp)
            .width(MENU_W)
            .fillMaxHeight()
            .glassPanel(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp, vertical = 18.dp)
            // → cierra el menú y vuelve al contenido; el foco no sale del panel por arriba ni por abajo
            .onPreviewKeyEvent { e ->
                if (e.key == Key.DirectionRight) { if (e.type == KeyEventType.KeyDown) onClose(); true } else false
            }
            .focusProperties { enter = { activeItem } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Glass", Type.heading, p.text, Modifier.weight(1f))
            T(now, Type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), p.muted)
        }
        val main = Tab.entries.filter { it != Tab.SETTINGS }
        main.forEach { t -> MenuItem(t, nav.tab == t, activeItem.takeIf { nav.tab == t }, onFocusTab, onPick) }
        Spacer(Modifier.weight(1f))
        MenuItem(Tab.SETTINGS, nav.tab == Tab.SETTINGS, activeItem.takeIf { nav.tab == Tab.SETTINGS }, onFocusTab, onPick)
    }
}

@Composable
private fun MenuItem(t: Tab, active: Boolean, fr: FocusRequester?, onFocusTab: (Tab) -> Unit, onPick: (Tab) -> Unit) {
    val p = LocalPalette.current
    Focusable(
        onClick = { onPick(t) },
        onFocused = { onFocusTab(t) },
        focusRequester = fr,
        liftOnFocus = 1.04f,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { focused ->
        val fg = if (focused) p.focusText else if (active) p.text else p.text.copy(alpha = 0.8f)
        Row(
            Modifier.fillMaxWidth()
                .background(if (focused) p.focusBg else if (active) p.text.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.material3.Icon(t.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            T(t.label, Type.label.copy(fontSize = 15.sp, fontWeight = if (active || focused) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium), fg)
        }
    }
}

@Composable
private fun TabContent(app: GlassApp, nav: Nav, homeFocus: com.glass.tv.ui.screens.HomeFocus) {
    when (nav.tab) {
        Tab.HOME -> HomeScreen(app, nav, homeFocus)
        Tab.LIVE -> LiveScreen(app)
        Tab.GUIDE -> GuideScreen(app)
        Tab.MOVIES -> MoviesScreen(app, nav)
        Tab.SERIES -> SeriesScreen(app, nav)
        Tab.DISCOVER -> com.glass.tv.ui.screens.DiscoverScreen(app, nav)
        Tab.FAVS -> FavoritesScreen(app, nav)
        Tab.REC -> RecordingsScreen(app)
        Tab.SETTINGS -> SettingsScreen(app, nav)
    }
}

/** Frase bajo el título de cada pestaña. */
private fun tabHint(t: Tab): String = when (t) {
    Tab.LIVE -> "Los canales de tu lista, por carpetas, con lo que se emite ahora"
    Tab.MOVIES -> "Las películas de tu proveedor. ≡ sobre una carátula la añade a favoritos"
    Tab.SERIES -> "Las series de tu proveedor. ≡ sobre una carátula la añade a favoritos"
    Tab.FAVS -> "Canales, películas, series y títulos de Descubrir que has guardado"
    Tab.REC -> "Grabaciones programadas, en curso y terminadas"
    Tab.SETTINGS -> "Aspecto, reproducción, cuenta, Descubrir y copia de seguridad"
    else -> ""
}

/** Espera antes de abrir la sección enfocada en el menú. */
private const val TAB_SWITCH_DELAY_MS = 220L

/** Ancho del menú lateral. */
private val MENU_W = 248.dp

private fun clock(): String = SimpleDateFormat("HH:mm", Locale("es")).format(Date())

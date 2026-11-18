package com.glass.tv.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Elemento enfocable con el mando: OK pulsa, el botón Menú (≡) abre acciones.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    focusRequester: FocusRequester? = null,
    scaleOnFocus: Boolean = false,
    /** Elevación ligera de botones y pastillas (1 = ninguna). */
    liftOnFocus: Float = 1f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val lift by animateFloatAsState(
        if (focused) (if (scaleOnFocus) 1.09f else liftOnFocus) else 1f,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "foco",
    )
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { e ->
                if (onMenu != null && e.key == Key.Menu) {
                    if (e.type == KeyEventType.KeyUp) onMenu()
                    true
                } else false
            }
            // Efecto de foco de tvOS: lo enfocado "se levanta" hacia ti con un muelle suave y proyecta sombra
            .graphicsLayer {
                scaleX = lift; scaleY = lift
            }
            .then(if (scaleOnFocus && focused) Modifier.shadow(22.dp, shape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
            .clip(shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        content = { content(focused) },
    )
}

@Composable
fun T(text: String, style: TextStyle, color: Color = LocalPalette.current.text, modifier: Modifier = Modifier, maxLines: Int = 1, minLines: Int = 1) {
    Text(text = text, style = withSystemFont(style), color = color, modifier = modifier, maxLines = maxLines, minLines = minLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dot: Color? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    trailing: String? = null,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    // Botón de tvOS: cápsula de cristal; con el foco se vuelve blanca y se eleva
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        focusRequester = focusRequester,
        onFocused = onFocused,
        liftOnFocus = 1.06f,
    ) { focused ->
        val fg = if (focused) p.focusText else p.text
        Row(
            modifier = Modifier
                .then(
                    when {
                        focused -> Modifier.background(p.focusBg)
                        selected -> Modifier.glass(shape, p.text.copy(alpha = if (p.dark) 0.26f else 0.14f))
                        else -> Modifier.glass(shape)
                    }
                )
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            T(text, Type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), fg)
            if (trailing != null) {
                Spacer(Modifier.weight(1f, fill = false))
                T(trailing, Type.small, fg.copy(alpha = 0.7f))
            }
        }
    }
}

/** Fila de lista (carpetas, canales, ajustes), como las listas agrupadas de tvOS. */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    dot: Color? = null,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        onMenu = onMenu,
        onFocused = onFocused,
        focusRequester = focusRequester,
        shape = shape,
        liftOnFocus = 1.015f,
    ) { focused ->
        val fg = if (focused) p.focusText else p.text
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    when {
                        focused -> Modifier.background(p.focusBg)
                        selected -> Modifier.glass(shape)
                        else -> Modifier
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Column(Modifier.weight(1f)) {
                T(title, Type.label, fg)
                if (!subtitle.isNullOrBlank()) T(subtitle, Type.small, if (focused) fg.copy(alpha = 0.6f) else p.muted)
            }
            if (trailing != null) T(trailing, Type.small, if (focused) fg.copy(alpha = 0.6f) else p.muted)
        }
    }
}

@Composable
fun Img(url: String, modifier: Modifier = Modifier, crop: Boolean = true) {
    val p = LocalPalette.current
    Box(modifier.background(p.placeholder)) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Carátula al estilo de la app Apple TV: esquinas redondeadas, sin marco. Con el foco se eleva,
 * proyecta sombra y un reflejo de luz cruza el cristal; el título se ilumina debajo.
 */
@Composable
fun Poster(
    url: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    subtitle: String? = null,
    progress: Float? = null,
    onMenu: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    wide: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Focusable(
            onClick = onClick, onMenu = onMenu,
            onFocused = onFocused, shape = shape, scaleOnFocus = true, focusRequester = focusRequester,
            modifier = Modifier.onFocusChanged { focused = it.isFocused },
        ) { f ->
            Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 2f / 3f)) {
                Img(url, Modifier.fillMaxSize().clip(shape))
                if (f) GlassSheen(shape)
                if (progress != null) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().height(4.dp)
                            .clip(CircleShape).background(Color.White.copy(alpha = 0.28f))
                    ) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
        }
        T(title, Type.label, if (focused) p.text else p.text.copy(alpha = 0.72f))
        if (subtitle != null) T(subtitle, Type.small, p.muted)
    }
}

/** Reflejo especular del foco: luz diagonal y borde brillante, como el cristal de tvOS. */
@Composable
fun BoxScope.GlassSheen(shape: Shape) {
    Box(
        Modifier.matchParentSize().clip(shape)
            .background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.22f), 0.35f to Color.White.copy(alpha = 0.04f), 0.6f to Color.Transparent))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))), shape)
    )
}

/**
 * Material Liquid Glass: relleno translúcido, un brillo suave arriba y un borde especular que capta la luz.
 * Sobre una imagen o el fondo ambiental deja ver lo que hay detrás.
 */
fun Modifier.glass(shape: Shape, fill: Color? = null): Modifier = composed {
    val p = LocalPalette.current
    this
        .clip(shape)
        .background(fill ?: p.glass)
        .background(Brush.verticalGradient(0f to Color.White.copy(alpha = if (p.dark) 0.09f else 0.40f), 0.55f to Color.Transparent))
        .border(
            1.dp,
            Brush.verticalGradient(listOf(p.glassEdge, p.glassEdge.copy(alpha = 0.04f), p.glassEdge.copy(alpha = 0.22f))),
            shape,
        )
}

/** Cristal grueso para diálogos y menús: más opaco para que el texto se lea, con sombra. */
fun Modifier.glassPanel(shape: Shape = RoundedCornerShape(28.dp)): Modifier = composed {
    val p = LocalPalette.current
    this.shadow(30.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).glass(shape, p.glassThick)
}

/** Fondo ambiental: un degradado profundo y suave que da color al cristal de encima. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .background(p.bg)
            .background(Brush.radialGradient(listOf(p.ambientA, Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 1600f))
            .background(Brush.radialGradient(listOf(p.ambientB, Color.Transparent), center = androidx.compose.ui.geometry.Offset(2200f, 1300f), radius = 1500f))
    )
}

@Composable
fun Empty(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.CenterStart) {
        T(text, Type.body, LocalPalette.current.muted, maxLines = 3)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        T(text, Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), fg)
    }
}

package app.stemma.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stemma.data.Settings
import app.stemma.data.SettingsStore
import app.stemma.data.ThemeChoice
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Text size, theme and language-expansion controls, shown on entries and in Settings. */
@Composable
fun AppearanceControls(settings: Settings, store: SettingsStore, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Text size", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            FilledTonalIconButton(onClick = { scope.launch { store.setTextZoom(settings.textZoom - 10) } }) {
                Icon(Icons.Filled.Remove, contentDescription = "Smaller text")
            }
            Slider(
                value = settings.textZoom.toFloat(),
                onValueChange = { scope.launch { store.setTextZoom((it / 10f).roundToInt() * 10) } },
                valueRange = SettingsStore.MIN_ZOOM.toFloat()..SettingsStore.MAX_ZOOM.toFloat(),
                steps = (SettingsStore.MAX_ZOOM - SettingsStore.MIN_ZOOM) / 10 - 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            FilledTonalIconButton(onClick = { scope.launch { store.setTextZoom(settings.textZoom + 10) } }) {
                Icon(Icons.Filled.Add, contentDescription = "Larger text")
            }
        }
        Text(
            "${settings.textZoom}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Text("Theme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ThemeSwatch("Auto", null, settings.theme == ThemeChoice.SYSTEM) { scope.launch { store.setTheme(ThemeChoice.SYSTEM) } }
            ThemeSwatch("Light", Color(0xFFFFFFFF) to Color(0xFF202122), settings.theme == ThemeChoice.LIGHT) { scope.launch { store.setTheme(ThemeChoice.LIGHT) } }
            ThemeSwatch("Sepia", Color(0xFFF8F1E3) to Color(0xFF222222), settings.theme == ThemeChoice.SEPIA) { scope.launch { store.setTheme(ThemeChoice.SEPIA) } }
            ThemeSwatch("Dark", Color(0xFF1F2023) to Color(0xFFEAECF0), settings.theme == ThemeChoice.DARK) { scope.launch { store.setTheme(ThemeChoice.DARK) } }
            ThemeSwatch("Black", Color(0xFF000000) to Color(0xFFEAECF0), settings.theme == ThemeChoice.BLACK) { scope.launch { store.setTheme(ThemeChoice.BLACK) } }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Expand all languages", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Otherwise only ${settings.preferredLanguage} (or the first language) starts open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.expandAllLanguages,
                onCheckedChange = { scope.launch { store.setExpandAllLanguages(it) } },
            )
        }
    }
}

/** A round "Aa" sample of a theme. `colors` is (background, text); null means "follow the system". */
@Composable
private fun ThemeSwatch(label: String, colors: Pair<Color, Color>?, selected: Boolean, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "$label theme"
            }
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (colors != null) Modifier.background(colors.first)
                    else Modifier.background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            0.5f to Color.White, 0.5f to Color(0xFF1F2023),
                        ),
                    ),
                )
                .border(BorderStroke(if (selected) 2.5.dp else 1.dp, outline), CircleShape),
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors?.second ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("Aa", fontFamily = FontFamily.Serif, fontSize = 17.sp, color = colors?.second ?: Color(0xFF72777D))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

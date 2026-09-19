package dev.openscales.debug

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openscales.OpenScalesApp
import dev.openscales.ble.BleJournal
import dev.openscales.ui.theme.OpenScalesTheme

/** Журнал BLE-обмена (только debug): просмотр, «Поделиться», «Очистить». */
class JournalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as OpenScalesApp
        val journal = app.bleJournal ?: return finish()
        setContent {
            OpenScalesTheme {
                val entries by journal.entries.collectAsStateWithLifecycle()
                val listState = rememberLazyListState()
                LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Журнал BLE") },
                            subtitle = { Text("${entries.size} / ${BleJournal.DEFAULT_CAPACITY}") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                            },
                            actions = {
                                IconButton(onClick = { share(app, journal) }) { Icon(Icons.Rounded.Share, "Поделиться") }
                                IconButton(onClick = journal::clear) { Icon(Icons.Rounded.DeleteSweep, "Очистить") }
                            },
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding(),
                        ),
                    ) {
                        items(entries) { e ->
                            Text(
                                BleJournal.format(e),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = when (e.kind) {
                                    BleJournal.Kind.ERROR -> MaterialTheme.colorScheme.error
                                    BleJournal.Kind.TX -> MaterialTheme.colorScheme.primary
                                    BleJournal.Kind.PHASE, BleJournal.Kind.BOND -> MaterialTheme.colorScheme.tertiary
                                    else -> Color.Unspecified
                                },
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun share(app: OpenScalesApp, journal: BleJournal) {
        val scale = app.repository.state.value
        val header = buildString {
            appendLine("Open Scales — журнал BLE")
            appendLine("Телефон: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Весы: ${scale.name ?: "—"}, ${scale.model.displayName}, ${scale.address ?: "—"}")
            append("Прошивка весов: ${scale.settings.firmware ?: "не прочитана"}")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Open Scales BLE log")
            putExtra(Intent.EXTRA_TEXT, journal.export(header))
        }
        startActivity(Intent.createChooser(send, "Поделиться журналом"))
    }
}

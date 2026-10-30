package com.anindra.messages.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anindra.messages.R

/**
 * Every destination the About screen can reach, in one place.
 *
 * The URLs and the issue prefill are built here rather than inline in the
 * composable so they can be asserted off-device: a wrong URL is invisible in a
 * diff but fails the unit test.
 */
internal object AboutLinks {
    const val REPO_URL = "https://github.com/an1ndra/Messages"
    /** Where the signed APKs live, for anyone who does not use a store. */
    const val RELEASE_URL = "https://github.com/an1ndra/Messages/releases/latest"
    const val FDROID_URL = "https://f-droid.org/packages/com.anindra.messages/"
    const val DEVELOPER = "an1ndra"

    /**
     * GitHub silently drops an issue form's prefill when the body is longer
     * than this, which leaves the reporter staring at an empty form, so the
     * diagnostics are trimmed to fit before the URL is built.
     */
    const val ISSUE_BODY_LIMIT = 4096

    /**
     * Shown in the licences dialog, grouped by licence: four libraries under one
     * Apache heading read better than the same licence name repeated four
     * times. Library and licence names stay untranslated, like SMS and PIN.
     */
    val LICENCES: Map<String, List<String>> = linkedMapOf(
        "Apache License 2.0" to listOf(
            "AndroidX / Jetpack Compose",
            "Coil",
            "libphonenumber",
            "Kotlin coroutines"
        )
    )

    val issuesUrl: String get() = "$REPO_URL/issues/new"

    /**
     * English on purpose: the issue lands in this project's tracker, where the
     * title is read next to everybody else's English reports.
     */
    fun issueTitle(versionName: String): String =
        "Messages $versionName on Android ${Build.VERSION.RELEASE}"

    fun issueUrl(title: String, body: String): String {
        val base = "$issuesUrl?title=${queryValue(title)}"
        return if (body.isBlank()) base else "$base&body=${queryValue(body)}"
    }

    /** Percent-encoding for a query value: spaces become %20, not '+'. */
    fun queryValue(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * The diagnostics an issue can carry, cut to whole lines so it stays under
 * [limit]. The report is sectioned top-down (app, theme, notifications, SIM),
 * so dropping the tail keeps the parts that answer most reports.
 */
internal fun diagnosticsForIssue(report: String, limit: Int = AboutLinks.ISSUE_BODY_LIMIT): String {
    val trimmed = report.trimEnd()
    if (trimmed.length <= limit) return trimmed
    val note = "\n(truncated)"
    val room = limit - note.length
    val kept = StringBuilder()
    for (line in trimmed.lineSequence()) {
        if (kept.length + line.length + 1 > room) break
        kept.append(line).append('\n')
    }
    return kept.trimEnd().toString() + note
}

private fun openAboutUrl(context: Context, url: String) {
    android.util.Log.d("AboutAction", "open $url")
    if (!openUrl(context, url)) {
        Toast.makeText(context, R.string.about_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun shareApp(context: Context, chooserTitle: String, text: String) {
    android.util.Log.d(
        "AboutAction",
        "share len=${text.length} release=${text.contains(AboutLinks.RELEASE_URL)} " +
            "fdroid=${text.contains(AboutLinks.FDROID_URL)}"
    )
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_open_failed, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Who made the app, what it is, and the three things a reader can do about
 * it: star it, report a problem, or pass it on.
 *
 * Takes no AppViewModel: everything shown is static except the diagnostics,
 * which arrive through [requestDiagnostics] so this screen stays previewable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    requestDiagnostics: ((String) -> Unit) -> Unit,
    onBack: () -> Unit,
    scrollState: ScrollState
) {
    val context = LocalContext.current
    val appInfo = remember(context) {
        com.anindra.messages.crash.CrashReporter.appInfo(context)
    }
    val versionName = appInfo.versionName
    val versionCode = appInfo.versionCode
    var issueDialog by remember { mutableStateOf(false) }
    var licencesDialog by remember { mutableStateOf(false) }

    // Back returns to Settings, where this screen was opened from, instead of
    // falling through to the app's root dispatcher and landing on the list.
    BackHandler(onBack = onBack)

    val shareIntro = stringResource(
        R.string.about_share_text,
        AboutLinks.RELEASE_URL,
        AboutLinks.FDROID_URL
    )
    val chooserTitle = stringResource(R.string.about_share_title)
    val issueTitle = AboutLinks.issueTitle(versionName)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                title = { Text(stringResource(R.string.settings_about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.icon_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = SettingsLayout.SCREEN_PADDING)
        ) {
            Spacer(Modifier.height(SettingsLayout.TOP_GAP))

            Identity(versionName, versionCode)

            Spacer(Modifier.height(8.dp))

            SettingsGroup {
                SettingsRow(
                    position = RowPosition.FIRST,
                    title = stringResource(R.string.about_star_title),
                    subtitle = stringResource(R.string.about_star_subtitle),
                    onClick = { openAboutUrl(context, AboutLinks.REPO_URL) }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.about_issue_title),
                    subtitle = stringResource(R.string.about_issue_subtitle),
                    onClick = { issueDialog = true }
                )
                SettingsRow(
                    position = RowPosition.MIDDLE,
                    title = stringResource(R.string.about_share_title),
                    subtitle = stringResource(R.string.about_share_subtitle),
                    onClick = { shareApp(context, chooserTitle, shareIntro) }
                )
                SettingsRow(
                    position = RowPosition.LAST,
                    title = stringResource(R.string.about_licences_title),
                    subtitle = stringResource(R.string.about_licences_subtitle),
                    onClick = { licencesDialog = true }
                )
            }

            Spacer(Modifier.height(SettingsLayout.BOTTOM_GAP))
        }
    }

    if (issueDialog) {
        // Three choices do not fit an AlertDialog's two-button row: they wrapped
        // into a ragged block with Cancel stranded underneath. A dialog that
        // states the privacy contract deserves full-width buttons in the order
        // they are likely to be read.
        Dialog(onDismissRequest = { issueDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.about_issue_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.about_diagnostics_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            issueDialog = false
                            requestDiagnostics { report ->
                                val body = diagnosticsForIssue(report)
                                android.util.Log.d(
                                    "AboutAction",
                                    "issue body=${body.length} limit=${AboutLinks.ISSUE_BODY_LIMIT}"
                                )
                                openAboutUrl(context, AboutLinks.issueUrl(issueTitle, body))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.about_open_with_diagnostics))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            issueDialog = false
                            openAboutUrl(context, AboutLinks.issueUrl(issueTitle, ""))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.about_open_without))
                    }
                    TextButton(
                        onClick = { issueDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }

    if (licencesDialog) {
        Dialog(onDismissRequest = { licencesDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.about_licences_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.about_licences_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    for ((licence, libraries) in AboutLinks.LICENCES) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                licence,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        for (library in libraries) {
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(library, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    TextButton(
                        onClick = { licencesDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            }
        }
    }
}

/** Plate and glyph size of the launcher icon on the About screen. */
private val ICON_PLATE = 136.dp
private val ICON_GLYPH = 96.dp

@Composable
internal fun Identity(versionName: String, versionCode: Long) {
    LaunchedEffect(Unit) {
        android.util.Log.d(
            "AboutIcon",
            "plate=${ICON_PLATE.value}dp glyph=${ICON_GLYPH.value}dp"
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(ICON_PLATE)
                .clip(RoundedCornerShape(38.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_about_app),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON_GLYPH)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_about_version, versionName, versionCode),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        AboutLinks.DEVELOPER.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.about_developer_format, AboutLinks.DEVELOPER),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

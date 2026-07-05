package com.ostemirt.ezbolus.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import com.ostemirt.ezbolus.data.export.ExportManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.ostemirt.ezbolus.data.settings.CurveModelKind
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.data.settings.NotificationStyle
import com.ostemirt.ezbolus.data.settings.supportedDosingIncrements
import androidx.compose.ui.platform.LocalContext
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Glucose unit
            SectionCard(
                title = "Glucose unit",
                blurb = "Applies to target, ISF, and the glucose you type in. " +
                    "Switching auto-converts ISF and target so the clinical values stay the same.",
            ) {
                UnitSegmented(current = s.glucoseUnit, onChange = vm::changeGlucoseUnit)
            }

            // Dosing ratios
            SectionCard(title = "Dosing ratios") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DoubleField(
                        label = "ICR — 1 U covers this many grams",
                        value = s.icr,
                        suffix = "g / U",
                        onCommit = vm::setIcr,
                    )
                    DoubleField(
                        label = "ISF — 1 U lowers glucose by",
                        value = s.isf,
                        suffix = "${s.glucoseUnit.label} / U",
                        onCommit = vm::setIsf,
                    )
                    DoubleField(
                        label = "Target glucose",
                        value = s.target,
                        suffix = s.glucoseUnit.label,
                        onCommit = vm::setTarget,
                    )
                }
            }

            // Insulin decay
            SectionCard(title = "Insulin decay") {
                ActionTimeSlider(
                    hours = s.actionTimeHours,
                    onChange = vm::setActionTime,
                )
                Text(
                    "Decay curve",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
                CurveSegmented(
                    current = s.curveModel,
                    onChange = vm::setCurve,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Pen / pump increment
            SectionCard(
                title = "Pen / pump increment",
                blurb = "Smallest dose your device can deliver. The calculator shows " +
                    "round-down and round-up options in this step.",
            ) {
                IncrementSegmented(
                    current = s.dosingIncrement,
                    onChange = vm::setDosingIncrement,
                )
            }

            // Backup / restore
            BackupSection()

            // IOB alert
            IobAlertSection(
                enabled = s.alertEnabled,
                threshold = s.alertThresholdUnits,
                reArm = s.alertReArmOnRise,
                style = s.alertStyle,
                onEnabledChange = vm::setAlertEnabled,
                onThresholdChange = vm::setAlertThreshold,
                onReArmChange = vm::setAlertReArm,
                onStyleChange = vm::setAlertStyle,
            )
        }
    }
}

// ---------- Backup / restore ----------

@Composable
private fun BackupSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ExportManager(ctx) }
    var status by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = try {
                val count = manager.exportTo(uri)
                "Exported $count entries."
            } catch (t: Throwable) {
                "Export failed: ${t.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = try {
                val summary = manager.importFrom(uri)
                "Imported ${summary.intakeCount} entries. This replaced all previous data."
            } catch (t: Throwable) {
                "Import failed: ${t.message}"
            }
        }
    }

    SectionCard(
        title = "Backup",
        blurb = "Export a JSON file with your settings and history. Import replaces " +
            "everything on this device with the file's contents — useful when moving to " +
            "a new phone.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val name = "ezbolus-backup-${System.currentTimeMillis()}.json"
                        exportLauncher.launch(name)
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f),
                ) { Text("Export") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f),
                ) { Text("Import") }
            }
            status?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- IOB alert section (with permission handling) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IobAlertSection(
    enabled: Boolean,
    threshold: Double,
    reArm: Boolean,
    style: NotificationStyle,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Double) -> Unit,
    onReArmChange: (Boolean) -> Unit,
    onStyleChange: (NotificationStyle) -> Unit,
) {
    val ctx = LocalContext.current
    var notifGranted by remember { mutableStateOf(hasNotifPermission(ctx)) }
    var exactGranted by remember { mutableStateOf(canScheduleExactAlarms(ctx)) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }

    SectionCard(title = "IOB alert") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingRow(
                text = "Notify when insulin on board drops below the threshold",
                checked = enabled,
                onCheckedChange = { on ->
                    onEnabledChange(on)
                    if (on) {
                        if (!notifGranted &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        exactGranted = canScheduleExactAlarms(ctx)
                    }
                },
            )

            if (enabled) {
                if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionWarning(
                        text = "Notifications are blocked. Grant permission or the alert won't fire.",
                        actionLabel = "Grant",
                        onAction = {
                            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
                if (!exactGranted) {
                    PermissionWarning(
                        text = "Exact alarms are disabled — the alert may fire late. Open system " +
                            "settings to allow \"Alarms & reminders\" for EzBolus.",
                        actionLabel = "Open settings",
                        onAction = { openExactAlarmSettings(ctx) },
                    )
                }

                DoubleField(
                    label = "Threshold",
                    value = threshold,
                    suffix = "U",
                    onCommit = onThresholdChange,
                )
                SettingRow(
                    text = "Do not re-alert until IOB rises again",
                    checked = reArm,
                    onCheckedChange = onReArmChange,
                )

                Text(
                    "Notification style",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    NotificationStyle.entries.forEachIndexed { index, ns ->
                        SegmentedButton(
                            selected = style == ns,
                            onClick = { onStyleChange(ns) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = NotificationStyle.entries.size,
                            ),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            icon = {},
                        ) {
                            Text(
                                when (ns) {
                                    NotificationStyle.GENTLE -> "Gentle"
                                    NotificationStyle.ALARM -> "Alarm"
                                },
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Text(
                    when (style) {
                        NotificationStyle.GENTLE ->
                            "Heads-up peek at the top of the screen, silent — the safe default."
                        NotificationStyle.ALARM ->
                            "Plays the notification sound and vibrates. Useful overnight, but " +
                                "remember: the notification is a snapshot — always open the app " +
                                "and recalculate before dosing."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionWarning(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun hasNotifPermission(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else true

private fun canScheduleExactAlarms(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am?.canScheduleExactAlarms() ?: false
    } else true

private fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${ctx.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        ctx.startActivity(intent)
    }
}

// ---------- Section card ----------

@Composable
private fun SectionCard(
    title: String,
    blurb: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (blurb != null) {
                Text(
                    blurb,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Column(Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

// ---------- Segmented controls ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSegmented(current: GlucoseUnit, onChange: (GlucoseUnit) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        GlucoseUnit.entries.forEachIndexed { index, unit ->
            val selected = current == unit
            SegmentedButton(
                selected = selected,
                onClick = { onChange(unit) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = GlucoseUnit.entries.size),
                icon = { if (selected) Icon(Icons.Filled.Check, null) },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(unit.label, fontSize = 13.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurveSegmented(
    current: CurveModelKind,
    onChange: (CurveModelKind) -> Unit,
    modifier: Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        CurveModelKind.entries.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = current == kind,
                onClick = { onChange(kind) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = CurveModelKind.entries.size,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                icon = {},
            ) {
                Text(
                    kind.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncrementSegmented(current: Double, onChange: (Double) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        supportedDosingIncrements.forEachIndexed { index, inc ->
            SegmentedButton(
                selected = current == inc,
                onClick = { onChange(inc) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = supportedDosingIncrements.size,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                icon = {},
            ) { Text(formatIncrement(inc), fontSize = 13.sp) }
        }
    }
}

// ---------- Action time slider ----------

@Composable
private fun ActionTimeSlider(hours: Double, onChange: (Double) -> Unit) {
    // Slider is 2.0..7.0, stepped by 0.5 → 10 gaps → 9 discrete intermediate steps.
    // Compose Slider `steps` counts INTERMEDIATE positions between start and end.
    val steps = 9
    var value by remember { mutableStateOf(hours.toFloat()) }
    LaunchedEffect(hours) { value = hours.toFloat() }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "Action time",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "%.1f h".format(value),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = {
            // Snap to nearest 0.5 defensively and commit.
            val snapped = round(value * 2f) / 2f
            onChange(snapped.toDouble())
            value = snapped
        },
        valueRange = 2f..7f,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("2 h", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("7 h", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Small helpers ----------

@Composable
private fun SettingRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoubleField(
    label: String,
    value: Double,
    suffix: String,
    onCommit: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(formatDouble(value)) }
    LaunchedEffect(value) { text = formatDouble(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val filtered = new.replace(',', '.').filterNumericInput()
            text = filtered
            filtered.toDoubleOrNull()?.let { if (it > 0.0) onCommit(it) }
        },
        label = { Text(label, fontSize = 11.sp) },
        trailingIcon = {
            Text(
                suffix,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun String.filterNumericInput(): String {
    var seenDot = false
    val sb = StringBuilder()
    for (c in this) {
        when {
            c.isDigit() -> sb.append(c)
            c == '.' && !seenDot -> { sb.append(c); seenDot = true }
        }
    }
    return sb.toString()
}

private fun formatDouble(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')

private fun formatIncrement(v: Double): String = when (v) {
    1.0 -> "1 U (whole)"
    0.5 -> "0.5 U"
    0.1 -> "0.1 U"
    else -> "%.2f U".format(v).trimEnd('0').trimEnd('.')
}

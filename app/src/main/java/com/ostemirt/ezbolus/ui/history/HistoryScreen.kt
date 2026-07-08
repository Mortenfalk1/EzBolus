package com.ostemirt.ezbolus.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ostemirt.ezbolus.data.db.Intake
import com.ostemirt.ezbolus.data.db.IntakeKind
import com.ostemirt.ezbolus.ui.calculator.formatDose
import com.ostemirt.ezbolus.ui.theme.LocalKindColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val intakes by vm.intakes.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showAddDialog) {
        AddManualDoseDialog(
            glucoseUnit = settings.glucoseUnit,
            dosingIncrement = settings.dosingIncrement,
            onDismiss = { showAddDialog = false },
            onSave = { units, glucose, carbs, takenAt ->
                vm.saveManualDose(
                    units = units,
                    glucose = glucose,
                    glucoseUnit = settings.glucoseUnit,
                    carbsGrams = carbs,
                    takenAt = takenAt,
                ) {
                    showAddDialog = false
                    scope.launch {
                        val outcome = snackbar.showSnackbar(
                            message = "Logged ${formatDose(units, settings.dosingIncrement)} U",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        if (outcome == SnackbarResult.ActionPerformed) vm.undoSave(takenAt)
                    }
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("History", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Log a past dose")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (intakes.isEmpty()) {
            EmptyState(Modifier.padding(padding))
            return@Scaffold
        }

        val zone = ZoneId.systemDefault()
        val grouped: List<Pair<LocalDate, List<Intake>>> = intakes
            .groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
            .toSortedMap(reverseOrder())
            .map { (day, rows) -> day to rows.sortedByDescending { it.takenAt } }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            for ((day, rows) in grouped) {
                item(key = "hdr-$day") { DayHeader(day, rows) }
                items(rows, key = { it.id }) { row ->
                    IntakeRow(row) { vm.delete(row.id) }
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}

// ---------- Empty state ----------

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Text(
            "No entries yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Confirm a suggested dose on the calculator and it will appear here.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Balance the vertical bias — bottom of the column reads a bit heavy in the design mock.
        Spacer(Modifier.size(80.dp))
    }
}

// ---------- Day header with summary ----------

@Composable
private fun DayHeader(day: LocalDate, rows: List<Intake>) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM yyyy"))
    }
    val summary = summariseDay(rows)
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp, start = 4.dp)) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (summary.isNotEmpty()) {
            Text(
                summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * "6.5 U insulin · 1 correction · 1 meal"
 *
 * A dose is counted as a "meal" if a carbs row shares its takenAt; otherwise
 * it's a "correction". This matches how the calculator decides which mode to
 * run in (empty carbs = correction-only).
 */
private fun summariseDay(rows: List<Intake>): String {
    val insulinRows = rows.filter { it.kind == IntakeKind.INSULIN }
    if (insulinRows.isEmpty()) return ""
    val carbsTimestamps = rows.filter { it.kind == IntakeKind.CARBS }.map { it.takenAt }.toSet()
    val totalU = insulinRows.sumOf { it.insulinUnits ?: 0.0 }
    var meals = 0
    var corrections = 0
    for (r in insulinRows) {
        if (r.takenAt in carbsTimestamps) meals++ else corrections++
    }
    val parts = buildList {
        add("${"%.1f".format(totalU)} U insulin")
        if (corrections > 0) add("$corrections correction${if (corrections == 1) "" else "s"}")
        if (meals > 0) add("$meals meal${if (meals == 1) "" else "s"}")
    }
    return parts.joinToString(" · ")
}

// ---------- Intake row ----------

@Composable
private fun IntakeRow(row: Intake, onDelete: () -> Unit) {
    val time = Instant.ofEpochMilli(row.takenAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            KindDot(row.kind)
            Column(Modifier.weight(1f)) {
                Text(
                    row.displayValue(),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$time · ${row.kindLabel()}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KindDot(kind: String) {
    val kinds = LocalKindColors.current
    val color = when (kind) {
        IntakeKind.INSULIN -> kinds.insulin
        IntakeKind.GLUCOSE -> kinds.glucose
        IntakeKind.CARBS -> kinds.carbs
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun Intake.kindLabel(): String = when (kind) {
    IntakeKind.INSULIN -> "Insulin"
    IntakeKind.GLUCOSE -> "Glucose"
    IntakeKind.CARBS -> "Carbs"
    else -> kind.replaceFirstChar { it.uppercase() }
}

private fun Intake.displayValue(): String = when (kind) {
    IntakeKind.INSULIN -> "${"%.1f".format(insulinUnits ?: 0.0)} U insulin"
    IntakeKind.GLUCOSE -> {
        val v = glucoseValue ?: 0.0
        when (glucoseUnit) {
            "MMOL_L" -> "${"%.1f".format(v)} mmol/L glucose"
            "MG_DL" -> "${v.toInt()} mg/dL glucose"
            else -> "$v glucose"
        }
    }
    IntakeKind.CARBS -> "${(carbsGrams ?: 0.0).toInt()} g carbs"
    else -> "—"
}

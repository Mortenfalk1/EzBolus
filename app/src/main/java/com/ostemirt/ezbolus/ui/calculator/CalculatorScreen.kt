package com.ostemirt.ezbolus.ui.calculator

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ostemirt.ezbolus.data.libre.LibreReading
import com.ostemirt.ezbolus.data.libre.LibreResult
import com.ostemirt.ezbolus.data.libre.LibreTrend
import com.ostemirt.ezbolus.data.settings.AppSettings
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.engine.BolusResult
import com.ostemirt.ezbolus.engine.computeBolus
import com.ostemirt.ezbolus.engine.computeCorrectionOnly
import com.ostemirt.ezbolus.ui.components.FirstRunNudge
import com.ostemirt.ezbolus.ui.components.IobRing
import com.ostemirt.ezbolus.ui.theme.EzBolusText
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    vm: CalculatorViewModel = viewModel(),
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val iob by vm.currentIob.collectAsStateWithLifecycle()
    val iobFraction by vm.currentIobFraction.collectAsStateWithLifecycle()
    val showNudge by vm.showFirstRunNudge.collectAsStateWithLifecycle()

    var carbsText by remember { mutableStateOf("") }
    var glucoseText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<BolusResult?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var correctionOnly by remember { mutableStateOf(false) }

    val libreConnected by vm.libreConnected.collectAsStateWithLifecycle()
    var libreBusy by remember { mutableStateOf(false) }
    var libreChip by remember { mutableStateOf<LibreChip?>(null) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Two pages: 0 = Enter (inputs), 1 = Dose (result). Swipe between them; the
    // dot indicator below also jumps. No vertical auto-scroll.
    val pagerState = rememberPagerState(pageCount = { 2 })
    fun goToPage(page: Int, spec: AnimationSpec<Float>? = null) {
        scope.launch {
            if (spec == null) pagerState.animateScrollToPage(page)
            else pagerState.animateScrollToPage(page, animationSpec = spec)
        }
    }
    // Gentler glide for the automatic Enter -> Dose advance after Calculate, so it
    // eases over rather than snapping. Manual swipes/dot taps keep the snappy default.
    val autoAdvanceSpec = tween<Float>(durationMillis = 650, easing = FastOutSlowInEasing)

    fun clearInputs() {
        carbsText = ""
        glucoseText = ""
        result = null
        errorText = null
        correctionOnly = false
        libreChip = null
    }

    fun fetchLibre() {
        libreBusy = true
        libreChip = null
        val unit = s.glucoseUnit
        val cutoff = s.libreStalenessMinutes
        vm.fetchLibreGlucose { outcome ->
            libreBusy = false
            when (outcome) {
                is LibreResult.Ok -> {
                    val r = outcome.value
                    glucoseText = formatLibreForInput(r, unit)   // pre-fill; user still reviews
                    libreChip = libreChipFor(r, r.ageMinutes(Instant.now()), unit, cutoff)
                }
                is LibreResult.Err -> {
                    libreChip = LibreChip(main = outcome.message, mainIsError = true, note = null, warning = null)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("EzBolus", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.Refresh, contentDescription = "History")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { EzBolusSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> InputPage(
                        showNudge = showNudge,
                        settings = s,
                        iob = iob,
                        iobFraction = iobFraction,
                        carbsText = carbsText,
                        glucoseText = glucoseText,
                        correctionOnly = correctionOnly,
                        errorText = errorText,
                        libreConnected = libreConnected,
                        libreBusy = libreBusy,
                        libreChip = libreChip,
                        onFetchLibre = ::fetchLibre,
                        onOpenSettings = onOpenSettings,
                        onCarbsChange = { carbsText = it.filterNumeric() },
                        onGlucoseChange = { glucoseText = it.filterNumeric() },
                        onCalculate = {
                            errorText = null
                            when (val outcome = calculate(carbsText, glucoseText, s, iob)) {
                                is CalcOutcome.Ok -> {
                                    result = outcome.result
                                    correctionOnly = outcome.correctionOnly
                                    goToPage(1, autoAdvanceSpec)   // gentle glide to the dose page
                                }
                                is CalcOutcome.Err -> {
                                    result = null
                                    errorText = outcome.message
                                }
                            }
                        },
                    )
                    else -> ResultPage(
                        result = result,
                        correctionOnly = correctionOnly,
                        settings = s,
                        onBackToInput = { goToPage(0) },
                        onDismiss = {
                            // Drop the unsaved suggestion but keep the typed inputs,
                            // and slide back so the user can tweak them.
                            result = null
                            errorText = null
                            correctionOnly = false
                            goToPage(0)
                        },
                        onConfirm = { chosenUnits ->
                            val glucose = glucoseText.toDoubleOrNull()
                            val carbs = if (correctionOnly) null else carbsText.toDoubleOrNull()
                            vm.confirmDose(
                                units = chosenUnits,
                                glucose = glucose,
                                glucoseUnit = s.glucoseUnit,
                                carbsGrams = carbs,
                            ) { takenAt ->
                                clearInputs()
                                goToPage(0)
                                scope.launch {
                                    val savedText = "Saved ${formatDose(chosenUnits, s.dosingIncrement)} U"
                                    val outcome = snackbar.showSnackbar(
                                        message = savedText,
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (outcome == SnackbarResult.ActionPerformed) vm.undoSave(takenAt)
                                }
                            }
                        },
                    )
                }
            }

            PageIndicator(
                current = pagerState.currentPage,
                labels = listOf("ENTER", "DOSE"),
                onSelect = { goToPage(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp),
            )
        }
    }
}

// ---------- Pages ----------

@Composable
private fun InputPage(
    showNudge: Boolean,
    settings: AppSettings,
    iob: Double,
    iobFraction: Float,
    carbsText: String,
    glucoseText: String,
    correctionOnly: Boolean,
    errorText: String?,
    libreConnected: Boolean,
    libreBusy: Boolean,
    libreChip: LibreChip?,
    onFetchLibre: () -> Unit,
    onOpenSettings: () -> Unit,
    onCarbsChange: (String) -> Unit,
    onGlucoseChange: (String) -> Unit,
    onCalculate: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showNudge) {
            FirstRunNudge(
                onOpenSettings = onOpenSettings,
                body = "You're still on the defaults — ICR ${settings.icr.trim()}, " +
                    "ISF ${settings.isf.trim()}, target ${settings.target.trim()} " +
                    "${settings.glucoseUnit.label}. Enter your own numbers so every dose " +
                    "matches your plan.",
            )
        }

        CarbsField(text = carbsText, onTextChange = onCarbsChange, correctionOnly = correctionOnly)

        GlucoseField(
            text = glucoseText,
            onTextChange = onGlucoseChange,
            unitLabel = settings.glucoseUnit.label,
        )

        if (libreConnected) {
            LibreFetchRow(busy = libreBusy, chip = libreChip, onFetch = onFetchLibre)
        }

        RatiosCard(iob, iobFraction)

        CalculateButton(
            enabled = !showNudge,
            onClick = {
                focusManager.clearFocus()   // drop the field focus → hides the keyboard
                onCalculate()
            },
        )

        errorText?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ---------- LibreLinkUp fetch (glucose autofill) ----------

/** State for the small chip shown under the glucose field after a Libre fetch. */
private data class LibreChip(
    val main: String,        // value + age + trend, OR an error message
    val mainIsError: Boolean,
    val note: String?,       // neutral secondary line (point-of-use disclaimer)
    val warning: String?,    // red secondary line (stale / rapid trend)
)

private fun formatLibreForInput(r: LibreReading, unit: GlucoseUnit): String =
    // Force a dot decimal separator (java.util.Locale.US) so the pre-filled value
    // parses regardless of device locale — e.g. Danish would otherwise write "6,2",
    // which the calculator's toDoubleOrNull() rejects.
    if (unit == GlucoseUnit.MMOL_L) "%.1f".format(java.util.Locale.US, r.mmol) else r.mgdl.toString()

private fun libreChipFor(r: LibreReading, ageMin: Long, unit: GlucoseUnit, cutoffMin: Int): LibreChip {
    val ageText = if (ageMin <= 0L) "just now" else "$ageMin min ago"
    val trendText = if (r.trend == LibreTrend.UNKNOWN) "" else " · ${r.trend.arrow} ${r.trend.label}"
    val main = "${formatLibreForInput(r, unit)} ${unit.label} · $ageText$trendText"
    val warning = when {
        ageMin > cutoffMin ->
            "This reading is $ageMin min old (your cutoff is $cutoffMin min). Check again " +
                "or fingerstick before dosing."
        r.trend.isRapid ->
            "Glucose is ${r.trend.label} — the sensor lags real blood glucose, so a " +
                "correction may over- or under-shoot. Consider a fingerstick."
        else -> null
    }
    return LibreChip(
        main = main,
        mainIsError = false,
        note = "From LibreLinkUp (a follower feed, not a primary monitor).",
        warning = warning,
    )
}

@Composable
private fun LibreFetchRow(busy: Boolean, chip: LibreChip?, onFetch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onFetch,
            enabled = !busy,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (busy) "  Fetching from Libre…" else "  Fetch glucose from Libre",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        chip?.let { c ->
            Text(
                c.main,
                fontSize = 12.sp,
                fontWeight = if (c.mainIsError) FontWeight.Normal else FontWeight.Medium,
                color = if (c.mainIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            c.warning?.let { w ->
                Text(
                    w,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            c.note?.let { n ->
                Text(
                    n,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultPage(
    result: BolusResult?,
    correctionOnly: Boolean,
    settings: AppSettings,
    onBackToInput: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    if (result == null) {
        // Nothing calculated yet — a calm prompt rather than a blank page.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "No dose yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Swipe back to Enter, type your carbs and glucose, then tap Calculate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = onBackToInput, modifier = Modifier.padding(top = 10.dp)) {
                Text("Go to Enter")
            }
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ResultCard(
            r = result,
            correctionOnly = correctionOnly,
            target = settings.target,
            unitLabel = settings.glucoseUnit.label,
            increment = settings.dosingIncrement,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
        Text(
            "Pick round-down or round-up, fine-tune with −/+ if you need to, then " +
                "Save — it will be logged to your history and reduce future IOB. " +
                "This tool assists your dosing decision, it does not replace your " +
                "care team's guidance.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Two labelled, tappable dots showing / switching the current page. */
@Composable
private fun PageIndicator(
    current: Int,
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == current
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(if (active) 8.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        ),
                )
                Text(
                    label,
                    style = EzBolusText.Eyebrow,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ---------- Input fields ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarbsField(text: String, onTextChange: (String) -> Unit, correctionOnly: Boolean) {
    // Dashed border variant when we're in correction-only mode — matches design frame 3.
    if (correctionOnly && text.isBlank()) {
        // Show a placeholder-styled dashed card that still opens the keyboard.
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Carbs (g) — leave empty for correction only", fontSize = 12.sp) },
            placeholder = { Text("Correction only") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("Carbs (g) — leave empty for correction only", fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlucoseField(text: String, onTextChange: (String) -> Unit, unitLabel: String) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("Current glucose ($unitLabel)", fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            Text(
                unitLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Ratios card with IOB ring ----------

@Composable
private fun RatiosCard(iob: Double, fraction: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IobRing(iob = iob, fraction = fraction)
            Column(Modifier.weight(1f)) {
                Text(
                    "IN USE · INSULIN ON BOARD",
                    style = EzBolusText.Eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${"%.1f".format(iob)} U active",
                    style = EzBolusText.ActiveNumber,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

// ---------- Calculate button ----------

@Composable
private fun CalculateButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("Calculate", fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- Result card ----------

@Composable
private fun ResultCard(
    r: BolusResult,
    correctionOnly: Boolean,
    target: Double,
    unitLabel: String,
    increment: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val stepLabel = when {
        increment >= 1.0 -> "pen delivers whole units"
        increment >= 0.5 -> "pen delivers in 0.5 U steps"
        else -> "pen delivers in 0.1 U steps"
    }

    // The dose is held as an integer count of `increment` steps so every tap
    // stays exactly on the pen's grid (no float drift). The round-down / round-up
    // cards and the manual −/+ all move this same step count.
    val exactSteps = r.total / increment
    val floorSteps = floor(exactSteps).toInt().coerceAtLeast(0)
    val ceilSteps = ceil(exactSteps).toInt().coerceAtLeast(0)
    val onGrid = floorSteps == ceilSteps          // exact dose already deliverable
    // Seeded at the nearest deliverable amount; a new calculation resets it.
    val seedSteps = remember(r.total, increment) {
        exactSteps.roundToInt().coerceAtLeast(0)
    }
    // rememberSaveable (not remember) so the user's round-up/fine-tune survives
    // swiping back to the Enter page and returning — the pager disposes the Dose
    // page's composition, but saveable state is retained per page. Resets only when
    // a new calculation changes r.total / increment.
    var steps by rememberSaveable(r.total, increment) { mutableStateOf(seedSteps) }
    val dose = steps * increment
    val roundDownSelected = !onGrid && steps == floorSteps
    val roundUpSelected = !onGrid && steps == ceilSteps

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header + eyebrow
            Column {
                if (correctionOnly) {
                    Text(
                        "Suggested correction to $unitLabel ${target.trim()}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Exact: ${"%.1f".format(r.total)} U · no carbs entered",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                } else {
                    Text(
                        "SUGGESTED DOSE",
                        style = EzBolusText.Eyebrow,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    Text(
                        "Exact: ${"%.1f".format(r.total)} U · $stepLabel",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            // Round-down / round-up choice — tap to select which deliverable dose
            // you'll take. Both are shown with equal weight so the choice stays
            // deliberate (never a single silently-rounded number).
            DoseChoiceRow(
                onGrid = onGrid,
                floorDose = floorSteps * increment,
                ceilDose = ceilSteps * increment,
                increment = increment,
                roundDownSelected = roundDownSelected,
                roundUpSelected = roundUpSelected,
                onSelectDown = { steps = floorSteps },
                onSelectUp = { steps = ceilSteps },
            )

            // Selected dose, centered, with manual −/+ fine-tuning by the pen step.
            SelectedDoseAdjuster(
                dose = dose,
                increment = increment,
                onDecrement = { if (steps > 0) steps -= 1 },
                onIncrement = { steps += 1 },
            )

            SaveDoseButton(
                dose = dose,
                increment = increment,
                onClick = { onConfirm(dose) },
            )

            DismissDoseButton(onClick = onDismiss)

            // Line items
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (!correctionOnly) LineItem("Carb bolus", r.carbBolus)
                LineItem("Correction (raw)", r.rawCorrection)
                LineItem("Correction after IOB", r.correctionAfterIob)
            }
        }
    }
}

@Composable
private fun DoseChoiceRow(
    onGrid: Boolean,
    floorDose: Double,
    ceilDose: Double,
    increment: Double,
    roundDownSelected: Boolean,
    roundUpSelected: Boolean,
    onSelectDown: () -> Unit,
    onSelectUp: () -> Unit,
) {
    if (onGrid) {
        // Exact dose already lands on the pen grid — one option, no up/down split.
        DoseOption(
            label = "DELIVERABLE DOSE",
            dose = floorDose,
            increment = increment,
            selected = true,
            onClick = onSelectDown,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DoseOption(
                label = "ROUND DOWN",
                dose = floorDose,
                increment = increment,
                selected = roundDownSelected,
                onClick = onSelectDown,
                modifier = Modifier.weight(1f),
            )
            DoseOption(
                label = "ROUND UP",
                dose = ceilDose,
                increment = increment,
                selected = roundUpSelected,
                onClick = onSelectUp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One tappable rounding choice. Selected = filled primary; unselected = plain
 *  surface with a hairline border so both read as equal-weight options. */
@Composable
private fun DoseOption(
    label: String,
    dose: Double,
    increment: Double,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = EzBolusText.Eyebrow, color = content.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatDose(dose, increment),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.5).sp,
                    color = content,
                )
                Text(
                    "U",
                    fontSize = 14.sp,
                    color = content.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                )
            }
        }
    }
}

/** The dose the user will save: big number centered, quiet −/+ on either side
 *  for manual fine-tuning by one pen step. Not filled green — the Save button
 *  below is the only strong accent. */
@Composable
private fun SelectedDoseAdjuster(
    dose: Double,
    increment: Double,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val stepText = formatDose(increment, increment)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AdjustButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Decrease dose by $stepText U",
            enabled = dose > 0.0,
            onClick = onDecrement,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatDose(dose, increment),
                style = EzBolusText.BigDose,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "U",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        AdjustButton(
            icon = Icons.Filled.Add,
            contentDescription = "Increase dose by $stepText U",
            enabled = true,
            onClick = onIncrement,
        )
    }
}

@Composable
private fun AdjustButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (enabled) 0.35f else 0.15f),
        ),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun DismissDoseButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Dismiss without saving",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun SaveDoseButton(dose: Double, increment: Double, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            "Save — I took ${formatDose(dose, increment)} U",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LineItem(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
        )
        Text(
            "${"%.1f".format(value)} U",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// ---------- Snackbar ----------

@Composable
private fun EzBolusSnackbarHost(host: SnackbarHostState) {
    SnackbarHost(host) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

// ---------- Helpers ----------

internal fun formatDose(dose: Double, increment: Double): String =
    if (increment >= 1.0) dose.toInt().toString()
    else "%.1f".format(dose)

private sealed interface CalcOutcome {
    data class Ok(val result: BolusResult, val correctionOnly: Boolean) : CalcOutcome
    data class Err(val message: String) : CalcOutcome
}

private fun calculate(
    carbsText: String,
    glucoseText: String,
    s: AppSettings,
    iob: Double,
): CalcOutcome {
    val glucose = glucoseText.toDoubleOrNull()
        ?: return CalcOutcome.Err("Enter your current glucose.")
    val carbsBlank = carbsText.isBlank()
    return if (carbsBlank) {
        val r = computeCorrectionOnly(
            glucose = glucose,
            isf = s.isf,
            target = s.target,
            iob = iob,
        )
        CalcOutcome.Ok(r, correctionOnly = true)
    } else {
        val carbs = carbsText.toDoubleOrNull()
            ?: return CalcOutcome.Err("Carbs must be a number.")
        val r = computeBolus(
            carbsGrams = carbs,
            glucose = glucose,
            icr = s.icr,
            isf = s.isf,
            target = s.target,
            iob = iob,
        )
        CalcOutcome.Ok(r, correctionOnly = false)
    }
}

private fun String.filterNumeric(): String {
    val n = replace(',', '.')
    var seenDot = false
    val sb = StringBuilder()
    for (c in n) {
        when {
            c.isDigit() -> sb.append(c)
            c == '.' && !seenDot -> { sb.append(c); seenDot = true }
        }
    }
    return sb.toString()
}

private fun Double.trim(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString()
    else "%.2f".format(this).trimEnd('0').trimEnd('.')


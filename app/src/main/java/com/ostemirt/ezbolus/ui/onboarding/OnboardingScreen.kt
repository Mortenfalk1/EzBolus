package com.ostemirt.ezbolus.ui.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.ui.components.DoubleField
import com.ostemirt.ezbolus.ui.components.UnitSegmented
import com.ostemirt.ezbolus.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/**
 * First-run gate: medical disclaimer -> enter your own ICR/ISF/target -> done.
 * Shown once, before the calculator is reachable at all — see
 * [com.ostemirt.ezbolus.MainActivity] for the persisted-flag check that decides
 * whether this screen or the calculator is the initial screen.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    settingsVm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val repo = remember { OnboardingRepository(context) }
    val scope = rememberCoroutineScope()
    val s by settingsVm.state.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    fun goToPage(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        goToPage(pagerState.currentPage - 1)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomePage(onAcknowledge = { goToPage(1) })
                    1 -> RatiosPage(
                        icr = s.icr,
                        isf = s.isf,
                        target = s.target,
                        unit = s.glucoseUnit,
                        onIcrChange = settingsVm::setIcr,
                        onIsfChange = settingsVm::setIsf,
                        onTargetChange = settingsVm::setTarget,
                        onUnitChange = settingsVm::changeGlucoseUnit,
                        onContinue = { goToPage(2) },
                    )
                    else -> DonePage(
                        icr = s.icr,
                        isf = s.isf,
                        target = s.target,
                        unitLabel = s.glucoseUnit.label,
                        onBack = { goToPage(1) },
                        onGetStarted = {
                            scope.launch {
                                repo.setCompleted()
                                onComplete()
                            }
                        },
                    )
                }
            }
            StepDots(current = pagerState.currentPage, count = PAGE_COUNT)
        }
    }
}

@Composable
private fun WelcomePage(onAcknowledge: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Welcome to EzBolus",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Before you use this app, please read this carefully.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "EzBolus is a calculator, not a medical device. It is not reviewed or " +
                "approved by any regulator, and it does not know your medical history. " +
                "Every dose it suggests is only as good as the numbers you enter and the " +
                "ratios you configure.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Always use the ICR, ISF, and target glucose values given to you by your own " +
                "care team — never guess. Review every suggested dose yourself before " +
                "taking it, and never let this app replace your care team's guidance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAcknowledge,
            enabled = true,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("I understand — continue", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RatiosPage(
    icr: Double,
    isf: Double,
    target: Double,
    unit: GlucoseUnit,
    onIcrChange: (Double) -> Unit,
    onIsfChange: (Double) -> Unit,
    onTargetChange: (Double) -> Unit,
    onUnitChange: (GlucoseUnit) -> Unit,
    onContinue: () -> Unit,
) {
    val unitLabel = unit.label
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Set up your ratios",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Enter the numbers your care team gave you. You can change these later in " +
                "Settings — the calculator uses whatever is set here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Glucose unit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        UnitSegmented(current = unit, onChange = onUnitChange)

        DoubleField(
            label = "ICR — 1 U covers this many grams",
            value = icr,
            suffix = "g / U",
            onCommit = onIcrChange,
        )
        DoubleField(
            label = "ISF — 1 U lowers glucose by",
            value = isf,
            suffix = "$unitLabel / U",
            onCommit = onIsfChange,
        )
        DoubleField(
            label = "Target glucose",
            value = target,
            suffix = unitLabel,
            onCommit = onTargetChange,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onContinue,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DonePage(
    icr: Double,
    isf: Double,
    target: Double,
    unitLabel: String,
    onBack: () -> Unit,
    onGetStarted: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "You're all set",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "ICR ${icr.trimmed()} g/U · ISF ${isf.trimmed()} $unitLabel/U · " +
                "target ${target.trimmed()} $unitLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "You can change any of this later from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onGetStarted,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Get started", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }
    }
}

private fun Double.trimmed(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString()
    else "%.2f".format(this).trimEnd('0').trimEnd('.')

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (i == current) 8.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    ),
            )
        }
    }
}

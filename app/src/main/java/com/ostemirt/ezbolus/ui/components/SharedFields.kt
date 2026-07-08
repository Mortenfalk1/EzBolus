package com.ostemirt.ezbolus.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ostemirt.ezbolus.data.settings.GlucoseUnit

/**
 * A labelled numeric field for a single ratio/threshold value, committed on every
 * valid keystroke. Shared by [com.ostemirt.ezbolus.ui.settings.SettingsScreen] and
 * [com.ostemirt.ezbolus.ui.onboarding.OnboardingScreen] so both edit ICR/ISF/target
 * with identical behavior.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DoubleField(
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

/** Glucose-unit picker (mg/dL vs mmol/L), shared between Settings and Onboarding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnitSegmented(current: GlucoseUnit, onChange: (GlucoseUnit) -> Unit) {
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

internal fun formatDouble(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')

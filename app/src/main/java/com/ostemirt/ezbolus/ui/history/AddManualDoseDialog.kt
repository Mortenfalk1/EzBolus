package com.ostemirt.ezbolus.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ostemirt.ezbolus.data.settings.GlucoseUnit
import com.ostemirt.ezbolus.ui.calculator.DismissDoseButton
import com.ostemirt.ezbolus.ui.calculator.SaveDoseButton
import com.ostemirt.ezbolus.ui.calculator.SelectedDoseAdjuster
import com.ostemirt.ezbolus.ui.calculator.filterNumeric
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Log a dose taken outside the calculator flow (e.g. "4U at 11:32"), reusing the
 * calculator's dose stepper / Save-Dismiss styling so both entry points feel
 * identical — this dialog just adds a past date/time picker in front of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualDoseDialog(
    glucoseUnit: GlucoseUnit,
    dosingIncrement: Double,
    onDismiss: () -> Unit,
    onSave: (units: Double, glucose: Double?, carbsGrams: Double?, takenAt: Long) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val nowMillis = remember { System.currentTimeMillis() }
    var takenAt by remember { mutableStateOf(nowMillis) }

    var steps by remember { mutableStateOf(0) }
    val dose = steps * dosingIncrement

    var glucoseText by remember { mutableStateOf("") }
    var carbsText by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val selectedDate = remember(takenAt) { Instant.ofEpochMilli(takenAt).atZone(zone).toLocalDate() }
    val selectedTime = remember(takenAt) { Instant.ofEpochMilli(takenAt).atZone(zone).toLocalTime() }
    val isFuture = takenAt > nowMillis
    val canSave = dose > 0.0 && !isFuture

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Log a past dose",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                    }
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }
                if (isFuture) {
                    Text(
                        "Can't log a dose in the future.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "INSULIN TAKEN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                        SelectedDoseAdjuster(
                            dose = dose,
                            increment = dosingIncrement,
                            onDecrement = { if (steps > 0) steps -= 1 },
                            onIncrement = { steps += 1 },
                        )
                    }
                }

                OutlinedTextField(
                    value = glucoseText,
                    onValueChange = { glucoseText = it.filterNumeric() },
                    label = { Text("Glucose (${glucoseUnit.label}) — optional", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = { carbsText = it.filterNumeric() },
                    label = { Text("Carbs (g) — optional", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                SaveDoseButton(
                    dose = dose,
                    increment = dosingIncrement,
                    enabled = canSave,
                    onClick = {
                        onSave(dose, glucoseText.toDoubleOrNull(), carbsText.toDoubleOrNull(), takenAt)
                    },
                )
                DismissDoseButton(onClick = onDismiss)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // DatePicker communicates in UTC-midnight millis regardless of device zone.
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= nowMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedMillis ->
                        val pickedDate = Instant.ofEpochMilli(pickedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        takenAt = LocalDateTime.of(pickedDate, selectedTime).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val pickedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    takenAt = LocalDateTime.of(selectedDate, pickedTime).atZone(zone).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

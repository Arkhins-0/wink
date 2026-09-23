package com.arkhins.wink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.NightPanel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Dates and times chosen with the system's Material pickers rather than
 * typed. Values travel as ISO strings ("2026-10-03", "2026-10-03T14:00"),
 * which is what the server expects.
 */

private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val minuteFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", Locale.getDefault())

private fun parseDay(v: String): LocalDate? = runCatching { LocalDate.parse(v.trim()) }.getOrNull()
private fun parseMinute(v: String): LocalDateTime? = runCatching { LocalDateTime.parse(v.trim()) }.getOrNull()

/** A read-only field that opens a calendar. [value] is "yyyy-MM-dd" or blank. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true, maxToday: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val day = parseDay(value)
    PickerField(day?.format(dayFormat) ?: "", label, enabled) { open = true }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (day ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = if (maxToday) NotAfterToday else androidx.compose.material3.DatePickerDefaults.AllDates,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = NightPanel),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("OK", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state, colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = NightPanel)) }
    }
}

/** A read-only field that opens a calendar, then a clock. [value] is "yyyy-MM-ddTHH:mm" or blank. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true, defaultDay: String = "") {
    var step by remember { mutableStateOf(0) } // 0 closed, 1 date, 2 time
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    val current = parseMinute(value)
    PickerField(current?.format(minuteFormat) ?: "", label, enabled) { step = 1 }

    if (step == 1) {
        val start = current?.toLocalDate() ?: parseDay(defaultDay) ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = NightPanel),
            confirmButton = {
                TextButton(onClick = {
                    pickedDay = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: start
                    step = 2
                }) { Text("Next", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
        ) { DatePicker(state, colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = NightPanel)) }
    }
    if (step == 2) {
        val time = current?.toLocalTime() ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { step = 0 },
            containerColor = NightPanel,
            title = { Text(label) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val d = pickedDay ?: LocalDate.now()
                    onChange(LocalDateTime.of(d, LocalTime.of(state.hour, state.minute)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")))
                    step = 0
                }) { Text("OK", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { step = 1 }) { Text("Back") } },
        )
    }
}

/** A field that looks like the others but only opens something when tapped. */
@Composable
private fun PickerField(text: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Field(text, {}, label, enabled = enabled, placeholder = "Tap to choose")
        // A transparent layer takes the tap so the text field never opens a keyboard.
        Box(Modifier.matchParentSize().clickable(enabled = enabled, onClick = onClick))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object NotAfterToday : androidx.compose.material3.SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}

package io.github.tikarm.tour.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.tikarm.tour.TourCard
import io.github.tikarm.tour.TourCardText
import io.github.tikarm.tour.TourHost
import io.github.tikarm.tour.rememberTourTargets
import io.github.tikarm.tour.tourTarget

/** The task the tour points at, far enough down that the list has to scroll to it. */
private const val HighlightedTask = 14

private enum class Step(val title: String, val message: String) {
    Progress("Your progress", "See how many tasks are **done** at a glance."),
    Task("A task", "Tick the box to mark a task done. The list scrolls here so the highlight can follow it."),
    Add("Add a task", "Use **+** to write down something new."),
    Theme("Light or dark", "Switch the theme here. The tour card follows it too."),
    Done("You're all set", "This step has no target, so the card shows in the middle."),
}

private data class Task(val title: String, val isDone: Boolean)

private val initialTasks = listOf(
    "Buy groceries", "Call the dentist", "Water the plants", "Pay the electricity bill",
    "Reply to emails", "Book train tickets", "Clean the kitchen", "Go for a run",
    "Return library books", "Fix the bike tire", "Plan the weekend", "Write a birthday card",
    "Back up the laptop", "Pick up dry cleaning", "Read one chapter", "Order printer ink",
    "Walk the dog", "Update the resume", "Defrost the freezer", "Call grandma",
    "Sort old photos", "Renew the passport", "Change bed sheets", "Try a new recipe",
).mapIndexed { index, title -> Task(title, isDone = index % 3 == 0) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleApp() {
    var isDark by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf<Step?>(Step.Progress) }
    val tasks = remember { mutableStateListOf(*initialTasks.toTypedArray()) }
    val targets = rememberTourTargets<Step>()
    val listState = rememberLazyListState()
    val steps = Step.entries

    LaunchedEffect(step) {
        if (step == Step.Task) listState.animateScrollToItem(HighlightedTask)
    }
    MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
        TourHost(
            target = step,
            targets = targets,
            // Back (or Esc on desktop) skips the tour instead of acting on the screen underneath
            onDismissRequest = { step = null },
            tip = { shown ->
                TourCard(
                    step = shown.ordinal,
                    stepCount = steps.size,
                    onNext = { step = steps.getOrNull(shown.ordinal + 1) },
                    onPrevious = { step = steps.getOrNull(shown.ordinal - 1) },
                    onSkip = { step = null },
                ) { index ->
                    TourCardText(title = steps[index].title, message = steps[index].message)
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("My tasks") },
                        actions = {
                            IconButton(onClick = { step = steps.first() }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Start tour")
                            }
                            IconButton(
                                onClick = { isDark = !isDark },
                                modifier = Modifier.tourTarget(Step.Theme, targets),
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = "Switch theme",
                                )
                            }
                        },
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { tasks.add(Task("New task ${tasks.size + 1}", isDone = false)) },
                        modifier = Modifier.tourTarget(Step.Add, targets),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add task")
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    ProgressCard(
                        done = tasks.count { it.isDone },
                        total = tasks.size,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .tourTarget(Step.Progress, targets),
                    )
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        itemsIndexed(tasks) { index, task ->
                            val rowModifier = if (index == HighlightedTask) {
                                Modifier.tourTarget(Step.Task, targets)
                            } else {
                                Modifier
                            }
                            TaskRow(
                                task = task,
                                onToggle = { tasks[index] = task.copy(isDone = !task.isDone) },
                                modifier = rowModifier,
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$done of $total done", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TaskRow(task: Task, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.isDone, onCheckedChange = { onToggle() })
        Text(
            text = task.title,
            color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
        )
    }
}

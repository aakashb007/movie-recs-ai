package com.reelwise.app.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelwise.app.data.model.LearnedTaste
import com.reelwise.app.data.model.MediaType
import java.util.Locale

/** Learned affinities a person would recognise as "into" or "not into", strongest first. */
data class TasteLines(val into: List<Pair<String, Double>>, val notInto: List<Pair<String, Double>>, val leaning: String?)

fun tasteLines(learned: LearnedTaste): TasteLines {
    val all = learned.genres.map { it.key to it.value } +
        learned.decades.map { "${it.key}s" to it.value } +
        learned.languages.filterKeys { it != "en" }.map { languageName(it.key) to it.value }
    val types = learned.types
    val movie = types[MediaType.MOVIE] ?: 0.0
    val tv = types[MediaType.TV] ?: 0.0
    val leaning = when {
        movie - tv > 0.25 -> "Leaning towards films"
        tv - movie > 0.25 -> "Leaning towards series"
        else -> null
    }
    return TasteLines(
        into = all.filter { it.second >= SHOWN }.sortedByDescending { it.second }.take(8),
        notInto = all.filter { it.second <= -SHOWN }.sortedBy { it.second }.take(5),
        leaning = leaning,
    )
}

private const val SHOWN = 0.15

private fun languageName(code: String): String =
    Locale(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearnedTasteCard(learned: LearnedTaste?, onForgetQuietSignals: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Text("What Reelwise has learned", style = MaterialTheme.typography.titleMedium)
            }
            val lines = learned?.takeIf { it.evidence >= MIN_EVIDENCE }?.let(::tasteLines)
            if (lines == null || (lines.into.isEmpty() && lines.notInto.isEmpty())) {
                Text(
                    "Rate a few titles, add some to My list, or just open the ones that catch your eye. " +
                        "Reelwise learns from all of it and your For you feed adapts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                return@Column
            }
            if (lines.into.isNotEmpty()) TasteRow("You're into", lines.into, positive = true)
            if (lines.notInto.isNotEmpty()) TasteRow("Less your thing", lines.notInto, positive = false)
            lines.leaning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
            Text(
                "Learned from ${learned?.evidence ?: 0} titles. Ratings count most; My list, opened details and " +
                    "swiped-past cards count a little, and older activity slowly fades.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            TextButton(onClick = onForgetQuietSignals, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Forget opens and swipes")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasteRow(label: String, items: List<Pair<String, Double>>, positive: Boolean) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (name, value) ->
            val tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Surface(
                shape = RoundedCornerShape(50),
                color = tint.copy(alpha = 0.10f + 0.25f * kotlin.math.abs(value).toFloat()),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
            ) {
                Text(
                    "$name ${strength(value)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (positive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** One to three dots for how strongly the model believes it. */
private fun strength(v: Double): String = "•".repeat(
    when {
        kotlin.math.abs(v) >= 0.5 -> 3
        kotlin.math.abs(v) >= 0.3 -> 2
        else -> 1
    },
)

private const val MIN_EVIDENCE = 3


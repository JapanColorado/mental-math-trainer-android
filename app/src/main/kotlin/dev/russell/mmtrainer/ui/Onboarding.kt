package dev.russell.mmtrainer.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private class WelcomePage(val icon: ImageVector, val title: String, val body: String)

private val PAGES = listOf(
    WelcomePage(Icons.Outlined.Schedule, "Three minutes at a time",
        "Each session mixes key facts to memorize, short lessons on mental techniques, and timed drills. " +
            "A few short sessions a day beat one long one."),
    WelcomePage(Icons.Outlined.Dialpad, "Answer fast, not just right",
        "Facts come back on a schedule, and they only space out once your recall is quick. " +
            "If you don't know an answer, press Show on a blank answer to see it and how to work it out."),
    WelcomePage(Icons.Outlined.Speed, "See how far you've come",
        "The benchmark is a 6-minute test that stays the same difficulty, so your score shows real progress. " +
            "Take it now as a baseline, or jump straight into your first session."),
)

/** A short welcome, shown once to new users. */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onClick = { vm.finishOnboarding() }) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val page = PAGES[i]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconBadge(page.icon, size = 112.dp)
                Spacer(Modifier.height(28.dp))
                Text(page.title, style = Type.h1, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = Type.body, color = Palette.MUTED, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.Center) {
            PAGES.indices.forEach { i ->
                Box(Modifier.padding(horizontal = 4.dp).size(8.dp).clip(CircleShape)
                    .background(if (i == pager.currentPage) Palette.ACCENT else Palette.SURFACE_2))
            }
        }
        // Same height on every page, so the page above doesn't shift on the last one.
        Column(Modifier.height(118.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (last) {
                PrimaryButton("Start my first session", Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow) {
                    vm.finishOnboarding(vm::startDaily)
                }
                SecondaryButton("Take the benchmark first", Modifier.fillMaxWidth().height(54.dp)) {
                    vm.finishOnboarding(vm::startBenchmark)
                }
            } else {
                PrimaryButton("Next", Modifier.fillMaxWidth()) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            }
        }
    }
}

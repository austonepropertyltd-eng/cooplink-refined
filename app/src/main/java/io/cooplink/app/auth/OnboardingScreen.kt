package io.cooplink.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.OnboardingPreferences
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class OnboardingSlide(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val gradient: List<Color>,
)

private val slides = listOf(
    OnboardingSlide(
        emoji = "💰",
        title = "Save Together,\nGrow Together",
        subtitle = "Join your cooperative and build wealth as a community. Every naira saved is a step forward.",
        gradient = listOf(Color(0xFF0A1628), Color(0xFF112240)),
    ),
    OnboardingSlide(
        emoji = "📋",
        title = "Loans in Minutes",
        subtitle = "Apply for loans and get approved fast. Track your repayments in real time.",
        gradient = listOf(Color(0xFF0D1F3C), Color(0xFF1B2A49)),
    ),
    OnboardingSlide(
        emoji = "📊",
        title = "Total Visibility",
        subtitle = "See every contribution, loan, and transaction. Your cooperative finances are always clear.",
        gradient = listOf(Color(0xFF0A1628), Color(0xFF0F2038)),
    ),
    OnboardingSlide(
        emoji = "🚀",
        title = "Your Cooperative\nin Your Pocket",
        subtitle = "Everything your cooperative does — savings, loans, payments — now in one app.",
        gradient = listOf(Color(0xFF112240), Color(0xFF1B2A49)),
    ),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences,
) : ViewModel() {
    fun markDone(onDone: () -> Unit) {
        viewModelScope.launch {
            onboardingPreferences.setDone()
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val slide = slides[page]
            Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(slide.gradient)),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(slide.emoji, style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(32.dp))
                    Text(
                        slide.title, style = MaterialTheme.typography.headlineMedium,
                        color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        slide.subtitle, style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                    )
                }
            }
        }

        TextButton(
            onClick = { viewModel.markDone(onFinished) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp),
        ) { Text("Skip", color = Color.White.copy(alpha = 0.7f)) }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(slides.size) { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        Modifier
                            .size(if (selected) 10.dp else 8.dp)
                            .background(
                                if (selected) io.cooplink.app.ui.theme.CoopGold else Color.White.copy(alpha = 0.3f),
                                CircleShape,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (pagerState.currentPage == slides.lastIndex) {
                        viewModel.markDone(onFinished)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = io.cooplink.app.ui.theme.CoopGold),
            ) {
                Text(
                    if (pagerState.currentPage == slides.lastIndex) "Get Started" else "Next",
                    color = io.cooplink.app.ui.theme.CoopNavyDeep, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

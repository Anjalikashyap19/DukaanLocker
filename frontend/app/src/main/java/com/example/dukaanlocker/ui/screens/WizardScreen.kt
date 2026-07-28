package com.example.dukaanlocker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.StoreMallDirectory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dukaanlocker.WizardAnswers
import com.example.dukaanlocker.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Adaptive question list based on business count ────────────────────────────
private data class QInfo(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun getQuestions(isMultiple: Boolean): List<QInfo> {
    val base = mutableListOf(
        QInfo("How many businesses do you own?", "Tell us about your business portfolio", Icons.Default.Store)
    )
    if (isMultiple) {
        base.add(QInfo("Different categories?", "Do you run businesses across different categories?", Icons.Default.Category))
        base.add(QInfo("Multiple branches?", "Within a category, do you operate multiple branches?", Icons.Default.Business))
    }
    base.add(QInfo("Operation scope", "Where do you operate your business?", Icons.Default.Public))
    base.add(QInfo("Business presence", "How are your operations spread across locations?", Icons.Default.DevicesOther))
    return base
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardScreen(
    onComplete: (WizardAnswers) -> Unit,
    onSkip: () -> Unit,
    onBackToLogin: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    var businessCount by remember { mutableStateOf("ONE") }
    var crossCategory by remember { mutableStateOf(false) }
    var multipleBranches by remember { mutableStateOf(false) }
    var operationScope by remember { mutableStateOf("CITY") }
    var digitalReadiness by remember { mutableStateOf("PHYSICAL") }
    var showingMultipleAnswer by remember { mutableStateOf(false) }

    // Recalculate questions list reactively
    val isMultiple = businessCount == "MULTIPLE"
    val questions = remember(businessCount) { getQuestions(isMultiple) }

    val pagerState = rememberPagerState(pageCount = { questions.size })
    val scope = rememberCoroutineScope()

    // After user picks "Multiple", advance to next question
    LaunchedEffect(businessCount) {
        if (showingMultipleAnswer && businessCount == "MULTIPLE") {
            // Move forward after selection animation
            delay(200)
            scope.launch { pagerState.animateScrollToPage(1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Progress Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ABOUT YOUR BUSINESS",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary, letterSpacing = 1.sp
                )
                Text(
                    text = "${pagerState.currentPage + 1}/${questions.size}",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(questions.size) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index <= pagerState.currentPage) colors.primary else colors.border
                            )
                    )
                }
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            userScrollEnabled = false
        ) { page ->
            // Determine which actual question index we're on
            val qIndex = page

            when {
                // Q1: Business Count
                qIndex == 0 -> BusinessCountQuestion(
                    selected = businessCount,
                    onSelect = {
                        businessCount = it
                        showingMultipleAnswer = true
                    }
                )
                // Q2: Cross-category (only shown for multiple businesses)
                isMultiple && qIndex == 1 -> YesNoQuestion(
                    title = questions[qIndex].title,
                    subtitle = questions[qIndex].subtitle,
                    icon = questions[qIndex].icon,
                    selected = crossCategory,
                    onSelect = { crossCategory = it }
                )
                // Q3: Multiple branches (only shown for multiple businesses)
                isMultiple && qIndex == 2 -> YesNoQuestion(
                    title = questions[qIndex].title,
                    subtitle = questions[qIndex].subtitle,
                    icon = questions[qIndex].icon,
                    selected = multipleBranches,
                    onSelect = { multipleBranches = it }
                )
                // Q4 or Q3 (for single business): Operation Scope
                (!isMultiple && qIndex == 1) || (isMultiple && qIndex == 3) -> OperationScopeQuestion(
                    selected = operationScope,
                    onSelect = { operationScope = it }
                )
                // Q5 or Q4 (for single business): Business Presence
                else -> BusinessPresenceQuestion(
                    selected = digitalReadiness,
                    onSelect = { digitalReadiness = it }
                )
            }
        }

        // Bottom Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage == 0) {
                TextButton(onClick = onBackToLogin) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back to Login", color = colors.textSecondary)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onSkip) {
                    Text("Skip", color = colors.textSecondary.copy(alpha = 0.5f))
                }
            } else {
                TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", color = colors.textSecondary)
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < questions.size - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onComplete(
                            WizardAnswers(
                                businessCount = businessCount,
                                crossCategory = crossCategory,
                                multipleBranches = multipleBranches,
                                operationScope = operationScope,
                                digitalReadiness = digitalReadiness,
                                totalBusinesses = if (businessCount == "ONE") 1
                                else {
                                    var count = 2
                                    if (crossCategory) count += 1
                                    if (multipleBranches) count += 1
                                    count
                                }
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.background)
            ) {
                Text(
                    if (pagerState.currentPage < questions.size - 1) "Next →"
                    else "Complete Setup",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Question 1: Business Count ───────────────────────────────────────────────
@Composable
private fun BusinessCountQuestion(
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Store,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "How many businesses do you own?",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary, textAlign = TextAlign.Center
        )

        Text(
            text = "Tell us about your business portfolio",
            fontSize = 14.sp, color = colors.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SelectionCard(
                modifier = Modifier.weight(1f),
                title = "One Business",
                subtitle = "I own a single shop",
                icon = Icons.Default.Home,
                isSelected = selected == "ONE",
                onClick = { onSelect("ONE") }
            )
            SelectionCard(
                modifier = Modifier.weight(1f),
                title = "Multiple",
                subtitle = "I own multiple shops",
                icon = Icons.Default.StoreMallDirectory,
                isSelected = selected == "MULTIPLE",
                onClick = { onSelect("MULTIPLE") }
            )
        }
    }
}

// ── Yes/No Questions ─────────────────────────────────────────────────────────
@Composable
private fun YesNoQuestion(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(text = subtitle, fontSize = 14.sp, color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionCard(
                modifier = Modifier.weight(1f), title = "Yes", subtitle = "This applies to me",
                icon = Icons.Default.CheckCircle, isSelected = selected, onClick = { onSelect(true) }
            )
            SelectionCard(
                modifier = Modifier.weight(1f), title = "No", subtitle = "This doesn't apply",
                icon = Icons.Default.Cancel, isSelected = !selected, onClick = { onSelect(false) }
            )
        }
    }
}

// ── Operation Scope ──────────────────────────────────────────────────────────
@Composable
private fun OperationScopeQuestion(
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = colors.primary, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Operation scope", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text("Where do you operate your business?", fontSize = 14.sp, color = colors.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScopeOption("CITY", "Within a City", "Local operations in one city", Icons.Default.LocationCity, selected, onSelect)
            ScopeOption("STATE", "Within a State", "Operations across a state", Icons.Default.Map, selected, onSelect)
            ScopeOption("NATIONAL", "Pan India / National", "Operations across multiple states", Icons.Default.Public, selected, onSelect)
        }
    }
}

@Composable
private fun ScopeOption(
    value: String, title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: String, onClick: (String) -> Unit
) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(value) },
        colors = CardDefaults.cardColors(containerColor = if (selected == value) colors.primary.copy(alpha = 0.1f) else colors.cardBg),
        border = BorderStroke(1.dp, if (selected == value) colors.primary.copy(alpha = 0.6f) else colors.border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected == value) colors.primary else colors.textSecondary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
            }
            RadioButton(selected = selected == value, onClick = { onClick(value) },
                colors = RadioButtonDefaults.colors(selectedColor = colors.primary))
        }
    }
}

// ── Business Presence ────────────────────────────────────────────────────────
@Composable
private fun BusinessPresenceQuestion(
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.DevicesOther, contentDescription = null, tint = colors.primary, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Business presence", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text("How are your operations spread across locations?", fontSize = 14.sp, color = colors.textSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScopeOption("PHYSICAL", "Single Physical Store", "One brick & mortar location", Icons.Default.Store, selected, onSelect)
            ScopeOption("SCATTERED", "Multiple Locations", "Multiple physical branches", Icons.Default.Business, selected, onSelect)
            ScopeOption("DIGITAL", "Digital / Online Only", "Online store or digital presence", Icons.Default.Computer, selected, onSelect)
        }
    }
}

// ── Reusable Selection Card ──────────────────────────────────────────────────
@Composable
private fun SelectionCard(
    modifier: Modifier = Modifier,
    title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean, onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) colors.primary.copy(alpha = 0.1f) else colors.cardBg),
        border = BorderStroke(1.dp, if (isSelected) colors.primary.copy(alpha = 0.6f) else colors.border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(if (isSelected) colors.primary.copy(alpha = 0.2f) else colors.border),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (isSelected) colors.primary else colors.textSecondary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSelected) colors.primary else colors.textPrimary)
            Text(subtitle, fontSize = 11.sp, color = colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

package com.iadv.dukaanlocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.components.LauncherLogo
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.strings.appLanguages
import com.iadv.dukaanlocker.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Feature banner data ──
private data class FeatureBanner(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val gradient: List<Color>
)

private val featureBanners = listOf(
    FeatureBanner(
        icon = Icons.Default.Shield,
        title = "Bank-Grade Security",
        desc = "Your business documents stay encrypted, private & safe in one vault.",
        gradient = listOf(Color(0xFF0F766E), Color(0xFF2563EB))
    ),
    FeatureBanner(
        icon = Icons.Default.Notifications,
        title = "Renewal Reminders",
        desc = "Never miss an expiry - get timely alerts for every important document.",
        gradient = listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
    )
)

// ── Document types carousel data ──
private data class DocType(val name: String, val icon: ImageVector, val color: Color)

private val docTypes = listOf(
    DocType("GST Certificate", Icons.Default.Receipt, Color(0xFF2563EB)),
    DocType("PAN Card", Icons.Default.CreditCard, Color(0xFFF59E0B)),
    DocType("Aadhaar Card", Icons.Default.Person, Color(0xFF0F766E)),
    DocType("FSSAI License", Icons.Default.Restaurant, Color(0xFFE91E63)),
    DocType("Trade License", Icons.Default.Assignment, Color(0xFF7C3AED)),
    DocType("Shop & Est.", Icons.Default.Store, Color(0xFF009688)),
    DocType("Fire NOC", Icons.Default.LocalFireDepartment, Color(0xFFFF5722)),
    DocType("Udyam Reg.", Icons.Default.Business, Color(0xFF3F51B5)),
    DocType("Drug License", Icons.Default.Medication, Color(0xFF2196F3)),
    DocType("Factory License", Icons.Default.Factory, Color(0xFF795548))
)

// ── Auto-sliding flat feature banner carousel (DigiLocker style) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onGetStarted: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onLanguageChanged: (String) -> Unit = {}
) {
    val colors = LocalAppColors.current

    val lang = LocalAppLanguage.current
    val selectedLang = appLanguages.firstOrNull { it.code == lang } ?: appLanguages[0]
    var langExpanded by remember { mutableStateOf(false) }

    val categories = remember {
        listOf(
            "Beauty, Salon & Personal Care",
            "Restaurants, Hotels & Catering",
            "Food Retail & Grocery",
            "Healthcare, Clinics & Diagnostics",
            "Pharmacy, Medicines & Medical Equipment",
            "Education & Training",
            "Garments, Textile & Tailoring",
            "IT, Software & Digital Services",
            "Electronics, Electrical & Telecom",
            "Automobile, Transport & Travel",
            "Banking, Finance & Insurance",
            "Construction Materials, Hardware & Industrial Goods",
            "Courier, Logistics & Warehousing",
            "Professional & Consultancy Services",
            "Others"
        )
    }

    val catIcons = remember {
        listOf(
            Icons.Default.Face, Icons.Default.Restaurant, Icons.Default.Store, Icons.Default.LocalHospital,
            Icons.Default.Medication, Icons.Default.School, Icons.Default.Checkroom, Icons.Default.Computer,
            Icons.Default.DevicesOther, Icons.Default.DirectionsCar, Icons.Default.AccountBalance,
            Icons.Default.Hardware, Icons.Default.LocalShipping, Icons.Default.Work, Icons.Default.MoreHoriz
        )
    }

    val catColors = remember {
        listOf(
            Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5), Color(0xFF2196F3),
            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFFF9800),
            Color(0xFFFF5722), Color(0xFF795548), Color(0xFF607D8B), Color(0xFF0F766E),
            Color(0xFFF59E0B), Color(0xFF7C3AED), Color(0xFF64748B)
        )
    }

    val navColor = Color(0xFF2563EB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // ── Sticky top navbar ──
        Column(modifier = Modifier.fillMaxWidth().background(navColor)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: app logo + name
                LauncherLogo(modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DukaanLocker",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Right: search icon + language switcher
                IconButton(onClick = {}, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = AppStrings.get(lang, "Search"),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = it }
                    ) {
                        Row(
                            modifier = Modifier
                                .menuAnchor()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedLang.native,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                            modifier = Modifier.width(240.dp)
                        ) {
                            appLanguages.forEach { appLang ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = appLang.native,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "(${appLang.label})",
                                                fontSize = 11.sp,
                                                color = colors.textSecondary
                                            )
                                            if (appLang.code == selectedLang.code) {
                                                Spacer(modifier = Modifier.weight(1f))
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = colors.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        langExpanded = false
                                        onLanguageChanged(appLang.code)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Scrollable content ──
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Feature slider (auto-slides every 3s) ──
            FeatureSlider(lang = lang)

            Spacer(modifier = Modifier.height(22.dp))

            // ── Documents carousel (circular) ──
            DocumentsCarousel(lang = lang, colors = colors)

            Spacer(modifier = Modifier.height(28.dp))

            // ── Section header ──
        Text(
            text = AppStrings.get(lang, "CATEGORIES"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            letterSpacing = 1.5.sp,
            modifier = Modifier.fillMaxWidth()
        )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Categories list (2 columns, flexible height) ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categories.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEachIndexed { colIdx, cat ->
                            val globalIdx = categories.indexOf(cat)
                            CategoryListCard(
                                name = AppStrings.get(lang, cat),
                                icon = catIcons.getOrElse(globalIdx) { Icons.Default.Business },
                                accent = catColors.getOrElse(globalIdx) { colors.primary },
                                colors = colors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Bottom CTA ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.background
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Text(
                        AppStrings.get(lang, "GET STARTED"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onGetStarted) {
                    Text(
                        AppStrings.get(lang, "Already have an account? Sign In"),
                        fontSize = 12.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ── Auto-sliding flat feature banner carousel (DigiLocker style) ──
@Composable
private fun FeatureSlider(lang: String) {
    val pagerState = rememberPagerState(pageCount = { featureBanners.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        delay(3000)
        scope.launch {
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % featureBanners.size)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val banner = featureBanners[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .height(132.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(banner.gradient))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get(lang, banner.title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppStrings.get(lang, banner.desc),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            banner.icon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(featureBanners.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) colorsActive()
                            else colorsActive().copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

private fun colorsActive(): Color = Color(0xFF2563EB)

// ── Documents carousel (circular) ──
@Composable
private fun DocumentsCarousel(lang: String, colors: AppColors) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = AppStrings.get(lang, "DOCUMENTS YOU CAN STORE"),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.primary,
            letterSpacing = 1.5.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            items(docTypes) { doc ->
                Column(
                    modifier = Modifier.width(78.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .border(1.5.dp, doc.color.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            doc.icon,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = doc.color
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.get(lang, doc.name),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryListCard(
    name: String,
    icon: ImageVector,
    accent: Color,
    colors: AppColors,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.cardBg,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = accent
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                lineHeight = 16.sp
            )
        }
    }
}

package com.iadv.dukaanlocker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.navigation.BottomTab
import com.iadv.dukaanlocker.ui.strings.AppStrings
import com.iadv.dukaanlocker.ui.strings.LocalAppLanguage
import com.iadv.dukaanlocker.ui.theme.*

sealed class BottomNavItem(
    val tab: BottomTab,
    val labelKey: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem(BottomTab.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Business : BottomNavItem(BottomTab.Business, "Business", Icons.Filled.Business, Icons.Outlined.Business)
    data object Docs : BottomNavItem(BottomTab.Docs, "Docs", Icons.Filled.Description, Icons.Outlined.Description)
    data object Team : BottomNavItem(BottomTab.Team, "Team", Icons.Filled.People, Icons.Outlined.People)
    data object Settings : BottomNavItem(BottomTab.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun BottomNavBar(
    currentTab: BottomTab,
    onNavigate: (BottomTab) -> Unit,
    isDarkTheme: Boolean = true,
    showTeam: Boolean = true
) {
    val colors = LocalAppColors.current
    val language = LocalAppLanguage.current

    val items = buildList {
        add(BottomNavItem.Home)
        add(BottomNavItem.Business)
        add(BottomNavItem.Docs)
        if (showTeam) add(BottomNavItem.Team)
        add(BottomNavItem.Settings)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = if (isDarkTheme) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f),
                    spotColor = if (isDarkTheme) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.08f)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) colors.cardBg else colors.background
            ),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(
                1.dp,
                if (isDarkTheme) colors.border else colors.border.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentTab == item.tab

                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) colors.primary else colors.textSecondary.copy(alpha = 0.55f),
                        label = "iconColor"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) colors.primary else colors.textSecondary.copy(alpha = 0.55f),
                        label = "textColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigate(item.tab) }
                            .background(
                                if (isSelected) colors.primary.copy(alpha = 0.10f)
                                else Color.Transparent
                            )
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.labelKey,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = AppStrings.get(language, item.labelKey),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

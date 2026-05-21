package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChannelEntity
import com.example.data.TransactionEntity
import com.example.ui.WalletUiEvent
import com.example.ui.WalletViewModel
import com.example.ui.WalletViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val viewModel: WalletViewModel by viewModels {
        WalletViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // Register event stream listener for toasts
                LaunchedEffect(Unit) {
                    viewModel.eventFlow.collectLatest { event ->
                        when (event) {
                            is WalletUiEvent.Success -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                            }
                            is WalletUiEvent.Error -> {
                                Toast.makeText(context, "❌ " + event.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WalletDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDashboardScreen(viewModel: WalletViewModel) {
    val nodeAlias by viewModel.nodeAlias.collectAsStateWithLifecycle()
    val nodeUri by viewModel.nodeUri.collectAsStateWithLifecycle()
    val totalBalance by viewModel.totalBalance.collectAsStateWithLifecycle()
    val lightningBalance by viewModel.lightningBalance.collectAsStateWithLifecycle()
    val inboundCapacity by viewModel.inboundCapacity.collectAsStateWithLifecycle()
    val onChainBalance by viewModel.onChainBalance.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val isFiatMode by viewModel.isFiatMode.collectAsStateWithLifecycle()

    var showSendSheet by remember { mutableStateOf(false) }
    var showReceiveSheet by remember { mutableStateOf(false) }
    var showChannelSheet by remember { mutableStateOf(false) }

    var selectedTransactionForDetails by remember { mutableStateOf<TransactionEntity?>(null) }
    var isNodeInfoExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Lightning Status",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Keystone Node",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF386A20))
                                )
                                Text(
                                    text = "Active • Syncing Blockchain",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleFiatMode() },
                        modifier = Modifier.testTag("currency_switch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch Unit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { isNodeInfoExpanded = !isNodeInfoExpanded },
                        modifier = Modifier.testTag("node_info_button")
                    ) {
                        Icon(
                            imageVector = if (isNodeInfoExpanded) Icons.Default.Info else Icons.Outlined.Info,
                            contentDescription = "Node URI Info",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp) // Leave clean padding for FAB or CTA bar
            ) {
                // Expanding Information Card
                item {
                    AnimatedVisibility(
                        visible = isNodeInfoExpanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        NodeInfoCard(
                            alias = nodeAlias,
                            uri = nodeUri,
                            onClose = { isNodeInfoExpanded = false }
                        )
                    }
                }

                // Balance Dashboard Widget
                item {
                    BalanceDashboardWidget(
                        totalBalance = totalBalance,
                        lightningBalance = lightningBalance,
                        inboundCapacity = inboundCapacity,
                        onChainBalance = onChainBalance,
                        isFiatMode = isFiatMode,
                        viewModel = viewModel
                    )
                }

                // Quick Ergonomic Lightning Actions Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionPillButton(
                            title = "Send",
                            icon = Icons.Default.ArrowUpward,
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            onClick = { showSendSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_send_payment")
                        )
                        ActionPillButton(
                            title = "Receive",
                            icon = Icons.Default.ArrowDownward,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { showReceiveSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_receive_payment")
                        )
                    }
                }

                // Node Sandbox / Testing Utility (Provides highly organic interactive scenario generation)
                item {
                    SandboxControlCard(
                        onFaucetClick = { viewModel.requestFaucetFunds() },
                        onRouteClick = { viewModel.simulateRoutingActivity() },
                        onOpenChannelClick = { showChannelSheet = true },
                        onResetClick = { viewModel.resetWallet() }
                    )
                }

                // Liquidity Channels Section
                item {
                    SectionHeader(
                        title = "Payment Channels",
                        subtitle = "${channels.size} active connections"
                    )
                }

                if (channels.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(
                            text = "No open payment channels. Use the open channel dialog to connect to an LSP peer.",
                            icon = Icons.Outlined.LinkOff
                        )
                    }
                } else {
                    items(channels, key = { it.id }) { channel ->
                        ChannelItemCard(
                            channel = channel,
                            viewModel = viewModel,
                            onCloseClick = {
                                viewModel.closeChannel(channel)
                            }
                        )
                    }
                }

                // Transaction Activity / Ledger
                item {
                    SectionHeader(
                        title = "Activity Ledger",
                        subtitle = "Payments & routing history"
                    )
                }

                if (transactions.isEmpty()) {
                    item {
                        EmptyStatePlaceholder(
                            text = "No wallet activity logged yet.",
                            icon = Icons.Outlined.HistoryToggleOff
                        )
                    }
                } else {
                    items(transactions, key = { it.id }) { tx ->
                        TransactionItemRow(
                            transaction = tx,
                            viewModel = viewModel,
                            onClick = { selectedTransactionForDetails = tx }
                        )
                    }
                }
            }

            // Bottom Sheets
            if (showSendSheet) {
                SendPaymentSheet(
                    viewModel = viewModel,
                    onDismiss = { showSendSheet = false }
                )
            }

            if (showReceiveSheet) {
                ReceivePaymentSheet(
                    viewModel = viewModel,
                    onDismiss = { showReceiveSheet = false }
                )
            }

            if (showChannelSheet) {
                OpenChannelSheet(
                    viewModel = viewModel,
                    onDismiss = { showChannelSheet = false }
                )
            }

            // Transaction Detail Dialog
            if (selectedTransactionForDetails != null) {
                TransactionDetailsDialog(
                    transaction = selectedTransactionForDetails!!,
                    viewModel = viewModel,
                    onDismiss = { selectedTransactionForDetails = null }
                )
            }
        }
    }
}

// Subcomponents

@Composable
fun NodeInfoCard(
    alias: String,
    uri: String,
    onClose: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("node_info_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LSP Node Specifications",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close specifications",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onClose() }
                )
            }

            Text(
                text = "Alias: $alias",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uri,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CopyAll,
                    contentDescription = "Copy Node String",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(uri))
                            Toast
                                .makeText(context, "Copied Node URI!", Toast.LENGTH_SHORT)
                                .show()
                        }
                )
            }

            Text(
                text = "Give your friends this public node identifier to open channels directly to you, providing instant inbound routing lines.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun BalanceDashboardWidget(
    totalBalance: Long,
    lightningBalance: Long,
    inboundCapacity: Long,
    onChainBalance: Long,
    isFiatMode: Boolean,
    viewModel: WalletViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_balance_widget"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Group + Tap Switcher Hint
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleFiatMode() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "AVAILABLE BALANCE",
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = viewModel.formatBalance(totalBalance),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isFiatMode) {
                    Text(
                        text = "${totalBalance} sat",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = viewModel.satsToFiatString(totalBalance),
                        fontSize = 13.sp,
                        color = Color(0xFF386A20),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // Liquidity Flow Visualizer Bar (Dual-sided Channel Capacities representing Inbound & Outbound splits)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Outbound (Can Send)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Inbound (Can Receive)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Channel Split Graphical Slider Bar
                val totalChannelsCapacity = lightningBalance + inboundCapacity
                val capacityRatio = if (totalChannelsCapacity > 0) {
                    lightningBalance.toFloat() / totalChannelsCapacity.toFloat()
                } else {
                    0.5f
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (capacityRatio > 0) capacityRatio else 0.001f)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if ((1f - capacityRatio) > 0) (1f - capacityRatio) else 0.001f)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = viewModel.formatBalance(lightningBalance),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = viewModel.formatBalance(inboundCapacity),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // On Chain Reserve Status (Funds allocated in BTC UTXOs, not routing channels)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CurrencyBitcoin,
                        contentDescription = "BTC reserves",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "On-Chain Wallet Funds",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = viewModel.formatBalance(onChainBalance),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ActionPillButton(
    title: String,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SandboxControlCard(
    onFaucetClick: () -> Unit,
    onRouteClick: () -> Unit,
    onOpenChannelClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sandbox_control_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚡️ TESTING SANDBOX EVENT HUB",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Text(
                text = "Lightning operates instantly, but requires structured local balance allocations. Use this hub to fund, spin nodes, or route mock payments locally.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Faucet Button
                Button(
                    onClick = onFaucetClick,
                    modifier = Modifier.weight(1f).height(36.dp).testTag("sandbox_btn_faucet"),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF3E0), contentColor = Color(0xFFE65100))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Get Faucet", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Open Channel Trigger
                Button(
                    onClick = onOpenChannelClick,
                    modifier = Modifier.weight(1f).height(36.dp).testTag("sandbox_btn_channel"),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F5E9), contentColor = Color(0xFF2E7D32))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Chan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Route Simulation Button
                Button(
                    onClick = onRouteClick,
                    modifier = Modifier.weight(1f).height(36.dp).testTag("sandbox_btn_route"),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8EAF6), contentColor = Color(0xFF3F51B5))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Loop, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Route Fee", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Reset Button
                Button(
                    onClick = onResetClick,
                    modifier = Modifier.weight(1f).height(36.dp).testTag("sandbox_btn_reset"),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFC62828))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Ledger", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelItemCard(
    channel: ChannelEntity,
    viewModel: WalletViewModel,
    onCloseClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("channel_card_${channel.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (channel.isActive) Color(0xFF386A20) else Color(0xFFC06000))
                    )
                    Text(
                        text = channel.peerName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (channel.isActive) "ACTIVE" else "PENDING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (channel.isActive) Color(0xFF386A20) else Color(0xFFC06000),
                        modifier = Modifier
                            .background(
                                color = if (channel.isActive) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("close_channel_btn_${channel.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Close connection",
                        tint = Color(0xFFBA1A1A),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Liquidity Balance slider
            val ratio = if (channel.capacitySats > 0) {
                channel.localBalanceSats.toFloat() / channel.capacitySats.toFloat()
            } else {
                0.5f
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (ratio > 0) ratio else 0.001f)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if ((1f - ratio) > 0) (1f - ratio) else 0.001f)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }

            // Detail lines
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Local Balance (Can Send)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(viewModel.formatBalance(channel.localBalanceSats), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Remote Balance (Can Receive)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(viewModel.formatBalance(channel.remoteBalanceSats), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LSP Capacity: ${viewModel.formatBalance(channel.capacitySats)}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "URI: " + channel.peerNodeUri.take(15) + "...",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionItemRow(
    transaction: TransactionEntity,
    viewModel: WalletViewModel,
    onClick: () -> Unit
) {
    val amountColor = when (transaction.type) {
        "RECEIVE", "ON_CHAIN_RECEIVE", "ROUTING_FEES" -> Color(0xFF386A20)
        else -> Color(0xFFBA1A1A)
    }

    val typePrefix = when (transaction.type) {
        "RECEIVE", "ON_CHAIN_RECEIVE", "ROUTING_FEES" -> "+"
        else -> "-"
    }

    val iconVector = when (transaction.type) {
        "RECEIVE" -> Icons.Default.CallReceived
        "SEND" -> Icons.Default.CallMade
        "CHANNEL_OPEN" -> Icons.Default.SettingsEthernet
        "CHANNEL_CLOSE" -> Icons.Default.CallMerge
        "ROUTING_FEES" -> Icons.Default.TrendingUp
        else -> Icons.Default.VerticalAlignBottom
    }

    val badgeColor = MaterialTheme.colorScheme.surface

    val iconColor = when (transaction.type) {
        "RECEIVE", "ON_CHAIN_RECEIVE" -> Color(0xFF386A20)
        "SEND" -> Color(0xFFBA1A1A)
        "ROUTING_FEES" -> Color(0xFF005FB0)
        else -> Color(0xFF44474E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
            .testTag("tx_row_${transaction.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = transaction.type,
                    tint = iconColor,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                    )
            }

            Column {
                Text(
                    text = transaction.description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
                Text(
                    text = when (transaction.type) {
                        "RECEIVE" -> "Lightning Payment In"
                        "SEND" -> "Lightning Payment Out"
                        "CHANNEL_OPEN" -> "LSP Peer Channel Setup"
                        "CHANNEL_CLOSE" -> "LSP Channel Liquidation"
                        "ROUTING_FEES" -> "Routed Fee Earned"
                        else -> "On-Chain Refund"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$typePrefix${viewModel.formatBalance(transaction.amountSats)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            Text(
                text = if (transaction.type == "SEND" && transaction.feeSats > 0) {
                    "fee: ${transaction.feeSats} sat"
                } else if (transaction.status == "SUCCESS") {
                    "Success"
                } else {
                    "Pending"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmptyStatePlaceholder(text: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = text,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

// Dialogs & Sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendPaymentSheet(
    viewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    var invoiceString by remember { mutableStateOf("") }
    var inputAmountString by remember { mutableStateOf("") }
    var inputMemo by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(18.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚡️ SEND LIGHTNING PAYMENT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Scan or paste an invoice starting with lnbc... format to initiate peer routing transfer.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Auto Generator for easy testing inside streaming browser
            Button(
                onClick = {
                    invoiceString = "lnbc${Random.nextInt(100, 9999)}u1pjxywqrqw..."
                    inputAmountString = Random.nextInt(200, 15000).toString()
                    inputMemo = listOf(
                        "Giga Coffee Cup",
                        "Nostr Zap tips",
                        "Satoshi Merch Store",
                        "API Host Subscription"
                    ).random()
                },
                modifier = Modifier.testTag("fill_test_invoice_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Generate Mock Payment Invoice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = invoiceString,
                onValueChange = { invoiceString = it },
                label = { Text("Lightning Invoice (lnbc...)") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("invoice_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputAmountString,
                    onValueChange = { inputAmountString = it },
                    label = { Text("Amount (Sats)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("amount_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = inputMemo,
                    onValueChange = { inputMemo = it },
                    label = { Text("Memo / Description") },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("memo_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            val parsedAmount = inputAmountString.toLongOrNull() ?: 0L
            if (parsedAmount > 0) {
                Text(
                    text = "Equivalent Fiat: " + viewModel.satsToFiatString(parsedAmount),
                    fontSize = 11.sp,
                    color = Color(0xFF386A20),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Swipe to Pay Gesture (Custom Drag Implementation for tactile ergonomics!)
            Text(
                text = "TACTILE SWIPE ACTION TO CONFIRM COIN SETTLEMENT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            SlideToConfirmButton(
                onConfirm = {
                    viewModel.sendLightningPayment(
                        invoice = invoiceString,
                        amount = parsedAmount,
                        description = inputMemo
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slide_to_confirm_pay")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceivePaymentSheet(
    viewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    var receiveAmountString by remember { mutableStateOf("") }
    var receiveMemo by remember { mutableStateOf("") }
    var generatedInvoice by remember { mutableStateOf<String?>(null) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(18.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚡️ GENERATE LIGHTNING INVOICE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (generatedInvoice == null) {
                Text(
                    text = "Request inbound liquidity instantly! Create a custom lightning invoice to receive.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = receiveAmountString,
                    onValueChange = { receiveAmountString = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Amount (Sats)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receive_amount_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                val parsedAmt = receiveAmountString.toLongOrNull() ?: 0L
                if (parsedAmt > 0) {
                    Text(
                        text = "Equivalent Value: " + viewModel.satsToFiatString(parsedAmt),
                        fontSize = 11.sp,
                        color = Color(0xFF386A20),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = receiveMemo,
                    onValueChange = { receiveMemo = it },
                    label = { Text("Memo (What is this payment for?)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receive_description_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Button(
                    onClick = {
                        val amt = receiveAmountString.toLongOrNull() ?: 0L
                        if (amt <= 0) {
                            Toast.makeText(context, "Amount must be a positive integer", Toast.LENGTH_SHORT).show()
                        } else {
                            // Generate Invoice locally
                            generatedInvoice = "lnbc${amt}u1pjxywqrqpdqdqdq..." + UUID.randomUUID().toString().take(12)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_generate_invoice_action"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("GENERATE INVOICE", fontWeight = FontWeight.Bold)
                }
            } else {
                // Renders the incredible simulated QR Code Canvas + Invoice Data
                val reqSats = receiveAmountString.toLongOrNull() ?: 0L

                Text(
                    text = "INVOICE RENDERED SUCCESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF386A20),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Mathematically drawn vector QR representation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            QrCodeCanvas()

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Request: ${viewModel.formatBalance(reqSats)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            if (receiveMemo.isNotBlank()) {
                                Text(
                                    text = "\"$receiveMemo\"",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // Copy String Block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = generatedInvoice!!,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(generatedInvoice!!))
                            Toast.makeText(context, "Invoice copy successful!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CopyAll,
                            contentDescription = "Copy text",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Testing Sandbox Simulator Trigger for this Receive!
                Button(
                    onClick = {
                        viewModel.receiveLightningPayment(
                            amount = reqSats,
                            description = receiveMemo
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("simulate_payment_credit_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("SIMULATE CREDIT (SANDBOX SETTLE)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenChannelSheet(
    viewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    var pAlias by remember { mutableStateOf("") }
    var pUri by remember { mutableStateOf("") }
    var chanCapacity by remember { mutableStateOf("") }
    var localAllocation by remember { mutableStateOf("") }

    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(18.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚡️ CONNECT LSP ROUTING PEER",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Opening a channel reserves on-chain funds. Allocate your liquidity splits to fund the initial outbound balance.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        pAlias = "Starbucks LSP"
                        pUri = "03a45c...fed2@starbucks.ln:9735"
                        chanCapacity = "150000"
                        localAllocation = "100000"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Preset 1 (Starbucks)", fontSize = 9.sp)
                }
                Button(
                    onClick = {
                        pAlias = "Bitrefill Hub"
                        pUri = "02bbf...cbe32@bitrefill.com:9735"
                        chanCapacity = "300000"
                        localAllocation = "250000"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Preset 2 (Bitrefill)", fontSize = 9.sp)
                }
            }

            OutlinedTextField(
                value = pAlias,
                onValueChange = { pAlias = it },
                label = { Text("Peer Node Alias") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("peer_alias_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            OutlinedTextField(
                value = pUri,
                onValueChange = { pUri = it },
                label = { Text("Peer Pubkey URI (NodeID@IP:Port)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("peer_uri_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chanCapacity,
                    onValueChange = { chanCapacity = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Total Capacity (Sats)") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("capacity_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                OutlinedTextField(
                    value = localAllocation,
                    onValueChange = { localAllocation = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("My Allocation (Sats)") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("funding_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Button(
                onClick = {
                    val cap = chanCapacity.toLongOrNull() ?: 0L
                    val fund = localAllocation.toLongOrNull() ?: 0L

                    if (cap <= 0 || fund <= 0) {
                        Toast.makeText(context, "Capacity & Funding must be positive integers", Toast.LENGTH_SHORT).show()
                    } else if (fund > cap) {
                        Toast.makeText(context, "Funding cannot exceed capacity", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.openChannel(pAlias, pUri, cap, fund)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_channel_submit_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("ESTABLISH ROUTING TUNNEL", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TransactionDetailsDialog(
    transaction: TransactionEntity,
    viewModel: WalletViewModel,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Transaction Specifications",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Type:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = transaction.type, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Amount:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val isPositive = transaction.type in listOf("RECEIVE", "ON_CHAIN_RECEIVE", "ROUTING_FEES")
                    Text(
                        text = viewModel.formatBalance(transaction.amountSats),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) Color(0xFF386A20) else Color(0xFFBA1A1A)
                    )
                }
                if (transaction.feeSats > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Routing Fee Paid:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "${transaction.feeSats} sat", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("State:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val isSuccess = transaction.status == "SUCCESS"
                    Text(
                        text = transaction.status,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuccess) Color(0xFF386A20) else Color(0xFFBA1A1A)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Creation Time:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    java.util.Date(transaction.timestamp).toString().let {
                        Text(text = it.take(20), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (transaction.preimage.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Preimage Verification Hash", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = transaction.preimage,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.CopyAll,
                                contentDescription = "Copy Preimage",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        clipboard.setText(AnnotatedString(transaction.preimage))
                                        Toast.makeText(context, "Copied Preimage!", Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("DONE", fontWeight = FontWeight.Bold)
            }
        }
    )
}

// Graphic Elements

@Composable
fun QrCodeCanvas() {
    Canvas(
        modifier = Modifier
            .size(160.dp)
            .background(Color.White)
    ) {
        // Draw 15x15 simulated QR bits beautifully representing decentralized cryptology
        val sizeVal = 15
        val elementWidth = size.width / sizeVal
        val elementHeight = size.height / sizeVal

        val random = Random(12456L) // stable seed for clean looking code

        for (x in 0 until sizeVal) {
            for (y in 0 until sizeVal) {
                // Keep corner calibration patterns solid
                val isFinderPattern = (x < 4 && y < 4) || (x >= sizeVal - 4 && y < 4) || (x < 4 && y >= sizeVal - 4)
                val bitColor = if (isFinderPattern) {
                    // Finder pattern styling
                    if ((x == 0 || x == 3 || y == 0 || y == 3) || (x == sizeVal - 1 || x == sizeVal - 4 || y == 0 || y == 3) || (x == 0 || x == 3 || y == sizeVal - 1 || y == sizeVal - 4)) {
                        Color(0xFF151412)
                    } else if (x == 1 && y == 1 || x == sizeVal - 2 && y == 1 || x == 1 && y == sizeVal - 2) {
                        Color.White
                    } else {
                        Color(0xFF151412)
                    }
                } else {
                    // Random bits with stable seed
                    if (random.nextBoolean()) Color(0xFF151412) else Color.White
                }

                drawRect(
                    color = bitColor,
                    topLeft = Offset(x * elementWidth, y * elementHeight),
                    size = Size(elementWidth + 0.3F, elementHeight + 0.3F)
                )
            }
        }

        // Draw central elegant Glowing Amber Bitcoin lightning bolt over QR patterns
        val startBoltX = size.width * 0.45F
        val startBoltY = size.height * 0.25F
        val widthBolt = size.width * 0.15F

        val boltPath = Path().apply {
            moveTo(startBoltX + widthBolt, startBoltY)
            lineTo(startBoltX - widthBolt * 0.4F, startBoltY + size.height * 0.22F)
            lineTo(startBoltX + widthBolt * 0.25F, startBoltY + size.height * 0.22F)
            lineTo(startBoltX - widthBolt * 0.8F, startBoltY + size.height * 0.52F)
            lineTo(startBoltX + widthBolt * 1.5F, startBoltY + size.height * 0.24F)
            lineTo(startBoltX + widthBolt * 0.5F, startBoltY + size.height * 0.24F)
            close()
        }

        // Background shadow for lightning logo
        drawPath(
            path = boltPath,
            color = Color.White
        )
        // Main gorgeous amber bolt
        drawPath(
            path = boltPath,
            color = Color(0xFFEF6C00)
        )
    }
}

/**
 * Custom Swipe to pay gesture button. Fully responsive and tactile mobile experience.
 */
@Composable
fun SlideToConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragProgress by remember { mutableStateOf(0f) }
    val maxDragDps = 250.dp
    val density = LocalDensity.current
    val maxDragPx = with(density) { maxDragDps.toPx() }

    val animateOffset by animateFloatAsState(targetValue = dragProgress)
    val buttonColor by animateColorAsState(
        targetValue = if (dragProgress == maxDragPx) Color(0xFF386A20) else MaterialTheme.colorScheme.primary
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        // Label instructions that fade as you drag right
        Text(
            text = "SLIDE RIGHT TO COMMENCE PAYMENT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = (1f - (animateOffset / maxDragPx)).coerceIn(0f, 1f)),
            letterSpacing = 1.2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Track bar fill
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { animateOffset.toDp() + 56.dp })
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15F))
        )

        // Custom Swipe Thumb Handle
        Box(
            modifier = Modifier
                .offset { IntOffset(animateOffset.roundToInt(), 0) }
                .size(56.dp)
                .padding(4.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragProgress >= maxDragPx * 0.85F) {
                                dragProgress = maxDragPx
                                onConfirm()
                            } else {
                                dragProgress = 0F
                            }
                        },
                        onDragCancel = {
                            dragProgress = 0F
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragProgress = (dragProgress + dragAmount.x).coerceIn(0F, maxDragPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (dragProgress == maxDragPx) Icons.Default.Check else Icons.Default.KeyboardArrowRight,
                contentDescription = "Swipe Handle",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

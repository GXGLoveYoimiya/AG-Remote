package com.local.agremote.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.TextButton
import com.local.agremote.Translator
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalFocusManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.rounded.Image
import com.local.agremote.ModelInfo
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.rotate
import com.local.agremote.AppViewModel
import com.local.agremote.Step
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    onMenu: () -> Unit,
    showOverview: Boolean = false,
    onShowOverview: (Boolean) -> Unit = {},
    onOpenCustomizations: () -> Unit = {},
    onOpenUsage: () -> Unit = {},
) {
    val s by vm.chat.collectAsState()
    val list by vm.list.collectAsState()
    val selected by vm.selected.collectAsState()
    val models by vm.models.collectAsState()
    val account by vm.account.collectAsState()
    val sendingQueuedIds by vm.sendingQueuedIds.collectAsState()
    var pickModel by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val voiceCall by vm.voiceCall.collectAsState()
    val curConv = s.conv ?: s.id?.let { id -> list.find { it.id == id } }
    val focusManager = LocalFocusManager.current
    val visible = remember(s.steps) { s.steps.filter { it.kind != "hidden" } }
    // 实时语音的记录和普通消息分开存：按开始时间插进消息流里
    val rows = remember(visible, s.voiceChats) { withVoiceChats(groupRows(visible), s.voiceChats) }
    val running = isRunning(s.conv?.status)
    // 正在干活：电脑说在运行、刚发出的消息还没回音、或者最后一条还是我的消息
    val working = running || s.pending != null || (visible.lastOrNull()?.kind == "user" && s.conv?.status != "idle")
    val listState = remember(s.id) { LazyListState() }

    // 跟着 listState 重建：换对话后 listState 是新的，不能还盯着旧列表判断“是否在底部”
    val atBottom by remember(listState) { derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()
        last == null || last.index >= info.totalItemsCount - 2
    } }
    var firstScroll by remember(s.id) { mutableStateOf(true) }
    LaunchedEffect(visible.size, visible.lastOrNull()?.text?.length, visible.lastOrNull()?.status, s.pending, s.queued.size, working) {
        if (visible.isEmpty() && s.pending == null && s.queued.isEmpty()) return@LaunchedEffect
        if (firstScroll || atBottom || s.pending != null || s.queued.isNotEmpty()) {
            listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
            firstScroll = false
        }
    }

    // 额度圆环：进来先读一次，之后每分钟刷新；每轮回复结束也刷新
    LaunchedEffect(Unit) { while (true) { if (com.local.agremote.AppState.foreground) vm.refreshQuota(); kotlinx.coroutines.delay(60_000) } } // 在后台不刷新
    LaunchedEffect(working) { if (!working) vm.refreshQuota() }

    val projects by vm.projects.collectAsState()
    val newProjName by vm.newChatProjectName.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { focusManager.clearFocus(); onMenu() }) { Icon(Icons.Rounded.Menu, "对话列表") } },
                title = {
                    // 项目里的对话：“项目 / 对话标题”；项目里的新对话：项目图标 + 项目名（照 Claude）
                    val projName = if (s.id == null) newProjName
                        else projects.find { !it.sidecar && s.id in it.conversations }?.name ?: ""
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (projName.isNotBlank()) {
                            Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(projName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            if (s.id != null) Text("  /  ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        if (s.id != null) Text(curConv?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    }
                },
                actions = {
                    IconButton(onClick = { focusManager.clearFocus(); vm.openBrowser() }) {
                        Icon(Icons.Rounded.Language, "内置浏览器")
                    }
                    // 当前模型剩余的 5 小时额度，点开是用量页
                    if (s.id != null || s.pending != null) vm.fiveHourBucket(selected)?.let { b -> // 空白的新对话页不显示
                        IconButton(onClick = { focusManager.clearFocus(); onOpenUsage() }) { QuotaRing(b.remaining) }
                    }
                    if (s.id != null) {
                        if (curConv != null) {
                            Box {
                                IconButton(onClick = { focusManager.clearFocus(); moreMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, "更多操作")
                                }
                                ConvActionMenus(vm, curConv, expanded = moreMenu, onDismiss = { moreMenu = false }, showNewChat = true,
                                    onOverview = { focusManager.clearFocus(); onShowOverview(true) })
                            }
                        } else {
                            IconButton(onClick = { vm.newChat() }) { Icon(Icons.Rounded.EditNote, "新对话") }
                        }
                    }
                },
            )
        },
        bottomBar = { InputBar(vm, working, s.id, vm.modelChipLabel(selected), vm.modelLabel(selected)) { pickModel = true } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val empty = s.id == null && s.pending == null
            when {
                empty -> Column(Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    // App 自己的图标（和桌面图标、语音模式里的一样）
                    Image(painterResource(com.local.agremote.R.drawable.ic_fg), null, Modifier.size(84.dp))
                    Spacer(Modifier.size(16.dp))
                    if (newProjName.isBlank()) Text(
                        greeting(account?.name),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    ) else {
                        // 项目里的新对话：“关于 书，我能帮你做些什么？”，项目名用强调色
                        val parts = tr("关于 {0}，我能帮你做些什么？").split("{0}")
                        val ac = accent()
                        Text(
                            buildAnnotatedString {
                                append(parts.first())
                                withStyle(SpanStyle(color = ac)) { append(newProjName) }
                                append(parts.getOrElse(1) { "" })
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                s.loading && s.steps.isEmpty() && s.pending == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.error != null && s.steps.isEmpty() -> Text(s.error ?: "", Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error)
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                    val chatId = s.id ?: ""
                    val hasWorkspace = !s.conv?.workspace.isNullOrBlank()
                    items(rows, key = { it.key }) { r ->
                        when (r) {
                            is ChatRow.Single -> StepItem(vm, chatId, hasWorkspace, r.step)
                            is ChatRow.Process -> ProcessBlock(vm, chatId, r)
                            is ChatRow.Answer -> AnswerBody(vm, r.step)
                            is ChatRow.Voice -> VoiceChatBlock(vm, r.chat)
                        }
                    }
                    items(s.queued, key = { "q_" + it.id }) { q ->
                        val busyNow = q.id in sendingQueuedIds
                        UserBubble(
                            vm, q.text,
                            queued = true,
                            queuedImmediate = q.immediate,
                            sendingNow = busyNow,
                            onSendNow = if (q.immediate) null else ({ vm.sendQueuedNow(q) }),
                            onEditQueued = if (busyNow) null else ({ vm.editQueued(q) }),
                            onDeleteQueued = if (busyNow) null else ({ vm.deleteQueued(q) }),
                        )
                    }
                    s.pending?.let { p -> item(key = "pending") { UserBubble(vm, p, local = s.pendingFiles, mentions = s.pendingMentions) } }
                    if (working) item(key = "working") { WorkingRow(s.conv?.status == "canceling") }
                    item(key = "bottom") { Spacer(Modifier.size(4.dp)) }
                }
            }
        }
    }

    if (pickModel) ModelSheet(models, selected, onPick = { vm.pickModel(it) }, onClose = { pickModel = false })
    if (showOverview && s.id != null) {
        OverviewSheet(
            vm = vm,
            onOpenCustomizations = { onShowOverview(false); onOpenCustomizations() },
            onClose = { onShowOverview(false) },
        )
    }
    val preview by vm.preview.collectAsState()
    preview?.let { PreviewDialog(it) { vm.openPreview(null) } }
    voiceCall?.let { VoiceCallScreen(vm, it) }
}

@Composable
private fun greeting(name: String?): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val key = when (h) { in 5..10 -> "早上好"; in 11..12 -> "中午好"; in 13..17 -> "下午好"; else -> "晚上好" }
    val g = tr(key)
    val sep = if (LocalAppLang.current.startsWith("zh") || LocalAppLang.current == "ja") "，" else ", "
    return if (name.isNullOrBlank()) g else "$g$sep$name"
}

@Composable
private fun WorkingRow(canceling: Boolean) {
    Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ThinkingSpark(accent(), Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        ShimmerText(if (canceling) tr("正在停止…") else tr("正在运行…"))
    }
}

/** 思考中的小动画：一颗四角星慢慢转、轻轻“呼吸”，右上角一颗小星和它错开节奏一闪一闪。 */
@Composable
private fun ThinkingSpark(color: Color, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "spark")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "ph")
    androidx.compose.foundation.Canvas(modifier) {
        val twoPi = (2 * Math.PI).toFloat()
        fun star(c: Offset, r: Float, col: Color) {
            // 四角星：四个尖之间用向中心凹的曲线连起来
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x, c.y - r)
                quadraticTo(c.x, c.y, c.x + r, c.y)
                quadraticTo(c.x, c.y, c.x, c.y + r)
                quadraticTo(c.x, c.y, c.x - r, c.y)
                quadraticTo(c.x, c.y, c.x, c.y - r)
                close()
            }
            drawPath(p, col)
        }
        val big = size.minDimension * 0.42f * (0.78f + 0.22f * (0.5f - 0.5f * kotlin.math.cos(ph * twoPi)))
        val mainC = Offset(size.width * 0.44f, size.height * 0.56f)
        rotate(ph * 90f, pivot = mainC) { star(mainC, big, color) }
        val tw = kotlin.math.sin((ph + 0.5f) * twoPi).coerceAtLeast(0f) // 小星：主星缩小时它亮起来
        if (tw > 0.02f) star(Offset(size.width * 0.86f, size.height * 0.16f), size.minDimension * 0.15f * (0.5f + 0.5f * tw), color.copy(alpha = tw))
    }
}

/** 文字上有一道高光从左往右扫过（思考中的提示）。 */
@Composable
private fun ShimmerText(text: String) {
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val hi = MaterialTheme.colorScheme.onSurface
    val tr = rememberInfiniteTransition(label = "shimmer")
    val x by tr.animateFloat(-200f, 600f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "x")
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(base, hi, base), start = Offset(x, 0f), end = Offset(x + 160f, 0f))))
}

/** 模型选择：主力模型一组，下面是思考强度和更多模型。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSheet(models: List<ModelInfo>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    val families = remember(models) { models.groupBy { it.family }.toList() }
    // 有思考强度分组的放上面，Antigravity 没归组的放进“更多模型”
    fun isMain(levels: List<ModelInfo>) = levels.first().grouped
    val main = families.filter { isMain(it.second) }
    val more = families.filter { !isMain(it.second) }
    val cur = models.find { it.model == selected }
    val curLevels = families.find { it.first == cur?.family }?.second ?: emptyList()
    var page by remember { mutableStateOf("main") }

    // 换系列时尽量保持同样的思考强度
    fun pickFamily(levels: List<ModelInfo>) {
        val keep = levels.find { it.level == cur?.level } ?: levels.first()
        onPick(keep.model)
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, dragHandle = null) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
                IconButton(onClick = { if (page == "main") onClose() else page = "main" }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(if (page == "main") Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "关闭")
                }
                Text(when (page) { "levels" -> "Thinking"; "more" -> tr("更多"); else -> tr("选择模型") },
                    Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (models.isEmpty()) Text(tr("读取中…"), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.noSheetOverscroll().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    "main" -> {
                        Group {
                            main.forEachIndexed { k, (fam, levels) ->
                                if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow)
                                OptionRow(fam, levels.first().desc, levels.first().tag, fam == cur?.family) { pickFamily(levels); onClose() }
                            }
                        }
                        if (curLevels.size > 1) Group { NavRow(tr("思考强度"), cur?.level ?: "") { page = "levels" } }
                        if (more.isNotEmpty()) Group {
                            NavRow(tr("更多模型"), if (more.any { it.first == cur?.family }) cur?.family ?: "" else "") { page = "more" }
                        }
                    }
                    "levels" -> Group {
                        curLevels.forEachIndexed { k, m ->
                            if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow)
                            OptionRow(m.level, "", "", m.model == selected) { onPick(m.model); page = "main" }
                        }
                    }
                    "more" -> Group {
                        more.forEachIndexed { k, (fam, levels) ->
                            if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow)
                            OptionRow(fam, levels.first().desc, levels.first().tag, fam == cur?.family) { pickFamily(levels); onClose() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(color = sheetCardColor(), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun OptionRow(title: String, desc: String, tag: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (tag.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                        Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (checked) Icon(Icons.Rounded.Check, "已选", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun StepItem(vm: AppViewModel, chatId: String, hasWorkspace: Boolean, step: Step) {
    when {
        step.waiting != null -> WaitingCard(vm, hasWorkspace, step)
        step.kind == "user" -> UserBubble(vm, step.text, remote = step.attachments, mentions = step.mentions)
        step.kind == "assistant" -> AnswerBody(vm, step)
        step.kind == "error" -> ErrorCard(step.text)
        else -> ToolRow(step)
    }
}

@Composable
private fun UserBubble(vm: AppViewModel, text: String, remote: List<com.local.agremote.StepAttachment> = emptyList(),
                       local: List<com.local.agremote.Attachment> = emptyList(),
                       mentions: List<com.local.agremote.Mention> = emptyList(),
                       queued: Boolean = false,
                       queuedImmediate: Boolean = false,
                       sendingNow: Boolean = false,
                       onSendNow: (() -> Unit)? = null,
                       onEditQueued: (() -> Unit)? = null,
                       onDeleteQueued: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalAlignment = Alignment.End) {
        if (mentions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 320.dp).padding(bottom = 6.dp)) {
                mentions.forEach { m -> MentionChip(vm, m.name, m.logo) }
            }
        }
        // 附件：图片显示缩略图，其他文件显示文件名
        if (remote.isNotEmpty() || local.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 320.dp).padding(bottom = 6.dp)) {
                local.forEach { a ->
                    if (a.thumb != null) Box(
                        Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).clickable { vm.openPreview(localPreview(a)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(a.thumb.asImageBitmap(), a.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        if (a.mime.startsWith("video/")) VideoPlayBadge()
                    }
                    else FileCard(a.name) { vm.openPreview(localPreview(a)) }
                }
                remote.forEach { a -> AttachmentView(vm, a) }
            }
        }
        if (text.isNotBlank()) Surface(
            color = if (queued) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            var expanded by remember(text) { mutableStateOf(false) }
            var overflowed by remember(text) { mutableStateOf(false) }
            val linkColor = MaterialTheme.colorScheme.primary
            val annotated = remember(text, linkColor) {
                val re = Regex("""https?://[^\s<>)\]"'，。；：！？、】）》]+""")
                buildAnnotatedString {
                    var last = 0
                    for (m in re.findAll(text)) {
                        append(text.substring(last, m.range.first))
                        var u = m.value
                        var trail = ""
                        while (u.isNotEmpty() && u.last() in charArrayOf('.', ',', ';', ':', '!', '?', ')', ']')) {
                            trail = u.last() + trail
                            u = u.dropLast(1)
                        }
                        if (u.startsWith("http://", true) || u.startsWith("https://", true)) {
                            pushLink(
                                androidx.compose.ui.text.LinkAnnotation.Clickable(
                                    tag = "url",
                                    styles = androidx.compose.ui.text.TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = { vm.openLink(u) },
                                ),
                            )
                            append(u)
                            pop()
                            append(trail)
                        } else {
                            append(m.value)
                        }
                        last = m.range.last + 1
                    }
                    if (last < text.length) append(text.substring(last))
                }
            }
            Column(Modifier.animateContentSize()) {
                SelectionContainer {
                    Text(
                        annotated,
                        Modifier.padding(
                            start = 14.dp,
                            end = 14.dp,
                            top = 10.dp,
                            bottom = if (overflowed || expanded) 4.dp else 10.dp,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { res -> if (!expanded && res.hasVisualOverflow) overflowed = true },
                    )
                }
                if (overflowed || expanded) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val subTint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        Text(
                            if (expanded) tr("收起") else tr("展开全部"),
                            style = MaterialTheme.typography.labelMedium,
                            color = subTint,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (expanded) tr("收起") else tr("展开全部"),
                            Modifier.size(16.dp),
                            tint = subTint,
                        )
                    }
                }
            }
        }
        if (queued) {
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(if (queuedImmediate) tr("发送中…") else tr("排队中"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onSendNow != null) {
                    Surface(
                        onClick = onSendNow,
                        enabled = !sendingNow,
                        color = if (sendingNow) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (sendingNow) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.8.dp)
                                Spacer(Modifier.width(5.dp))
                                Text(tr("发送中…"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(3.dp))
                                Text(tr("立刻发送"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
                if (onEditQueued != null) {
                    Surface(
                        onClick = onEditQueued,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(50),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Edit, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(3.dp))
                            Text(tr("编辑"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (onDeleteQueued != null) {
                    Surface(
                        onClick = onDeleteQueued,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(50),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(2.dp))
                            Text(tr("取消"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** 消息里的一个附件：图片和视频显示缩略图，其他显示文件卡片；点开预览。 */
@Composable
private fun AttachmentView(vm: AppViewModel, a: com.local.agremote.StepAttachment) {
    when {
        a.mime.startsWith("image/") -> RemoteImage(vm, a)
        a.mime.startsWith("video/") -> RemoteVideo(vm, a)
        else -> FileCard(a.name) { vm.openPreview(vm.previewOf(a)) }
    }
}

@Composable
private fun VideoPlayBadge(size: Int = 38) {
    Surface(color = Color.Black.copy(alpha = 0.55f), shape = CircleShape, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, "播放视频", Modifier.size((size * 0.62f).dp), tint = Color.White)
        }
    }
}

@Composable
private fun RemoteVideo(vm: AppViewModel, a: com.local.agremote.StepAttachment) {
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, a.path, a.ref) { value = vm.videoThumbBitmap(a) }
    val b = bmp
    Box(
        Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { vm.openPreview(vm.previewOf(a)) },
        contentAlignment = Alignment.Center,
    ) {
        if (b != null) Image(b.asImageBitmap(), a.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        VideoPlayBadge()
    }
}

@Composable
private fun RemoteImage(vm: AppViewModel, a: com.local.agremote.StepAttachment) {
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, a.path, a.ref) { value = vm.mediaBitmap(a) }
    val b = bmp
    val m = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).clickable { vm.openPreview(vm.previewOf(a)) }
    if (b != null) Image(b.asImageBitmap(), a.name, m, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    else Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = m) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.outline) }
    }
}

@Composable
private fun FileCard(name: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Description, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp))
        }
    }
}

/** 一段连续的 agent 活动：思考、工具调用、中间回复收进“思考过程”，最后的回答单独显示。 */
private sealed interface ChatRow {
    val key: String
    data class Single(val step: Step) : ChatRow { override val key get() = "s${step.i}" }
    data class Process(val steps: List<Step>, val answer: Step?) : ChatRow { override val key get() = "p${steps.first().i}" }
    data class Answer(val step: Step) : ChatRow { override val key get() = "a${step.i}" }
    data class Voice(val chat: com.local.agremote.VoiceChatRec) : ChatRow { override val key get() = "v${chat.startedAt}" }
}

private fun rowTime(r: ChatRow): String = when (r) {
    is ChatRow.Single -> r.step.time
    is ChatRow.Process -> r.steps.first().time
    is ChatRow.Answer -> r.step.time
    is ChatRow.Voice -> r.chat.startedAt
}

/** 把每次语音通话插到它开始之后的第一条消息前面（时间都是 ISO 格式，可以直接比较）。 */
private fun withVoiceChats(rows: List<ChatRow>, chats: List<com.local.agremote.VoiceChatRec>): List<ChatRow> {
    if (chats.isEmpty()) return rows
    val out = rows.toMutableList()
    for (c in chats.sortedBy { it.startedAt }) {
        val at = out.indexOfFirst { it !is ChatRow.Voice && rowTime(it).isNotBlank() && rowTime(it) > c.startedAt }
        if (at < 0) out += ChatRow.Voice(c) else out.add(at, ChatRow.Voice(c))
    }
    return out
}

/** 一次实时语音通话：标题行（时长）+ 双方说的话，样式和普通消息一致。 */
@Composable
private fun VoiceChatBlock(vm: AppViewModel, c: com.local.agremote.VoiceChatRec) {
    val secs = runCatching {
        (java.time.Instant.parse(c.stoppedAt).toEpochMilli() - java.time.Instant.parse(c.startedAt).toEpochMilli()) / 1000
    }.getOrNull()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(tr("实时语音") + (secs?.let { " · %d:%02d".format(it / 60, it % 60) } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        c.lines.forEach { l ->
            if (l.user) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                    modifier = Modifier.widthIn(max = 320.dp)) {
                    SelectionContainer {
                        Text(l.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else Row(Modifier.fillMaxWidth().padding(end = 24.dp)) {
                Icon(Icons.Rounded.GraphicEq, null, Modifier.padding(top = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                SelectionContainer { Text(l.text, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

private fun groupRows(steps: List<Step>): List<ChatRow> {
    val out = mutableListOf<ChatRow>()
    var seg = mutableListOf<Step>()
    fun flush() {
        if (seg.isEmpty()) return
        val last = seg.last()
        val answer = last.takeIf { it.kind == "assistant" && (it.text.isNotBlank() || it.attachments.isNotEmpty()) }
        val proc = seg.filter { it !== answer || it.thinking.isNotBlank() }
        if (proc.isNotEmpty()) out += ChatRow.Process(proc, answer)
        answer?.let { out += ChatRow.Answer(it) }
        seg = mutableListOf()
    }
    for (st in steps) {
        if (st.waiting != null || st.kind == "user" || st.kind == "error") { flush(); out += ChatRow.Single(st) }
        else seg += st
    }
    flush()
    return out
}

@Composable
private fun ProcessBlock(vm: AppViewModel, chatId: String, r: ChatRow.Process) {
    var open by rememberSaveable(chatId, r.key) { mutableStateOf(false) }
    val tools = r.steps.count { it.kind != "assistant" }
    val running = r.answer == null && r.steps.any { it.status == "running" || it.status == "waiting" }
    val cur = r.steps.lastOrNull { it.status == "running" } ?: r.steps.last()
    val label = when {
        open -> tr("收起思考过程")
        running -> "${tr("思考过程")} · " + (if (cur.kind == "assistant") tr("思考中") else cur.title) + "…"
        tools > 0 -> "${tr("思考过程")} · $tools ${tr("个步骤")}"
        else -> tr("思考过程")
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = !open }.padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 正在思考时用强调色，其余时候是灰色
            val tint = if (running) accent() else MaterialTheme.colorScheme.outline
            Icon(Icons.Rounded.Lightbulb, null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline)
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.steps.forEach { st ->
                    if (st.kind == "assistant") {
                        if (st.thinking.isNotBlank()) ThinkingBody(vm, chatId, st)
                        if (st !== r.answer) AnswerBody(vm, st, copy = false)
                    } else ToolRow(st)
                }
            }
        }
    }
}

@Composable
private fun ThinkingBody(vm: AppViewModel, chatId: String, step: Step) {
    val showZhSet by vm.showZh.collectAsState()
    val showZh = "$chatId:${step.i}" in showZhSet
    val targetLang = LocalAppLang.current
    var translated by remember(step.thinking, targetLang) { mutableStateOf(Translator.cached(step.thinking, targetLang)) }
    var busy by remember(chatId, step.i, targetLang) { mutableStateOf(false) }
    val ctx = LocalContext.current
    LaunchedEffect(showZh, step.thinking, targetLang) {
        if (!showZh) return@LaunchedEffect
        val cached = Translator.cached(step.thinking, targetLang)
        if (cached != null) { translated = cached; return@LaunchedEffect }
        busy = true
        runCatching { Translator.translate(step.thinking, targetLang) }
            .onSuccess { translated = it }
            .onFailure {
                Toast.makeText(ctx, I18n.translateToast("翻译失败：${it.message}"), Toast.LENGTH_SHORT).show()
                vm.setThinkingZh(chatId, step.i, false)
            }
        busy = false
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (showZh) tr("Google 翻译") else tr("原文"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { vm.setThinkingZh(chatId, step.i, !showZh) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Rounded.Translate, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showZh) tr("显示原文") else tr("翻译"), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            SelectionContainer {
                Text(if (showZh) translated ?: step.thinking else step.thinking, Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnswerBody(vm: AppViewModel, step: Step, copy: Boolean = true) {
    val chat by vm.chat.collectAsState()
    val votes by vm.votes.collectAsState()
    val vote = votes["${chat.id ?: ""}:${step.i}"] ?: 0
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (step.attachments.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)) {
                step.attachments.forEach { a -> AttachmentView(vm, a) }
            }
        }
        if (step.text.isNotBlank()) {
            Markdown(step.text, Modifier.fillMaxWidth().padding(horizontal = 2.dp), onLink = { vm.openLink(it) })
            if (copy && step.status != "running") Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = { vm.copy(step.text) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ContentCopy, tr("复制"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                }
                IconButton(
                    onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, step.text)
                        }
                        val chooser = android.content.Intent.createChooser(send, I18n.t("分享"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(chooser) }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.Share, tr("分享"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = { vm.vote(step.i, if (vote == 1) 0 else 1) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (vote == 1) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        "点赞",
                        Modifier.size(18.dp),
                        tint = if (vote == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                IconButton(onClick = { vm.vote(step.i, if (vote == -1) 0 else -1) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (vote == -1) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                        "点踩",
                        Modifier.size(18.dp),
                        tint = if (vote == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    )
                }
                val t = msgTime(step.time)
                if (t.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (step.status == "running" && step.text.isBlank() && step.thinking.isBlank()) {
            Text(tr("思考中…"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun iconFor(step: Step): ImageVector = when {
    step.kind == "file" -> Icons.Rounded.EditNote
    step.type.contains("command") || step.type.contains("terminal") -> Icons.Rounded.Terminal
    step.type.startsWith("view") -> Icons.Rounded.Description
    step.type.contains("directory") -> Icons.Rounded.FolderOpen
    step.type.contains("search") || step.type == "find" -> Icons.Rounded.Search
    step.type.contains("browser") || step.type.contains("url") || step.type.contains("web") -> Icons.Rounded.Language
    else -> Icons.Rounded.Build
}

@Composable
private fun ToolRow(step: Step) {
    var open by rememberSaveable(step.i) { mutableStateOf(false) }
    val hasMore = step.detail.isNotBlank() || step.sub.isNotBlank()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = hasMore) { open = !open }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(iconFor(step), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (step.text.isNotBlank()) Text(
                        shortPath(step.text), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StepStatusIcon(step.status)
            }
            if (open) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(12.dp)) {
                    if (step.sub.isNotBlank()) Text(step.sub, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (step.detail.isNotBlank()) {
                        Spacer(Modifier.size(6.dp))
                        Box(Modifier.heightIn(max = 420.dp).horizontalScroll(rememberScrollState())) {
                            SelectionContainer { CodeText(step.detail, isDiff = step.kind == "file") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeText(text: String, isDiff: Boolean) {
    val add = Color(0xFF2E9E5B); val del = MaterialTheme.colorScheme.error
    val annotated = remember(text, isDiff) {
        buildAnnotatedString {
            text.lines().forEachIndexed { k, line ->
                if (k > 0) append('\n')
                val color = when {
                    !isDiff -> null
                    line.startsWith("+") -> add
                    line.startsWith("-") -> del
                    else -> null
                }
                if (color != null) withStyle(SpanStyle(color = color)) { append(line) } else append(line)
            }
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp))
}

@Composable
private fun StepStatusIcon(status: String) {
    when (status) {
        "running" -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        "waiting" -> Icon(Icons.Rounded.TouchApp, "等待确认", Modifier.size(18.dp), tint = Color(0xFFE08A00))
        "error" -> Icon(Icons.Rounded.ErrorOutline, "出错", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        "canceled" -> Icon(Icons.Rounded.Cancel, "已取消", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        else -> Icon(Icons.Rounded.CheckCircle, "完成", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun shortPath(s: String): String {
    if (s.contains('\n')) return s.lineSequence().first()
    val parts = s.split('/', '\\').filter { it.isNotEmpty() }
    return if ((s.contains('/') || s.contains('\\')) && parts.size > 3 && !s.contains(' ')) "…/" + parts.takeLast(3).joinToString("/") else s
}

@Composable
private fun ErrorCard(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            SelectionContainer { Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** agent 停下来等你确认的操作。 */
@Composable
private fun WaitingCard(vm: AppViewModel, hasWorkspace: Boolean, step: Step) {
    val w = step.waiting ?: return
    var busy by remember(step.i, step.status) { mutableStateOf(false) }
    var denyDialog by remember(step.i) { mutableStateOf(false) }
    var denyReason by remember(step.i) { mutableStateOf("") }
    fun send(accept: Boolean, scope: String? = null, answers: JSONArray? = null, text: String? = null) {
        busy = true
        vm.respond(step, accept, scope = scope, answers = answers, text = text, onError = { busy = false })
    }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TouchApp, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(waitingTitle(w.kind, w.action, step), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            if (w.kind == "permission" && w.label.isNotBlank()) {
                Text(w.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            val body = when (w.kind) {
                "runCommand" -> step.text
                "filePermission" -> w.target.ifBlank { w.label.ifBlank { w.path } }
                "permission" -> w.target.ifBlank { step.text }
                else -> step.text
            }
            if (body.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = RoundedCornerShape(10.dp)) {
                    SelectionContainer {
                        Text(body, Modifier.fillMaxWidth().padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
            if (w.reason.isNotBlank()) {
                Text(w.reason, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
            }
            if (w.kind == "askQuestion") {
                AskQuestionForm(step, busy) { answers -> send(true, answers = answers) }
                OutlinedButton(onClick = { send(false) }, enabled = !busy) { Text(tr("跳过")) }
            } else if (w.kind == "permission" || w.kind == "filePermission") {
                val scopes = if (w.scopes.isNotEmpty()) w.scopes else buildList {
                    add("PERMISSION_SCOPE_ONCE")
                    add("PERMISSION_SCOPE_CONVERSATION")
                    if (hasWorkspace || w.kind == "filePermission") add("PERMISSION_SCOPE_WORKSPACE")
                    if (!w.persistBlocked) add("PERMISSION_SCOPE_GLOBAL")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scopes.forEach { sc ->
                        val label = scopeLabel(sc)
                        if (sc == "PERMISSION_SCOPE_ONCE") {
                            Button(onClick = { send(true, scope = sc) }, enabled = !busy) { Text(label) }
                        } else {
                            FilledTonalButton(onClick = { send(true, scope = sc) }, enabled = !busy) { Text(label) }
                        }
                    }
                    OutlinedButton(onClick = { send(false) }, enabled = !busy) { Text(tr("拒绝")) }
                    if (w.kind == "permission") {
                        TextButton(onClick = { denyDialog = true }, enabled = !busy) { Text(tr("拒绝并说明")) }
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { send(false) }, enabled = !busy) { Text(tr("拒绝")) }
                    Button(onClick = { send(true) }, enabled = !busy) {
                        Text(if (w.kind == "runCommand") tr("运行") else tr("确认"))
                    }
                }
            }
        }
    }

    if (denyDialog) {
        AppAlertDialog(
            onDismissRequest = { denyDialog = false },
            title = { Text(tr("拒绝并告诉 agent 原因")) },
            text = {
                OutlinedTextField(
                    value = denyReason,
                    onValueChange = { denyReason = it },
                    placeholder = { Text(tr("例如：换一种方式，不要修改这个文件")) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            },
            confirmButton = {
                Button(onClick = { denyDialog = false; send(false, text = denyReason.trim()) }) { Text(tr("拒绝")) }
            },
            dismissButton = {
                TextButton(onClick = { denyDialog = false }) { Text(tr("取消")) }
            },
        )
    }
}

private fun scopeLabel(scope: String) = when (scope) {
    "PERMISSION_SCOPE_ONCE" -> "允许一次"
    "PERMISSION_SCOPE_CONVERSATION" -> "本对话允许"
    "PERMISSION_SCOPE_WORKSPACE" -> "本工作区允许"
    "PERMISSION_SCOPE_PROJECT" -> "本项目允许"
    "PERMISSION_SCOPE_GLOBAL" -> "始终允许"
    else -> scope.removePrefix("PERMISSION_SCOPE_").lowercase()
}

private fun waitingTitle(kind: String, action: String, step: Step) = when (kind) {
    "runCommand" -> "agent 想运行命令"
    "permission" -> when (action) {
        "command" -> "agent 想运行命令"
        "read_file" -> "agent 想读取文件"
        "write_file", "edit_file" -> "agent 想修改文件"
        else -> "agent 请求权限"
    }
    "filePermission" -> "agent 想访问文件"
    "askQuestion" -> "agent 有问题问你"
    "openBrowserUrl" -> "agent 想打开网页"
    else -> "等待你确认：${step.title}"
}

@Composable
private fun AskQuestionForm(step: Step, busy: Boolean, onSubmit: (JSONArray) -> Unit) {
    val qs = step.waiting?.questions ?: emptyList()
    val picked = remember(step.i) { mutableStateMapOf<Int, List<String>>() }
    val texts = remember(step.i) { mutableStateListOf<String>().apply { repeat(qs.size) { add("") } } }
    qs.forEachIndexed { qi, q ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q.question, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                q.options.forEach { (id, label) ->
                    val sel = picked[qi]?.contains(id) == true
                    FilterChip(selected = sel, onClick = {
                        val cur = picked[qi] ?: emptyList()
                        picked[qi] = if (q.multi) (if (sel) cur - id else cur + id) else listOf(id)
                    }, label = { Text(label) })
                }
            }
            OutlinedTextField(texts[qi], { texts[qi] = it }, Modifier.fillMaxWidth(), placeholder = { Text(tr("或者自己写…")) }, singleLine = false)
        }
    }
    Button(onClick = {
        val arr = JSONArray()
        qs.forEachIndexed { qi, q ->
            arr.put(JSONObject().put("question", q.question).put("optionIds", JSONArray(picked[qi] ?: emptyList<String>()))
                .put("text", texts.getOrElse(qi) { "" }))
        }
        onSubmit(arr)
    }, enabled = !busy) { Text(tr("提交")) }
}

private fun assistantName(modelLabel: String): String = when {
    modelLabel.contains("gemini", ignoreCase = true) -> "Gemini"
    modelLabel.contains("claude", ignoreCase = true) -> "Claude"
    else -> "Agent"
}

/**
 * 输入栏：没打字时收成一行；点开或有内容时，上面输入、下面是模型按钮和按钮组。
 * 录音时下面一行换成：取消 · 声纹 · 停止 · 发送。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun InputBar(vm: AppViewModel, working: Boolean, id: String?, modelLabel: String, fullLabel: String, onPickModel: () -> Unit) {
    val textState = remember(id) { TextFieldState(initialText = vm.getDraft(id)) }
    val text = textState.text.toString()
    val allDrafts by vm.drafts.collectAsState()
    LaunchedEffect(id, text) { vm.updateDraft(id, text) }
    LaunchedEffect(id, allDrafts) {
        val saved = allDrafts[vm.draftKey(id)].orEmpty()
        if (saved.isNotEmpty() && textState.text.isEmpty()) {
            textState.setTextAndPlaceCursorAtEnd(saved)
        }
    }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ctx = LocalContext.current
    val chatState by vm.chat.collectAsState()
    val sending = chatState.pending != null
    val recording by vm.recording.collectAsState()
    val finalizing by vm.sttFinalizing.collectAsState()
    val sttText by vm.sttText.collectAsState()
    val sttStable by vm.sttStable.collectAsState()
    val levels by vm.levels.collectAsState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // isImeVisible 在键盘开始收起的第一帧就会立刻变 false（不等键盘完全退完），
    // 让收起动画和键盘下滑同步执行，不再等键盘退完才卡顿一下再收起。
    val imeVisible = WindowInsets.isImeVisible

    var holdMode by remember { mutableStateOf(false) }
    var holdCancel by remember { mutableStateOf(false) }
    var holdImeAtStart by remember { mutableStateOf(false) }
    var surfacePos by remember { mutableStateOf(Offset.Zero) }
    var micBounds by remember { mutableStateOf(Rect.Zero) }
    var extraMicBounds by remember { mutableStateOf(Rect.Zero) }
    var inputBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(recording) {
        if (!recording) { holdMode = false; holdCancel = false }
    }

    fun setText(t: String) { textState.setTextAndPlaceCursorAtEnd(t) } // 光标放到末尾
    val receiveContent = remember(ctx) {
        ReceiveContentListener { transferable ->
            val cd = transferable.clipEntry.clipData
            var handled = false
            for (i in 0 until cd.itemCount) {
                val uri = cd.getItemAt(i).uri ?: continue
                val mime = ctx.contentResolver.getType(uri)
                    ?: (0 until cd.description.mimeTypeCount).map { cd.description.getMimeType(it) }
                        .firstOrNull { it.startsWith("image/") || it.startsWith("video/") }
                    ?: if (transferable.hasMediaType(MediaType.Image)) "image/png" else ""
                if (mime.startsWith("image/") || mime.startsWith("video/")) {
                    // 立刻读取字节，防止输入法临时 URI 权限在回调返回后失效
                    val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "pasted_${System.currentTimeMillis()}.png"
                        vm.addPastedBytes(name, mime, bytes)
                        handled = true
                    } else {
                        vm.addFromUris(listOf(uri))
                        handled = true
                    }
                }
            }
            if (handled) transferable.consume { it.uri != null } else transferable
        }
    }
    val atts by vm.attachments.collectAsState()
    val mentions by vm.mentions.collectAsState()
    var plus by remember { mutableStateOf(false) }
    fun picked(uris: List<android.net.Uri>) { if (uris.isNotEmpty()) { plus = false; vm.addFromUris(uris) } }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { picked(listOf(android.net.Uri.parse(it))) }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { picked(it) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked(it) }
    fun sendNow(t: String) {
        if (t.isBlank() && atts.isEmpty()) return
        setText("")
        vm.clearDraft(id)
        vm.send(t.trim().ifBlank { "请看附件。" }) { }
    }
    // 开始录音时输入框里已有的文字；识别结果接在它后面
    var base by remember { mutableStateOf("") }
    fun join(said: String) = if (base.isBlank()) said else base.trimEnd() + " " + said

    LaunchedEffect(id) {
        vm.sttResult.collect { (said, send) ->
            // 停止时已经先填了临时结果，这里用最终结果替换
            if (send) sendNow(join(said)) else setText(join(said))
        }
    }
    val pendingDraft by vm.pendingDraft.collectAsState()
    LaunchedEffect(id, pendingDraft) {
        val draft = pendingDraft ?: return@LaunchedEffect
        vm.clearPendingDraft()
        val curText = textState.text.toString()
        setText(if (curText.isBlank()) draft else curText.trimEnd() + "\n" + draft)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    fun stopKeep() {
        // 先把已经识别出的部分放进输入框，不让它“消失”，最终结果回来再替换
        if (sttText.isNotBlank()) setText(join(sttText))
        vm.stopVoice("keep")
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startVoice() else Toast.makeText(ctx, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
    }
    fun startVoice() {
        base = textState.text.toString()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startVoice()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    val voiceActive = recording || finalizing
    val expanded = imeVisible || sending || working || text.isNotEmpty() || voiceActive || atts.isNotEmpty() || mentions.isNotEmpty()
    var prevIme by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (prevIme && !imeVisible && focused && text.isEmpty() && !voiceActive && atts.isEmpty() && mentions.isEmpty()) {
            focusManager.clearFocus()
        }
        prevIme = imeVisible
    }
    val hasContent = text.isNotBlank() || atts.isNotEmpty()
    val canStop = id != null && working && !hasContent
    // 实时语音入口（电脑上 Antigravity 2.35+ 才有）：输入框为空时，在听写麦克风旁边
    val voiceCfg by vm.voiceCfg.collectAsState()
    val showVoiceMode = voiceCfg.available && !hasContent && !canStop
    val askVoiceMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startVoiceCall() else Toast.makeText(ctx, I18n.t("需要麦克风权限才能语音通话"), Toast.LENGTH_SHORT).show()
    }
    fun startVoiceMode() {
        focusManager.clearFocus()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startVoiceCall()
        else askVoiceMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    val curVoiceActive by rememberUpdatedState(voiceActive)
    val curExpanded by rememberUpdatedState(expanded)
    val curId by rememberUpdatedState(id)
    val curHasContent by rememberUpdatedState(hasContent)
    val curCanStop by rememberUpdatedState(canStop)
    val curImeVisible by rememberUpdatedState(imeVisible)
    val curStartVoice by rememberUpdatedState(::startVoice)
    val curStopKeep by rememberUpdatedState(::stopKeep)

    Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 4.dp)) {
        // 输入框：卡片底色 + 细边框 + 柔和阴影（各主题风格统一，只有颜色跟随主题）
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .onGloballyPositioned { surfacePos = it.positionInRoot() }
                .pointerInput(Unit) {
                    val stopDragPx = with(density) { 80.dp.toPx() }
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (curVoiceActive) return@awaitEachGesture
                        val rootDown = surfacePos + down.position
                        val primaryIsMic = !curHasContent && !curCanStop
                        val onMic = (primaryIsMic && micBounds.contains(rootDown)) ||
                            ((curHasContent || curCanStop) && extraMicBounds.contains(rootDown))
                        val onCollapsedInput = !curExpanded && !curImeVisible && inputBounds.contains(rootDown)
                        if (!onMic && !onCollapsedInput) return@awaitEachGesture

                        val downX = down.position.x
                        val downY = down.position.y
                        val holdTimeout = if (onMic) 240L else 320L
                        val exitedEarly = withTimeoutOrNull(holdTimeout) {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                if (!ch.pressed) return@withTimeoutOrNull true
                                val dx = ch.position.x - downX
                                val dy = ch.position.y - downY
                                if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop * 2) return@withTimeoutOrNull true
                            }
                        }
                        if (exitedEarly != null) return@awaitEachGesture

                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            curStartVoice()
                            return@awaitEachGesture
                        }

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val imeAtStart = curImeVisible
                        holdImeAtStart = imeAtStart
                        holdMode = true
                        holdCancel = false
                        curStartVoice()

                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null || !ch.pressed) {
                                ch?.consume()
                                break
                            }
                            ch.consume()
                            val shouldCancel = if (onMic) (downX - ch.position.x) > stopDragPx else (ch.position.x < size.width * 0.35f)
                            if (shouldCancel != holdCancel) {
                                holdCancel = shouldCancel
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        val wasCancel = holdCancel
                        holdMode = false
                        holdCancel = false
                        when {
                            !wasCancel -> vm.stopVoice("send")
                            imeAtStart -> curStopKeep()
                            else -> vm.stopVoice("cancel")
                        }
                    }
                },
        ) {
            Column(Modifier.padding(8.dp)) {
                val primary = @Composable {
                    FilledIconButton(
                        onClick = {
                            when {
                                hasContent -> sendNow(text)
                                canStop -> vm.cancel()
                                else -> startVoice()
                            }
                        },
                        modifier = Modifier.size(44.dp).onGloballyPositioned { micBounds = it.boundsInRoot() },
                        colors = when {
                            hasContent -> IconButtonDefaults.filledIconButtonColors(containerColor = accent(), contentColor = onAccent()) // 发送：强调色
                            canStop -> IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            else -> IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        },
                    ) {
                        Icon(
                            when { hasContent -> Icons.Rounded.ArrowUpward; canStop -> Icons.Rounded.Stop; else -> Icons.Rounded.Mic },
                            when { hasContent -> "发送"; canStop -> "停止 agent"; else -> "语音输入" },
                        )
                    }
                }

                val plusBtn = @Composable {
                    // “+”有一个浅色圆底（和麦克风一致）
                    FilledTonalIconButton(onClick = { plus = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Add, "添加到会话")
                    }
                }
                if (mentions.isNotEmpty() && !voiceActive) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, top = 4.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        mentions.forEach { c -> MentionChip(vm, c.name, c.logo) { vm.removeMention(c) } }
                    }
                }
                if (atts.isNotEmpty() && !voiceActive) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, top = 4.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        atts.forEach { a ->
                            AttachmentChip(a, onOpen = { vm.openPreview(localPreview(a)) }) { vm.removeAttachment(a) }
                        }
                    }
                }
                // 输入区：展开/收起进度直接与系统键盘实时高度 1:1 绑定（怎么来的就怎么回去，120Hz 同步无卡顿），
                // 发送中/有文字/录音时由 stayProgress 平滑接管，且「+」和主按钮始终固定不闪烁。
                val imePx = WindowInsets.ime.getBottom(density)
                var peakIme by remember { mutableIntStateOf(0) }
                if (imePx > peakIme) peakIme = imePx
                val stayExpanded = sending || working || text.isNotEmpty() || voiceActive || atts.isNotEmpty() || mentions.isNotEmpty()
                val imeFrac = if (peakIme > 0) (imePx.toFloat() / peakIme).coerceIn(0f, 1f) else if (imeVisible) 1f else 0f
                val stayProgress by animateFloatAsState(
                    targetValue = if (stayExpanded) 1f else 0f,
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    label = "stayMorph",
                )
                val progress = maxOf(imeFrac, stayProgress)
                val showTopField = !holdMode || text.isNotBlank() || sttText.isNotBlank() || holdImeAtStart
                if (!voiceActive) {
                    val endCollapsed = if (canStop || showVoiceMode) 104.dp else 52.dp
                    val startPad = lerp(48.dp, 6.dp, progress)
                    val endPad = lerp(endCollapsed, 6.dp, progress)
                    val topPad = lerp(0.dp, 2.dp, progress)
                    val bottomPad = lerp(0.dp, 50.dp, progress)
                    val fieldMinH = lerp(44.dp, 32.dp, progress)
                    var rightGroupW by remember { mutableIntStateOf(0) }
                    Box(Modifier.fillMaxWidth()) {
                        // 文本框：收起时夹在左右按钮之间，展开时平滑升到上方
                        Box(
                            Modifier.fillMaxWidth()
                                .padding(start = startPad, end = endPad, top = topPad, bottom = bottomPad)
                                .heightIn(min = fieldMinH, max = 160.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                state = textState,
                                modifier = Modifier.fillMaxWidth()
                                    .contentReceiver(receiveContent)
                                    .onGloballyPositioned { inputBounds = it.boundsInRoot() }
                                    .focusRequester(focus).onFocusChanged { focused = it.isFocused },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorator = { inner ->
                                    Box {
                                        if (text.isEmpty()) {
                                            val ai = assistantName(fullLabel)
                                            val phStyle = MaterialTheme.typography.bodyLarge
                                            val phColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            val fixed = when { sending -> tr("正在发送…"); working -> tr("排队发送消息"); else -> null } // 运行中发的消息会排队
                                            if (fixed != null) Text(fixed, style = phStyle, color = phColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            else {
                                                val collapsedPh = if (id == null) tr("输入或长按说话") else cjkSpace(tr("回复{0}或长按说话", ai))
                                                val expandedPh = if (id == null) cjkSpace(tr("与{0}对话", ai)) else cjkSpace(tr("回复{0}", ai))
                                                val k = (progress / 0.35f).coerceIn(0f, 1f)
                                                if (k < 1f) Text(collapsedPh, Modifier.graphicsLayer { alpha = 1f - k }, style = phStyle, color = phColor,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (k > 0f) Text(expandedPh, Modifier.graphicsLayer { alpha = k }, style = phStyle, color = phColor,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        // 唯一「+」按钮 + 模型选择按钮：同一基线垂直居中
                        // 左组最多用到右侧按钮组左边，名字太长就省略，不会钻到麦克风底下
                        Row(Modifier.align(Alignment.BottomStart).padding(end = with(LocalDensity.current) { rightGroupW.toDp() } + 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.zIndex(1f)) { plusBtn() }
                            if (progress > 0.02f) {
                                Spacer(Modifier.width(8.dp)) // “+”的圆底和模型按钮隔开
                                val chipK = ((progress - 0.1f) / 0.9f).coerceIn(0f, 1f)
                                Surface(
                                    onClick = { if (expanded) onPickModel() },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f, fill = false).graphicsLayer {
                                        translationX = -(1f - chipK) * 60.dp.toPx() // 从“+”底下滑出来
                                        alpha = chipK
                                    },
                                ) {
                                    Box(Modifier.height(44.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                        Text(modelLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        // 唯一右侧操作组（麦克风/发送/停止）：始终固定在右下角
                        Row(Modifier.align(Alignment.BottomEnd).onSizeChanged { rightGroupW = it.width }, verticalAlignment = Alignment.CenterVertically) {
                            if (hasContent || canStop) {
                                FilledTonalIconButton(onClick = { startVoice() },
                                    modifier = Modifier.size(44.dp).onGloballyPositioned { extraMicBounds = it.boundsInRoot() }) {
                                    Icon(Icons.Rounded.Mic, if (hasContent) "继续语音输入" else "语音输入")
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            primary()
                            if (showVoiceMode) {
                                Spacer(Modifier.width(8.dp))
                                FilledIconButton(onClick = { startVoiceMode() }, modifier = Modifier.size(44.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
                                    Icon(Icons.Rounded.GraphicEq, tr("实时语音"))
                                }
                            }
                        }
                    }
                } else {
                    if (showTopField) {
                        Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 24.dp, max = 160.dp)) {
                            if (text.isNotBlank() || sttText.isNotBlank()) {
                                Text(buildAnnotatedString {
                                    if (text.isNotBlank()) append(text.trimEnd() + " ")
                                    // 已确认的字是正常颜色，还在识别（可能会变）的部分是灰色
                                    val stable = sttStable.takeIf { it.isNotBlank() && sttText.startsWith(it) } ?: ""
                                    append(stable)
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                        append(sttText.removePrefix(stable).ifBlank { if (stable.isEmpty()) "…" else "" })
                                    }
                                }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            } else if (!holdMode) {
                                Text(tr("正在识别，请说话…"), style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val ai = assistantName(fullLabel)
                                Text(if (id == null) "与${ai}对话" else "回复${ai}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (finalizing) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.size(44.dp))
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(tr("正在转录…"), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalIconButton(onClick = {}, enabled = false, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.ArrowUpward, "正在转录")
                            }
                        }
                    } else if (recording && holdMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val leftIsCancel = !holdImeAtStart && holdCancel
                            val leftColors = when {
                                leftIsCancel -> IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError)
                                holdCancel -> IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary)
                                else -> IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledIconButton(onClick = {}, modifier = Modifier.size(44.dp), colors = leftColors) {
                                Icon(if (leftIsCancel) Icons.Rounded.Close else Icons.Rounded.Stop,
                                    if (leftIsCancel) "松开以取消" else "松开即停止")
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when {
                                    leftIsCancel -> "松开以取消"
                                    holdCancel -> "松开即停止"
                                    else -> "松开以发送"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (leftIsCancel) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Waveform(levels, Modifier.weight(1f).height(36.dp).padding(horizontal = 10.dp))
                            val rightColors = if (!holdCancel) IconButtonDefaults.filledIconButtonColors()
                            else IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledIconButton(onClick = {}, modifier = Modifier.size(44.dp), colors = rightColors) {
                                Icon(Icons.Rounded.ArrowUpward, "松开以发送")
                            }
                        }
                    } else if (recording) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(onClick = { vm.stopVoice("cancel") }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Close, "取消")
                            }
                            Waveform(levels, Modifier.weight(1f).height(36.dp).padding(horizontal = 12.dp))
                            FilledTonalIconButton(onClick = { stopKeep() }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Stop, "停止识别")
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(onClick = { vm.stopVoice("send") }, modifier = Modifier.size(44.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent(), contentColor = onAccent())) {
                                Icon(Icons.Rounded.ArrowUpward, "识别完直接发送")
                            }
                        }
                    }
                }
            }
        }
    }

    if (plus) PlusSheet(vm,
        onCamera = { val u = com.local.agremote.Attachments.cameraUri(ctx); cameraUri = u.toString(); cameraLauncher.launch(u) },
        onPhotos = { photoLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onFiles = { fileLauncher.launch(arrayOf("*/*")) },
        onInsert = { t -> val cur = textState.text.toString(); setText(if (cur.isBlank()) t else cur.trimEnd() + "\n" + t) }, onClose = { plus = false })
}

/** 输入框上方的附件预览：图片和视频显示缩略图，其他文件显示文件名；点开可预览。 */
@Composable
private fun AttachmentChip(a: com.local.agremote.Attachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    Box {
        if (a.thumb != null) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                Image(a.thumb.asImageBitmap(), a.name, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                if (a.mime.startsWith("video/")) VideoPlayBadge(size = 26)
            }
        } else {
            Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Description, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(a.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp))
                        Text("${(a.size + 1023) / 1024} KB", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(18.dp))
                }
            }
        }
        Surface(onClick = onRemove, shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp)) {
            Icon(Icons.Rounded.Close, "移除", Modifier.padding(3.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

/** 汉字和英文/数字之间补一个空格（“与Gemini对话” → “与 Gemini 对话”）。 */
private fun cjkSpace(s: String): String = s
    .replace(Regex("([\u4e00-\u9fff\u3040-\u30ff])([A-Za-z0-9])"), "$1 $2")
    .replace(Regex("([A-Za-z0-9])([\u4e00-\u9fff\u3040-\u30ff])"), "$1 $2")

/** 额度圆环：圈的长度是这 5 小时已用掉的比例；用得少是蓝色，过 60% 变橙黄，过 85% 变红。 */
@Composable
private fun QuotaRing(remaining: Double) {
    val used = ((1.0 - remaining) * 100).toInt().coerceIn(0, 100)
    val color = when {
        used >= 85 -> MaterialTheme.colorScheme.error
        used >= 60 -> Color(0xFFF0A030)
        else -> Color(0xFF3B82F6) // 用得少：蓝色
    }
    CircularProgressIndicator(progress = { used / 100f }, modifier = Modifier.size(20.dp), color = color,
        trackColor = color.copy(alpha = 0.2f), strokeWidth = 2.5.dp, strokeCap = StrokeCap.Round, gapSize = 0.dp)
}

/** 声纹：最近一段时间的音量柱，新的在右边。 */
@Composable
private fun Waveform(levels: List<Float>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    val idle = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val bars = 40
        val gap = size.width / bars
        val w = (gap * 0.45f).coerceAtLeast(2f)
        val data = levels.takeLast(bars)
        for (k in 0 until bars) {
            val idx = k - (bars - data.size)
            val v = if (idx >= 0) data[idx] else 0f
            val h = (size.height * v).coerceAtLeast(w)
            val x = k * gap + gap / 2
            drawLine(if (idx >= 0) color else idle, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2),
                strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

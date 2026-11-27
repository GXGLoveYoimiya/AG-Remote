package com.local.agremote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.local.agremote.AppViewModel
import com.local.agremote.Customization
import com.local.agremote.MarketItem
import com.local.agremote.Translator

private fun scopeName(s: String) = when (s) {
    "builtin" -> "内置"; "global" -> "全局"; "workspace" -> "工作区"; "project" -> "项目"; "plugin" -> "插件"
    else -> s
}

/** 有官方中文就用官方中文，没有就用 fallback（再没有就原文）。 */
private fun AppViewModel.t(en: String, fallback: String = en) = zh(en).let { if (it == en) fallback else it }

/** 判断一段文本是否主要为外文、需要调用电脑网络谷歌翻译。 */
fun needsGoogleTranslate(s: String): Boolean {
    if (s.isBlank()) return false
    var latin = 0
    var cjk = 0
    for (ch in s) {
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            ch in '\u4E00'..'\u9FFF' -> cjk++
        }
    }
    return latin >= 4 && cjk * 3 < latin
}

/**
 * 走电脑网络谷歌翻译的可复用状态：自动翻译为当前 App 语言，按 (语言, 原文) 缓存。
 * 返回 Pair(当前展示文本, 是否正在翻译)。
 */
@Composable
fun rememberGoogleTranslated(raw: String, enabled: Boolean): Pair<String, Boolean> {
    val targetLang = LocalAppLang.current
    val shouldTranslate = enabled && raw.isNotBlank() && (if (targetLang == "en") !needsGoogleTranslate(raw) else needsGoogleTranslate(raw))
    var translated by remember(raw, targetLang) { mutableStateOf(Translator.cached(raw, targetLang)) }
    var loading by remember(raw, targetLang) { mutableStateOf(false) }
    LaunchedEffect(raw, shouldTranslate, targetLang) {
        if (!shouldTranslate) return@LaunchedEffect
        val hit = Translator.cached(raw, targetLang)
        if (hit != null) { translated = hit; return@LaunchedEffect }
        loading = true
        val res = runCatching { Translator.translate(raw, targetLang) }.getOrNull()
        if (!res.isNullOrBlank()) translated = res
        loading = false
    }
    val shown = if (shouldTranslate && !translated.isNullOrBlank()) translated!! else raw
    return shown to (shouldTranslate && loading && translated == null)
}

/** 自定义扩展：和电脑上一样，分“插件市场”和“已安装”；支持整页/单项谷歌翻译（走电脑网络）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var q by rememberSaveable { mutableStateOf("") }
    var translateOn by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MarketItem?>(null) }
    var mcpDetail by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.loadMarket(); vm.loadCustomizations(); vm.loadExtras() }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        AppTopBar(tr("自定义"), onBack = onBack) {
                TextButton(onClick = { translateOn = !translateOn }) {
                    Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp),
                        tint = if (translateOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(if (translateOn) tr("显示原文") else tr("翻译"), maxLines = 1, softWrap = false,
                        color = if (translateOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf(tr("插件市场"), tr("已安装")).forEachIndexed { i, l ->
                    SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(l, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            OutlinedTextField(q, { q = it; if (tab == 0) vm.loadMarket(it) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(28.dp),
                placeholder = { Text(tr("搜索")) },
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh))
            Box(Modifier.weight(1f)) {
                if (tab == 0) MarketTab(vm, q, translateOn) { detail = it }
                else InstalledTab(vm, q, translateOn, onToggleTranslate = { translateOn = !translateOn }, onOpenMcp = { mcpDetail = it }) { detail = it }
            }
        }
    }
    detail?.let { d -> PluginDetail(vm, d, initialTranslate = translateOn, onStartChat = onBack) { detail = null } }
    mcpDetail?.let { name -> McpDetail(vm, name) { mcpDetail = null } }
}

@Composable
private fun MarketTab(vm: AppViewModel, q: String, translateOn: Boolean, onOpen: (MarketItem) -> Unit) {
    val m by vm.market.collectAsState()
    val market = m
    if (market == null) { Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }; return }
    val byId = remember(market) { market.items.associateBy { it.id } }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (q.isNotBlank()) {
            if (market.items.isEmpty()) item { Hint(vm.t("No customizations match your search.", "没有匹配的插件。")) }
            if (market.items.isNotEmpty()) item(key = "search") { PluginGroup(vm, market.items, market.installed, translateOn, onOpen) }
            return@LazyColumn
        }
        // 推荐位
        val feat = market.featured.mapNotNull { byId[it] }
        if (feat.isNotEmpty()) item(key = "featured") { FeaturedCard(vm, feat, market.installed, translateOn, onOpen) }
        // 按分类
        market.groups.forEach { g ->
            val members = g.members.mapNotNull { byId[it] }
            if (members.isEmpty()) return@forEach
            item(key = "g_" + g.id) {
                val (gName, _) = rememberGoogleTranslated(vm.zh(g.name), translateOn)
                val (gDesc, _) = rememberGoogleTranslated(vm.zh(g.description), translateOn)
                Column(Modifier.padding(start = 6.dp, top = 18.dp, bottom = 4.dp)) {
                    Text(gName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (gDesc.isNotBlank()) Text(gDesc, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val open = expanded[g.id] == true
            val shown = if (open || members.size <= 6) members else members.take(6)
            item(key = "m_" + g.id) { PluginGroup(vm, shown, market.installed, translateOn, onOpen) }
            if (members.size > 6) item(key = "more_" + g.id) {
                TextButton(onClick = { expanded[g.id] = !open }) { Text(if (open) tr("收起") else tr("展开更多（{0}）", members.size - 6)) }
            }
        }
    }
}

@Composable
private fun FeaturedCard(vm: AppViewModel, feat: List<MarketItem>, installed: Set<String>, translateOn: Boolean, onOpen: (MarketItem) -> Unit) {
    val main = feat.firstOrNull { it.name.equals("Context7", true) } ?: feat.first()
    var localTr by remember(main.id, translateOn) { mutableStateOf(translateOn) }
    val baseDesc = vm.zh(main.description)
    val (desc, translating) = rememberGoogleTranslated(baseDesc, localTr)
    Surface(color = sheetCardColor(), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            // 上半部分：几个推荐插件的图标
            Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    feat.take(5).forEach { f ->
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                            Box(Modifier.padding(8.dp)) { Logo(vm, f.logo, 36.dp) }
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text(tr("推荐"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(main.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (main.id in installed) OutlinedButton(onClick = { onOpen(main) }) { Text(tr("已添加")) }
                    else Button(onClick = { vm.marketAction(main.id, "install") }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(tr("添加"))
                    }
                    OutlinedButton(onClick = { onOpen(main) }) { Text(tr("详情")) }
                    if (needsGoogleTranslate(baseDesc)) {
                        TextButton(onClick = { localTr = !localTr }) {
                            if (translating) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Translate, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (localTr) tr("原文") else tr("翻译"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginRow(vm: AppViewModel, p: MarketItem, installed: Set<String>, translateOn: Boolean, onOpen: (MarketItem) -> Unit) {
    val busy by vm.installing.collectAsState()
    val (desc, _) = rememberGoogleTranslated(vm.zh(p.description), translateOn)
    Row(Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Logo(vm, p.logo, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(vm.zh(p.name), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        when {
            p.id in busy -> CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
            p.id in installed -> Icon(Icons.Rounded.Check, "已添加", tint = iconInk())
            else -> IconButton(onClick = { vm.marketAction(p.id, "install") }) { Icon(Icons.Rounded.Add, "添加") }
        }
    }
}

/** 一组插件：同一张卡片，行之间细分隔线。 */
@Composable
private fun PluginGroup(vm: AppViewModel, list: List<MarketItem>, installed: Set<String>, translateOn: Boolean, onOpen: (MarketItem) -> Unit) {
    CardGroup {
        list.forEachIndexed { i, p ->
            if (i > 0) CardDivider(indent = false)
            PluginRow(vm, p, installed, translateOn, onOpen)
        }
    }
}

@Composable
private fun Logo(vm: AppViewModel, url: String, size: Dp) {
    val bmp by produceState<android.graphics.Bitmap?>(null, url) { value = vm.logoBitmap(url) }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    else Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(size / 4), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Extension, null, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.outline) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginDetail(vm: AppViewModel, p: MarketItem, initialTranslate: Boolean, onStartChat: () -> Unit = {}, onClose: () -> Unit) {
    val m by vm.market.collectAsState()
    val busy by vm.installing.collectAsState()
    val installed = m?.installed?.contains(p.id) == true
    var confirmRemove by remember { mutableStateOf(false) }
    var localTr by remember(p.id) { mutableStateOf(initialTranslate) }
    val baseDesc = vm.zh(p.description)
    val (desc, translating) = rememberGoogleTranslated(baseDesc, localTr)

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(vm, p.logo, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.zh(p.name), style = MaterialTheme.typography.titleLarge)
                    Text(listOfNotNull(p.author.ifBlank { null }, p.version.ifBlank { null }?.let { "v$it" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (needsGoogleTranslate(baseDesc)) {
                    TextButton(onClick = { localTr = !localTr }) {
                        if (translating) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (localTr) tr("原文") else tr("翻译"))
                        }
                    }
                }
            }
            Text(desc, style = MaterialTheme.typography.bodyMedium)
            // 已安装的插件：启用开关、账号状态（登录只能在电脑上做）、自带技能、推荐提示词
            val conns by vm.connectors.collectAsState()
            LaunchedEffect(p.id, installed) { if (installed) vm.loadConnectors() }
            val conn = conns?.find { it.kind == "plugin" && it.key == p.id.substringAfterLast('/') }
            if (installed && conn != null) {
                CardGroup {
                    ListRow(tr("启用"), end = RowEnd.Toggle(conn.enabled) { vm.toggleConnector(conn, it) })
                    if (conn.connected != null || conn.needsAuth) {
                        CardDivider(indent = false)
                        ListRow(tr("账号"), end = RowEnd.None,
                            value = when { conn.connected == true -> conn.account.ifBlank { tr("已连接") }; else -> tr("未登录") },
                            desc = if (conn.connected != true) tr("登录需要在电脑上的 Antigravity 里完成") else null)
                    }
                }
                if (conn.skills.isNotEmpty()) {
                    SectionLabel(tr("包含的技能"))
                    CardGroup {
                        conn.skills.forEachIndexed { i, s ->
                            if (i > 0) CardDivider(indent = false)
                            ListRow(s, end = RowEnd.None)
                        }
                    }
                }
                if (conn.prompts.isNotEmpty()) {
                    SectionLabel(tr("试试这样问"))
                    CardGroup {
                        conn.prompts.forEachIndexed { i, s ->
                            if (i > 0) CardDivider(indent = false)
                            ListRow(s) { onClose(); vm.startWithPrompt(s); onStartChat() }
                        }
                    }
                }
            }
            if (p.homepage.isNotBlank()) {
                TextButton(onClick = { onClose(); vm.openBrowser(p.homepage) }) {
                    Text(p.homepage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                p.id in busy -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                installed -> OutlinedButton(onClick = { confirmRemove = true }, Modifier.fillMaxWidth()) { Text(tr("移除")) }
                else -> Button(onClick = { vm.marketAction(p.id, "install") }, Modifier.fillMaxWidth()) { Text(tr("添加")) }
            }
            Text(tr("添加后会装到电脑上的 Antigravity 里。有些插件需要在电脑上登录对应账号后才能用。"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmRemove) com.local.agremote.ui.AppAlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("${tr("移除")}「${p.name}」？") },
        text = { Text(p.name) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; vm.marketAction(p.id, "uninstall") }) { Text(tr("移除"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("取消")) } },
    )
}

/** 已安装：插件 + 技能与规则 + MCP服务。 */
@Composable
private fun InstalledTab(
    vm: AppViewModel,
    q: String,
    translateOn: Boolean,
    onToggleTranslate: () -> Unit,
    onOpenMcp: (String) -> Unit = {},
    onOpen: (MarketItem) -> Unit,
) {
    val m by vm.market.collectAsState()
    val items by vm.customizations.collectAsState()
    val skills by vm.skills.collectAsState()
    val mcp by vm.mcp.collectAsState()
    fun match(s: String) = q.isBlank() || s.contains(q, true)
    val plugins = m?.let { mk -> mk.items.filter { it.id in mk.installed && match(it.name) } } ?: emptyList()
    val sr = (items ?: emptyList()).filter { (it.type == "skill" || it.type == "rule" || it.type == "workflow") && match(it.name) }
    val servers = (mcp ?: emptyList()).filter { match(it.name) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("${tr("插件")}（${plugins.size}）") }
        if (plugins.isEmpty()) item { Hint(tr("未安装")) }
        if (plugins.isNotEmpty()) item(key = "plugins") { PluginGroup(vm, plugins, m?.installed ?: emptySet(), translateOn, onOpen) }

        item {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("${tr("技能与规则")}（${sr.size}）") }
                if (sr.isNotEmpty()) {
                    TextButton(onClick = onToggleTranslate) {
                        Icon(Icons.Rounded.Translate, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (translateOn) tr("显示原文") else tr("翻译"), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (sr.isEmpty()) item { Hint(tr("暂无技能或规则。")) }
        if (sr.isNotEmpty()) item {
            CardGroup {
                sr.forEachIndexed { k, c ->
                    if (k > 0) CardDivider(indent = false)
                    CustomizationRow(c, skills.find { it.name == c.name }?.description?.let { vm.zh(it) } ?: "", translateOn)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)); SectionTitle(tr("MCP服务") + "（${servers.size}）") }
        if (servers.isEmpty()) item { Hint(tr("电脑端暂未配置 MCP 服务。")) }
        if (servers.isNotEmpty()) item(key = "servers") { CardGroup { servers.forEachIndexed { i, s ->
            if (i > 0) CardDivider(indent = false)
            run {
                Row(Modifier.fillMaxWidth().clickable { onOpenMcp(s.name) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(s.status.ifBlank { null }, if (s.tools > 0) "${s.tools} 个工具" else null).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (s.error.isNotBlank()) Text(s.error, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error)
                    }
                    AppSwitch(checked = s.enabled, onCheckedChange = { vm.toggleMcp(s.name, it) })
                }
            }
        } } }
    }
}

/** MCP 服务详情：整体开关 + 每个工具的开关（和电脑上一样写进 mcp_config.json）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McpDetail(vm: AppViewModel, name: String, onClose: () -> Unit) {
    val mcp by vm.mcp.collectAsState()
    val s = mcp?.find { it.name == name } ?: return
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (s.error.isNotBlank()) CardBox { Text(s.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            CardGroup { ListRow(tr("启用"), desc = s.status.lowercase().replace('_', ' '), end = RowEnd.Toggle(s.enabled) { vm.toggleMcp(s.name, it) }) }
            if (s.toolList.isNotEmpty()) {
                SectionLabel(tr("工具") + " · ${s.toolList.count { it.enabled }}/${s.toolList.size}")
                CardGroup {
                    s.toolList.forEachIndexed { i, tl ->
                        if (i > 0) CardDivider(indent = false)
                        ListRow(tl.name, desc = tl.description.lineSequence().firstOrNull()?.take(120),
                            end = RowEnd.Toggle(tl.enabled) { vm.toggleMcpTool(s.name, tl.name, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    SectionLabel(t)
}

@Composable
private fun Hint(t: String) {
    Text(t, Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CustomizationRow(c: Customization, rawDesc: String, globalTranslate: Boolean) {
    var localTr by remember(c.name, globalTranslate) { mutableStateOf(globalTranslate) }
    var expanded by remember(c.name) { mutableStateOf(false) }
    val (desc, translating) = rememberGoogleTranslated(rawDesc, localTr)
    val canTr = needsGoogleTranslate(rawDesc)
    Column(
        Modifier.fillMaxWidth().clickable { if (rawDesc.isNotBlank()) expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(listOf(scopeName(c.scope), if (c.status == "enabled") "已启用" else c.status).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (c.status == "enabled") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (canTr) {
                Spacer(Modifier.width(6.dp))
                if (translating) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp)
                } else {
                    Text(
                        if (localTr) "原文" else "译",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentText(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { localTr = !localTr }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) 20 else 3,
            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (c.error.isNotBlank()) Text(c.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

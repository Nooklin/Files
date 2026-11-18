package org.nooklin.files.ui.settings

import org.nooklin.files.ui.theme.AppText
import androidx.compose.foundation.layout.Arrangement
import org.nooklin.files.ui.NcLink
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import org.nooklin.files.ui.common.NooklinTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.files.R
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.common.GlassPill
import org.nooklin.files.ui.common.NextcloudTile
import org.nooklin.files.ui.common.TileColors
import org.nooklin.files.ui.files.ConfirmDialog

/** Connect to a Nextcloud server with an app password, or disconnect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNextcloudScreen(vm: FilesViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val nc = vm.nc
    val account by nc.account.collectAsStateWithLifecycle()
    val connect by nc.connect.collectAsStateWithLifecycle()
    val allowHttp by nc.allowHttp.collectAsStateWithLifecycle()
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    // The password is not kept when the screen is saved and restored
    var password by remember { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }

    val status by nc.status.collectAsStateWithLifecycle()
    LaunchedEffect(account) {
        if (account != null) {
            password = ""
            nc.clearConnectError()
            nc.checkConnection()
        } else {
            nc.resetStatus()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_nextcloud), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            val current = account
            StatusCard(
                account = current?.let { it.user + "@" + hostOf(it.server) },
                link = if (current == null) (if (connect.testing) NcLink.CHECKING else NcLink.UNKNOWN) else status.link,
                error = status.error,
                connected = current != null,
                onRetry = { nc.checkConnection() }
            )
            Spacer(Modifier.height(8.dp))
            if (current != null) {
                SettingsGroup(
                    null,
                    listOf(
                        { ValueRow(stringResource(R.string.nc_server_label), current.server) },
                        { ValueRow(stringResource(R.string.nc_user_label), current.user) }
                    )
                )
                FilledTonalButton(
                    onClick = { confirmDisconnect = true },
                    border = BorderStroke(1.5.dp, GlassPill.destructiveText),
                    colors = ButtonDefaults.filledTonalButtonColors(contentColor = GlassPill.destructiveText),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.nc_disconnect), fontWeight = FontWeight.Bold)
                }
            } else {
                SettingsGroup(
                    null,
                    listOf({
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NooklinTextField(
                                value = server,
                                onValueChange = { server = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.nc_server_label)) },
                                supportingText = { Text(stringResource(R.string.nc_server_help)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth()
                            )
                            NooklinTextField(
                                value = user,
                                onValueChange = { user = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.nc_user_label)) },
                                supportingText = { Text(stringResource(R.string.nc_user_help)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            NooklinTextField(
                                value = password,
                                onValueChange = { password = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.nc_password_label)) },
                                supportingText = { Text(stringResource(R.string.nc_login_help)) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    })
                )
                if (connect.error != null) {
                    Text(
                        connect.error.orEmpty(),
                        color = GlassPill.destructiveText,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
                    )
                }
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { nc.connect(server, user, password) }, enabled = !connect.testing) {
                        Text(stringResource(R.string.nc_connect))
                    }
                    if (connect.testing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsGroup(
                null,
                listOf({
                    SettingsToggleRow(stringResource(R.string.settings_allow_http), allowHttp, Icons.Filled.Lock, TileColors.Warning) { nc.setAllowHttp(it) }
                })
            )
            SettingsNote(stringResource(R.string.nc_http_explanation))
            Spacer(Modifier.height(bottomPadding))
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.nc_disconnect_title),
            text = stringResource(R.string.nc_disconnect_text),
            confirmLabel = stringResource(R.string.nc_disconnect),
            onConfirm = {
                confirmDisconnect = false
                nc.disconnect()
            },
            onDismiss = { confirmDisconnect = false }
        )
    }
}

private fun hostOf(server: String): String =
    try {
        java.net.URI(server).host ?: server
    } catch (e: Exception) {
        server
    }

/** The big card at the top: a cloud with a ring in the color of the state, and what is going on in words. */
@Composable
private fun StatusCard(account: String?, link: NcLink, error: String?, connected: Boolean, onRetry: () -> Unit) {
    val green = Color(0xFF34C759)
    val target = when {
        !connected && link != NcLink.CHECKING -> MaterialTheme.colorScheme.outline
        link == NcLink.ONLINE -> green
        link == NcLink.OFFLINE -> GlassPill.destructiveText
        else -> MaterialTheme.colorScheme.primary
    }
    val ring by animateColorAsState(target, animationSpec = tween(400), label = "ring")
    // While it checks the ring breathes
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    val checking = link == NcLink.CHECKING
    val title = when {
        checking && !connected -> R.string.nc_status_connecting
        checking -> R.string.nc_status_checking
        !connected -> R.string.nc_status_not_connected
        link == NcLink.ONLINE -> R.string.nc_status_connected
        link == NcLink.OFFLINE -> R.string.nc_status_offline
        else -> R.string.nc_status_checking
    }
    Surface(
        color = GlassPill.adaptiveGlassBackground,
        shape = RoundedCornerShape(24.dp),
        border = GlassPill.adaptiveBorder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .border(4.dp, ring.copy(alpha = if (checking) alpha else 1f), CircleShape)
                    .background(Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                NextcloudTile(size = 44.dp)
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (connected && link != NcLink.UNKNOWN) ring else MaterialTheme.colorScheme.onSurface)
                val detail = when {
                    !connected -> stringResource(R.string.nc_status_not_connected_hint)
                    link == NcLink.OFFLINE && error != null -> error
                    else -> account.orEmpty()
                }
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = AppText.Body,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (connected && link == NcLink.OFFLINE) {
                    TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                        Text(stringResource(R.string.nc_status_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

package org.nooklin.files.ui.settings

import org.nooklin.files.ui.theme.AppText
import org.nooklin.files.ui.common.GlassPill
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import org.nooklin.files.ui.common.NooklinTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.files.R
import org.nooklin.files.cloud.SavedServer
import org.nooklin.files.cloud.ServerType
import org.nooklin.files.ui.FilesViewModel
import org.nooklin.files.ui.common.IconTile
import org.nooklin.files.ui.common.TileColors
import org.nooklin.files.ui.files.ConfirmDialog

fun serverIcon(type: ServerType) = if (type == ServerType.FTP) Icons.Rounded.Dns else Icons.Rounded.Lan
fun serverTile(type: ServerType) = if (type == ServerType.FTP) TileColors.Ftp else TileColors.Smb

/** The saved FTP and SMB servers. Tap one to change it, the cross removes it. */
@Composable
fun SettingsServersScreen(
    vm: FilesViewModel,
    bottomPadding: Dp,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit
) {
    val servers by vm.servers.servers.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<SavedServer?>(null) }
    SettingsPageScaffold(stringResource(R.string.settings_servers), bottomPadding, onBack) {
        if (servers.isNotEmpty()) {
            SettingsGroup(
                null,
                servers.map { server ->
                    {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable { onEdit(server.id) }
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(serverIcon(server.type), serverTile(server.type), Modifier.padding(end = 12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        server.type.name + "  ·  " + server.host + if (server.share.isNotEmpty()) "/" + server.share else "",
                                        fontSize = AppText.Caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { deleting = server }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            )
        } else {
            SettingsNote(stringResource(R.string.servers_empty))
        }
        Spacer(Modifier.height(8.dp))
        SettingsGroup(
            null,
            listOf({ SettingsNavRow(stringResource(R.string.servers_add), Icons.Filled.Add, TileColors.Share) { onAdd() } })
        )
        SettingsNote(stringResource(R.string.servers_explanation))
    }
    deleting?.let { server ->
        ConfirmDialog(
            title = stringResource(R.string.server_delete_title, server.name),
            text = stringResource(R.string.server_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                vm.servers.delete(server.id)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }
}

/** The form for one server. serverId is empty for a new one. */
@Composable
fun SettingsServerEditScreen(vm: FilesViewModel, serverId: String, bottomPadding: Dp, onDone: () -> Unit, onBack: () -> Unit) {
    val servers = vm.servers
    val existing = remember(serverId) { servers.servers.value.firstOrNull { it.id == serverId } }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: ServerType.SMB) }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(existing?.host.orEmpty()) }
    var port by rememberSaveable { mutableStateOf(if (existing != null && existing.port != existing.defaultPort) existing.port.toString() else "") }
    var share by rememberSaveable { mutableStateOf(existing?.share.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(existing?.user.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(existing?.password.orEmpty()) }
    var formError by remember { mutableStateOf<String?>(null) }
    val test by servers.test.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { servers.clearTest() } }

    val errHost = stringResource(R.string.server_error_host_empty)
    val errShare = stringResource(R.string.server_error_share_empty)
    val errPort = stringResource(R.string.server_error_port)

    // Null with the reason in formError when something is missing
    fun build(): SavedServer? {
        val cleanHost = host.trim()
        val portNumber = if (port.isBlank()) SavedServer.defaultPortFor(type) else port.trim().toIntOrNull() ?: -1
        formError = when {
            cleanHost.isEmpty() || cleanHost.contains('/') || cleanHost.contains(' ') -> errHost
            type == ServerType.SMB && share.trim().trim('/', '\\').isEmpty() -> errShare
            portNumber !in 1..65535 -> errPort
            else -> null
        }
        if (formError != null) return null
        return SavedServer(
            serverId, type, name.trim().ifEmpty { cleanHost }, cleanHost, portNumber,
            if (type == ServerType.SMB) share.trim().trim('/', '\\') else "", user.trim(), password
        )
    }

    SettingsPageScaffold(
        stringResource(if (serverId.isEmpty()) R.string.servers_add else R.string.servers_edit),
        bottomPadding,
        onBack
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ServerType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option; servers.clearTest() },
                    label = { Text(option.name) }
                )
            }
        }
        SettingsGroup(
            null,
            listOf({
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NooklinTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(stringResource(R.string.server_label_name)) },
                        supportingText = { Text(stringResource(R.string.server_help_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    NooklinTextField(
                        value = host, onValueChange = { host = it }, singleLine = true,
                        label = { Text(stringResource(R.string.server_label_host)) },
                        supportingText = { Text(stringResource(if (type == ServerType.FTP) R.string.server_help_host_ftp else R.string.server_help_host_smb)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    NooklinTextField(
                        value = port, onValueChange = { port = it.filter(Char::isDigit) }, singleLine = true,
                        label = { Text(stringResource(R.string.server_label_port)) },
                        supportingText = { Text(stringResource(R.string.server_help_port, SavedServer.defaultPortFor(type))) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (type == ServerType.SMB) {
                        NooklinTextField(
                            value = share, onValueChange = { share = it }, singleLine = true,
                            label = { Text(stringResource(R.string.server_label_share)) },
                            supportingText = { Text(stringResource(R.string.server_help_share)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    NooklinTextField(
                        value = user, onValueChange = { user = it }, singleLine = true,
                        label = { Text(stringResource(R.string.nc_user_label)) },
                        supportingText = { Text(stringResource(if (type == ServerType.FTP) R.string.server_help_user_ftp else R.string.server_help_user_smb)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    NooklinTextField(
                        value = password, onValueChange = { password = it }, singleLine = true,
                        label = { Text(stringResource(R.string.server_label_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            })
        )
        if (type == ServerType.FTP) SettingsNote(stringResource(R.string.server_ftp_warning))
        val message = formError ?: test.error
        if (message != null) {
            Text(message, color = GlassPill.destructiveText, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp))
        } else if (test.ok) {
            Text(stringResource(R.string.server_test_ok), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp))
        }
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                build()?.let {
                    servers.save(it)
                    onDone()
                }
            }) {
                Text(stringResource(R.string.server_save))
            }
            FilledTonalButton(onClick = { build()?.let { servers.testConnection(it) } }, enabled = !test.testing) {
                Text(stringResource(R.string.server_test))
            }
            if (test.testing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(24.dp))
        }
    }
}

/*
 Copyright 2026 Adobe. All rights reserved.
 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
 */
package com.adobe.marketing.flagsdemoapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adobe.marketing.mobile.AdobeCallbackWithError
import com.adobe.marketing.mobile.AdobeError
import com.adobe.marketing.mobile.flags.AnalyticsParam
import com.adobe.marketing.mobile.flags.FeatureEvaluationContext
import com.adobe.marketing.mobile.flags.FeatureEvaluationResult
import com.adobe.marketing.mobile.flags.Flag
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

/** Defaults for feature evaluation (provisioned STG sandbox attributes). */
private object FlagsFetchConfig {
    val BASE_ATTRIBUTES: Map<String, List<String>> = mapOf(
    )
}

private fun mergedAttributes(contextEntries: List<ContextEntry>): Map<String, List<String>> {
    val out = FlagsFetchConfig.BASE_ATTRIBUTES.toMutableMap()
    contextEntries.filter { it.key.isNotBlank() }.forEach { out[it.key] = listOf(it.value) }
    return out
}

private fun buildFlagsEvaluationContext(
    contextEntries: List<ContextEntry>,
): FeatureEvaluationContext {
    return FeatureEvaluationContext.builder()
        .withAttributes(mergedAttributes(contextEntries))
        .build()
}

/**
 * Serializes [FeatureEvaluationResult] to JSON using the same field names exposed by
 * [Flag.getFeature] without parsing or reshaping optional values (e.g. [FeatureEvaluationResult.getMeta]).
 */
private fun FeatureEvaluationResult?.toGetFeatureJson(pretty: Int = 2): String {
    if (this == null) {
        return "null"
    }

    val root = JSONObject()
    root.put("id", id)
    root.put("key", key)
    featureGroupKey?.let { root.put("featureGroupKey", it) }
    meta?.let { root.put("meta", it) }
    analyticsParam?.let { root.put("analyticsParam", it.toJsonObject()) }
    return root.toString(pretty)
}

private fun AnalyticsParam.toJsonObject(): JSONObject {
    val analytics = JSONObject()
    analytics.put("featureGroupId", featureGroupId)
    analytics.put("featureId", featureId)
    variantId?.let { analytics.put("variantId", it) }
    return analytics
}

class MainActivity : ComponentActivity() {

    companion object {
        internal const val TAG = "FlagsTestApp"

        /**
         * Provisioned flags keys (STAGE sandbox).
         * Keep aligned with flags-eval expectations when flags change.
         */
        val PROVISION_FEATURE_KEYS: List<String> = listOf(
        )

        val KNOWN_FEATURES: List<FeatureInfo> = PROVISION_FEATURE_KEYS.map { key ->
            FeatureInfo(key, key, "STG provision")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FlagTestScreen()
            }
        }
    }
}

data class FeatureInfo(val key: String, val displayName: String, val description: String)

/**
 * @param evaluationError When non-null, [enabled] is false because [AdobeCallbackWithError.fail] ran.
 */
data class FeatureState(
    val key: String,
    val enabled: Boolean,
    val info: FeatureInfo,
    val evaluationError: String? = null,
)

data class ContextEntry(val key: String, val value: String)

private enum class FlagTestTab(val label: String) {
    IS_ENABLED("Is Enabled"),
    GET_FEATURE("Get Feature"),
}

@Composable
fun FlagTestScreen() {
    var statusLabel by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(FlagTestTab.IS_ENABLED.ordinal) }
    var contextEntries by remember {
        mutableStateOf(
            FlagsFetchConfig.BASE_ATTRIBUTES.entries.map { ContextEntry(it.key, it.value.first()) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "Flags",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color(0xFF1A73E8)
            )
            Text(
                "Flags extension  |  $statusLabel",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = Color.White,
            contentColor = Color(0xFF1A73E8)
        ) {
            FlagTestTab.values().forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.label, fontWeight = FontWeight.Medium) }
                )
            }
        }

        when (FlagTestTab.values()[selectedTab]) {
            FlagTestTab.IS_ENABLED -> IsEnabledTabScreen(
                modifier = Modifier.weight(1f),
                contextEntries = contextEntries,
                onContextEntriesChange = { contextEntries = it },
                onStatusLabelChange = { statusLabel = it }
            )
            FlagTestTab.GET_FEATURE -> GetFeatureTabScreen(
                modifier = Modifier.weight(1f),
                contextEntries = contextEntries,
                onContextEntriesChange = { contextEntries = it },
                onStatusLabelChange = { statusLabel = it }
            )
        }
    }
}

@Composable
private fun EvaluationContextCard(
    contextEntries: List<ContextEntry>,
    onContextEntriesChange: (List<ContextEntry>) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Evaluation Context",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(
                    onClick = {
                        onContextEntriesChange(contextEntries + ContextEntry("", ""))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add context",
                        tint = Color(0xFF1A73E8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            contextEntries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = entry.key,
                        onValueChange = { newKey ->
                            onContextEntriesChange(
                                contextEntries.toMutableList().also {
                                    it[index] = entry.copy(key = newKey)
                                }
                            )
                        },
                        placeholder = { Text("key", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    OutlinedTextField(
                        value = entry.value,
                        onValueChange = { newValue ->
                            onContextEntriesChange(
                                contextEntries.toMutableList().also {
                                    it[index] = entry.copy(value = newValue)
                                }
                            )
                        },
                        placeholder = { Text("value", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    IconButton(
                        onClick = {
                            onContextEntriesChange(
                                contextEntries.toMutableList().also {
                                    it.removeAt(index)
                                }
                            )
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IsEnabledTabScreen(
    modifier: Modifier = Modifier,
    contextEntries: List<ContextEntry>,
    onContextEntriesChange: (List<ContextEntry>) -> Unit,
    onStatusLabelChange: (String) -> Unit,
) {
    val fetchCount = remember { AtomicInteger(0) }
    var isLoading by remember { mutableStateOf(false) }
    var featureStates by remember { mutableStateOf<List<FeatureState>>(emptyList()) }
    val tabScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusRow()

        EvaluationContextCard(
            contextEntries = contextEntries,
            onContextEntriesChange = onContextEntriesChange
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Interpreting flag results",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Green = Flag.isFeatureEnabled returned true. Grey = returned false. " +
                        "Amber = SDK error. Keys are evaluated one at a time.",
                    fontSize = 13.sp,
                    color = Color(0xFF37474F)
                )
            }
        }

        Button(
            onClick = {
                /**
                 * Enable this if you want to send some custom namespaces and value
                 */
//                val item = IdentityItem("", AuthenticatedState.AMBIGUOUS, true)
//                val identityMap = IdentityMap()
//                identityMap.addItem(item, "")
                val evaluationContext = buildFlagsEvaluationContext(contextEntries)
                isLoading = true
                val features = MainActivity.KNOWN_FEATURES
                tabScope.launch(Dispatchers.Main.immediate) {
                    val results = mutableListOf<FeatureState>()
                    try {
                        for (featureInfo in features) {
                            /**
                             * Enable this if you want to update identity with some custom namespaces and value
                             */
//                            Identity.updateIdentities(identityMap)
                            suspendCancellableCoroutine<Unit> { cont ->
                                Flag.isFeatureEnabled(
                                    featureInfo.key,
                                    evaluationContext,
                                    object : AdobeCallbackWithError<Boolean> {
                                        override fun call(isEnabled: Boolean?) {
                                            synchronized(results) {
                                                results.add(
                                                    FeatureState(
                                                        featureInfo.key,
                                                        isEnabled == true,
                                                        featureInfo,
                                                        null
                                                    )
                                                )
                                            }
                                            if (cont.isActive) {
                                                cont.resume(Unit)
                                            }
                                        }

                                        override fun fail(error: AdobeError?) {
                                            synchronized(results) {
                                                results.add(
                                                    FeatureState(
                                                        featureInfo.key,
                                                        false,
                                                        featureInfo,
                                                        error?.errorName ?: "fetch_error"
                                                    )
                                                )
                                            }
                                            Log.e(
                                                MainActivity.TAG,
                                                "Error for ${featureInfo.key}: ${error?.errorName}"
                                            )
                                            if (cont.isActive) {
                                                cont.resume(Unit)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        onStatusLabelChange("Is Enabled fetch #${fetchCount.incrementAndGet()}")
                        featureStates = results.sortedBy { it.key }
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1A73E8)),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("⟳  Fetch All Features", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (featureStates.isNotEmpty()) {
            val enabledCount = featureStates.count { it.enabled }
            val errorCount = featureStates.count { it.evaluationError != null }
            Text(
                "Results ($enabledCount/${featureStates.size} on, $errorCount errors)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    featureStates.forEach { state ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(state.key, fontSize = 14.sp)
                                state.evaluationError?.let { err ->
                                    Text(
                                        text = "Error: $err",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE65100)
                                    )
                                }
                            }
                            val dotColor =
                                when {
                                    state.evaluationError != null -> Color(0xFFFF9800)
                                    state.enabled -> Color(0xFF4CAF50)
                                    else -> Color(0xFFBDBDBD)
                                }
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        if (state != featureStates.last()) {
                            Divider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GetFeatureTabScreen(
    modifier: Modifier = Modifier,
    contextEntries: List<ContextEntry>,
    onContextEntriesChange: (List<ContextEntry>) -> Unit,
    onStatusLabelChange: (String) -> Unit,
) {
    val fetchCount = remember { AtomicInteger(0) }
    val featureKeys = MainActivity.PROVISION_FEATURE_KEYS
    var selectedFeatureKey by remember { mutableStateOf(featureKeys.first()) }
    var isLoading by remember { mutableStateOf(false) }
    var resultJson by remember { mutableStateOf<String?>(null) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    val tabScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val flagListScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusRow()

        EvaluationContextCard(
            contextEntries = contextEntries,
            onContextEntriesChange = onContextEntriesChange
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Select feature flag",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${featureKeys.size} provisioned flags — tap one, then Get Feature",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(flagListScrollState)
                ) {
                    featureKeys.forEach { key ->
                        val selected = key == selectedFeatureKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) Color(0xFFE3F2FD) else Color.Transparent
                                )
                                .clickable { selectedFeatureKey = key }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) Color(0xFF1A73E8) else Color(0xFFBDBDBD)
                                    )
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = key,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color(0xFF1A73E8) else Color(0xFF37474F)
                            )
                        }
                        if (key != featureKeys.last()) {
                            Divider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val evaluationContext = buildFlagsEvaluationContext(contextEntries)
                isLoading = true
                resultJson = null
                fetchError = null
                tabScope.launch(Dispatchers.Main.immediate) {
                    try {
                        val outcome = suspendCancellableCoroutine<GetFeatureOutcome> { cont ->
                            Flag.getFeature(
                                selectedFeatureKey,
                                evaluationContext,
                                object : AdobeCallbackWithError<FeatureEvaluationResult> {
                                    override fun call(result: FeatureEvaluationResult?) {
                                        if (cont.isActive) {
                                            cont.resume(GetFeatureOutcome.Success(result))
                                        }
                                    }

                                    override fun fail(error: AdobeError?) {
                                        Log.e(
                                            MainActivity.TAG,
                                            "getFeature error for $selectedFeatureKey: ${error?.errorName}"
                                        )
                                        if (cont.isActive) {
                                            cont.resume(
                                                GetFeatureOutcome.Failure(
                                                    error?.errorName ?: "fetch_error"
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        when (outcome) {
                            is GetFeatureOutcome.Failure -> fetchError = outcome.message
                            is GetFeatureOutcome.Success -> {
                                resultJson = outcome.result.toGetFeatureJson()
                                Log.d(
                                    MainActivity.TAG,
                                    "getFeature $selectedFeatureKey: $resultJson"
                                )
                            }
                        }
                        onStatusLabelChange("Get Feature #${fetchCount.incrementAndGet()} · $selectedFeatureKey")
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1A73E8)),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Get Feature", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        fetchError?.let { error ->
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFFF3E0)
            ) {
                Text(
                    text = "Error: $error",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp,
                    color = Color(0xFFE65100)
                )
            }
        }

        resultJson?.let { json ->
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Flag.getFeature(\"$selectedFeatureKey\")",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = json,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF263238)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ConnectionStatusRow() {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
            Text(
                "Flags: connected (STG)",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

private sealed class GetFeatureOutcome {
    data class Success(val result: FeatureEvaluationResult?) : GetFeatureOutcome()
    data class Failure(val message: String) : GetFeatureOutcome()
}

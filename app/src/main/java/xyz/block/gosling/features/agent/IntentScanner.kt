package xyz.block.gosling.features.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

object IntentScanner {
    fun getAvailableIntents(context: Context): List<IntentDefinition> {
        val packageManager = context.packageManager
        val appLabels = mutableMapOf<String, String>()
        val intentActions = mutableMapOf<String, MutableList<IntentActionDefinition>>()
        val viewIntents = mutableMapOf<String, MutableList<String>>()

        val commonIntents = listOf(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            Intent(Intent.ACTION_VIEW),
            Intent(Intent.ACTION_SEND),
            Intent(Intent.ACTION_SEARCH),
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO),
            Intent(Intent.ACTION_WEB_SEARCH),
//            Intent(ACTION_SET_ALARM),
//            Intent(ACTION_IMAGE_CAPTURE)
        )

        for (intent in commonIntents) {
            val resolvedActivities =
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

            for (resolveInfo in resolvedActivities) {
                val packageName = resolveInfo.activityInfo.packageName
                val appLabel =
                    packageManager.getApplicationLabel(resolveInfo.activityInfo.applicationInfo)
                        .toString()
                val actionName = intent.action ?: "UNKNOWN_ACTION"

                val parameters = extractExtrasForAction(actionName)
                val newAction =
                    IntentActionDefinition(actionName, parameters.first, parameters.second)

                appLabels[packageName] = appLabel
                intentActions.getOrPut(packageName) { mutableListOf() }.add(newAction)

                if (actionName == Intent.ACTION_VIEW) {
                    val urls = extractViewUrls(resolveInfo)
                    if (urls.isNotEmpty()) {
                        viewIntents.getOrPut(packageName) { mutableListOf() }.addAll(urls)
                    }
                }
            }
        }

        return intentActions.map { (packageName, actions) ->
            IntentDefinition(
                packageName = packageName,
                appLabel = appLabels[packageName] ?: "Unknown App",
                actions = actions
            )
        }
    }

    private fun extractViewUrls(resolveInfo: ResolveInfo): List<String> {
        val urls = mutableListOf<String>()
        val filter = resolveInfo.filter

        if (filter != null) {
            for (i in 0 until filter.countDataSchemes()) {
                val scheme = filter.getDataScheme(i)
                for (j in 0 until filter.countDataAuthorities()) {
                    val host = filter.getDataAuthority(j)?.host
                    if (scheme != null && host != null) {
                        urls.add("$scheme://$host/*")
                    }
                }
            }
        }
        return urls
    }


    private fun extractExtrasForAction(
        action: String
    ): Pair<List<String>, List<String>> {
        val requiredExtras = mutableListOf<String>()
        val optionalExtras = mutableListOf<String>()

        when (action) {
            Intent.ACTION_SEARCH, Intent.ACTION_WEB_SEARCH -> requiredExtras.add("query")
            Intent.ACTION_SEND, Intent.ACTION_SENDTO -> requiredExtras.add("android.intent.extra.TEXT")
            Intent.ACTION_DIAL -> requiredExtras.add("android.intent.extra.PHONE_NUMBER")
        }

        return Pair(requiredExtras, optionalExtras)
    }
}


data class IntentDefinition(
    val packageName: String,
    val appLabel: String,
    val actions: List<IntentActionDefinition>
)

data class IntentActionDefinition(
    val action: String,
    val requiredParameters: List<String>,
    val optionalParameters: List<String>
)

fun IntentDefinition.formatForLLM(): String {
    // TODO: Use the full intent. For now just return the label and name
    return "$appLabel: $packageName"
//    val actionsFormatted = actions.joinToString("\n    ") { action ->
//        val required = action.requiredParameters.joinToString(", ")
//        val optional = action.optionalParameters.joinToString(", ", "[", "]").takeIf { it.length > 2 } ?: ""
//        "${action.action}($required$optional)"
//    }
//    return "$appLabel: $packageName\nActions:\n    $actionsFormatted"
}


package org.tensorflow.lite.examples.objectdetection

import android.content.Context
import org.json.JSONObject

/** Asset format: {"0":{"0":1,"1":2},"1":{...},"2":{...},"3":{...}}.
 * Example keys illustrate the format; verify actual model metadata before use.
 */
fun loadCocoCategoryMaps(context: Context, assetName: String): Map<Int, Map<Int, Int>> {
    val root = JSONObject(context.assets.open(assetName).bufferedReader().use { it.readText() })
    return root.keys().asSequence().associate { model ->
        val entries = root.getJSONObject(model)
        model.toInt() to entries.keys().asSequence().associate { index ->
            val category = entries.getInt(index)
            require(category > 0)
            index.toInt() to category
        }
    }
}

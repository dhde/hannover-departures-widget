import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class DepartureItem(
    @SerializedName("line") val line: String?,
    @SerializedName("isCancelled") val isCancelled: Boolean? = false
)

fun main() {
    val json = """{"line": "Stadtbahn 3", "isCancelled": true}"""
    val item = Gson().fromJson(json, DepartureItem::class.java)
    println("Parsed isCancelled: ${item.isCancelled}")
}

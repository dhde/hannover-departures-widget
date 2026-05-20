fun String.strikeThrough(): String {
    return this.map { it + "\u0336" }.joinToString("")
}
fun main() {
    println("12:34".strikeThrough())
}

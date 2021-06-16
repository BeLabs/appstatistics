package de.belabs.appstatistics

class Logger {
  private var indent = 0

  fun increaseIndent() {
    indent += INDENT_FACTOR
  }

  fun decreaseIndent() {
    indent = maxOf(0, indent - INDENT_FACTOR)
  }

  fun log(log: String = "") {
    println(log.split("\n").joinToString(separator = "\n") { " ".repeat(indent) + it })
  }

  internal companion object {
    const val INDENT_FACTOR = 2
  }
}

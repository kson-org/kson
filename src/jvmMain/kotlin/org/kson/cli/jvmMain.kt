package org.kson.cli

import org.kson.mpp.getPlatformShim

fun main(args: Array<String>) = CommandLineInterface(getPlatformShim()) { println(it) }.run(args)
